package com.incurable;

import com.incurable.command.IncurableCommand;
import com.incurable.config.IncurableConfig;
import com.incurable.medical.DiseaseBehaviors;
import com.incurable.medical.DiseaseRegistry;
import com.incurable.medical.MedicalEngine;
import com.incurable.medical.MedicalProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global Medical Engine - Fabric 26.3.
 *
 * <p>Design summary:
 * <ul>
 *     <li>Diseases are data-oriented registry objects ({@link DiseaseRegistry}).</li>
 *     <li>Per-player state lives in {@link MedicalProfile}, attached via the Fabric
 *     attachment API. It is persisted and, when configured, copied on death.</li>
 *     <li>The mandatory one-minute progression/infection cycle lives in
 *     {@code com.incurable.mixin.DiseaseTickMixin}, injected into {@code ServerPlayer#tick()}.</li>
 *     <li>A disease can only ever be removed through {@link MedicalProfile#cure(Identifier)}.</li>
 * </ul>
 */
public final class Incurable implements ModInitializer {

	public static final String MOD_ID = "incurable";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * The player attachment holding every active disease.
	 *
	 * <p>{@code persistent(...)} writes it to the player's NBT, {@code copyOnDeath()} keeps it
	 * across death, and {@code syncWith(..., targetOnly())} mirrors it to the owner's client so
	 * the medical HUD can render it. Vanilla clients do not advertise support for the attachment
	 * during configuration, so nothing is ever sent to them and their connection stays untouched.
	 */
	public static final AttachmentType<MedicalProfile> MEDICAL_PROFILE = AttachmentRegistry.create(
			id("medical_profile"),
			(AttachmentRegistry.Builder<MedicalProfile> builder) -> builder
					.persistent(MedicalProfile.CODEC)
					.copyOnDeath()
					.syncWith(MedicalProfile.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
					.initializer(MedicalProfile::new));

	@Override
	public void onInitialize() {
		IncurableConfig.get().load();

		// Custom disease registry + the whole catalogue.
		DiseaseRegistry.bootstrap();

		// Hallucination bookkeeping / lightweight global maintenance (once per server tick).
		ServerTickEvents.END_SERVER_TICK.register(MedicalEngine::onServerTick);

		// Respect the "persist across death" config option. The attachment itself is always
		// copied on death, then wiped here when the server owner disabled persistence.
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (!IncurableConfig.get().persistAcrossDeath) {
				int removed = profile(newPlayer).cureAll();
				if (removed > 0) {
					LOGGER.info("[Incurable] {} died - wiping {} disease(s) (persistAcrossDeath=false)",
							newPlayer.getScoreboardName(), removed);
				}
				sync(newPlayer);
			}
		});

		// Push the current state as soon as the player enters the world / respawns, so the HUD
		// is up to date without waiting for the next pulse.
		ServerPlayerEvents.JOIN.register(player -> sync(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));

		ServerPlayerEvents.LEAVE.register(player -> MedicalEngine.forget(player.getUUID()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> IncurableCommand.register(dispatcher));

		LOGGER.info("[Incurable] Global Medical Engine online. Diseases are permanent; only cure() removes them.");
	}

	/** {@code incurable:<path>} */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/**
	 * Returns the player's medical profile, creating it if the attachment is absent.
	 * Attachment methods are added to {@code Entity} by Fabric, hence the cast.
	 */
	public static MedicalProfile profile(Entity entity) {
		return ((AttachmentTarget) entity).getAttachedOrCreate(MEDICAL_PROFILE, MedicalProfile::new);
	}

	public static MedicalProfile profile(Player player) {
		return profile((Entity) player);
	}

	/**
	 * Read-only access for the client HUD: returns {@code null} when the attachment has not been
	 * synchronised (or does not exist) yet. Unlike {@link #profile(Entity)} this never creates one.
	 */
	public static MedicalProfile profileOrNull(Entity entity) {
		return ((AttachmentTarget) entity).getAttached(MEDICAL_PROFILE);
	}

	/**
	 * Publishes the current medical state to the owner's client.
	 *
	 * <p>The mutable instance is replaced by a {@link MedicalProfile#copy() copy}, because
	 * Fabric skips the network update when the old and new attachment values are {@code equals}
	 * - which they would be for the same mutated object.
	 */
	public static void sync(ServerPlayer player) {
		MedicalProfile profile = ((AttachmentTarget) player).getAttached(MEDICAL_PROFILE);
		if (profile != null) {
			((AttachmentTarget) player).setAttached(MEDICAL_PROFILE, profile.copy());
		}
	}

/**
	 * Infects the player, shows the first symptoms immediately and pushes the new state to
	 * their client. Without the instant symptom application a freshly infected player would
	 * look healthy until the next pulse.
	 *
	 * @return {@code true} if the disease was newly contracted.
	 */
	public static boolean infect(ServerPlayer player, Identifier diseaseId) {
		MedicalProfile profile = profile(player);
		boolean infected = profile.infect(diseaseId);

		if (infected) {
			var instance = profile.get(diseaseId);
			if (instance != null) {
				// Instant first symptoms + a clear "you are infected" message.
				DiseaseBehaviors.applyStage(instance, player, player.level());
				player.sendSystemMessage(Component.translatable("msg.incurable.infected",
						Component.translatable("disease.incurable." + diseaseId.getPath()))
						.withStyle(ChatFormatting.DARK_RED));
			}
			sync(player);
		}
		return infected;
	}

	/** Convenience for the cure API. */
	public static boolean cure(Player player, Identifier diseaseId) {
		boolean cured = profile(player).cure(diseaseId);
		if (cured && player instanceof ServerPlayer serverPlayer) {
			sync(serverPlayer);
		}
		return cured;
	}
}
