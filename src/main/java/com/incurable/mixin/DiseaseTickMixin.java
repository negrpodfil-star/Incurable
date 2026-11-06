package com.incurable.mixin;

import com.incurable.Incurable;
import com.incurable.config.IncurableConfig;
import com.incurable.medical.Disease;
import com.incurable.medical.DiseaseBehaviors;
import com.incurable.medical.DiseaseInstance;
import com.incurable.medical.DiseaseRegistry;
import com.incurable.medical.MedicalEngine;
import com.incurable.medical.MedicalProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the Global Medical Engine into {@code ServerPlayer#tick()}.
 *
 * <h2>The one-minute cycle</h2>
 * Every {@code pulseIntervalTicks} (default 1200 == 1 minute) the following happens:
 * <ol>
 *     <li><b>Already sick</b> - every disease advances its (monotonic) timer and is guaranteed
 *     to escalate one stage. At the terminal stage it instead triggers an uncurable acute
 *     attack. Nothing is ever removed here.</li>
 *     <li><b>Healthy</b> - immunity is evaluated; if it is broken, a new random disease is
 *     contracted and added to the player's active afflictions.</li>
 * </ol>
 *
 * <p>The only function able to remove a disease is {@link MedicalProfile#cure(Identifier)},
 * which is never called from this loop.
 */
@Mixin(ServerPlayer.class)
public abstract class DiseaseTickMixin {

	@Inject(method = "tick", at = @At("HEAD"))
	private void incurable$tick(CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		ServerLevel level = player.level();
		MedicalProfile profile = Incurable.profile(player);

		// ---- 1) MANDATORY 5-MINUTE PULSE -------------------------------------------------------
		// Driven by the global world clock, so every player on the server pulses on the same
		// tick. This is the "global timer" required by the design.
		int interval = IncurableConfig.get().pulseIntervalTicks;
		long gameTime = level.getGameTime();
		if (gameTime > 0L && gameTime % interval == 0L) {
			incurable$runPulse(player, level, profile);
		}

		// ---- 2) CHEAP CONTINUOUS SYMPTOMS ------------------------------------------------------
		// Only executed when the player is actually sick, and staggered per-entity so that the
		// server tick is never flooded by a big player count.
		if (profile.hasDiseases() && (player.tickCount & 3) == (player.getId() & 3)) {
			incurable$continuousSymptoms(player, level, profile);
		}
	}

	// =============================================================================================
	// The pulse
	// =============================================================================================

	@Unique
	private void incurable$runPulse(ServerPlayer player, ServerLevel level, MedicalProfile profile) {
		if (IncurableConfig.get().debugLogging) {
			Incurable.LOGGER.info("[Incurable] Pulse for {} (diseases={})", player.getScoreboardName(), profile.size());
		}

		if (!profile.hasDiseases()) {
			// Healthy but possibly immunocompromised: roll for a brand new disease.
			if (MedicalEngine.rollInfection(player, profile)) {
				Incurable.sync(player);
			}
			return;
		}

		for (DiseaseInstance instance : profile.snapshot()) {
			Disease disease = instance.disease();
			if (disease == null) {
				continue;
			}

			// Monotonic clock: DiseaseInstance#advance only ever adds positive ticks.
			// The delta is the configured pulse length, so the display timer matches reality.
			instance.advance(IncurableConfig.get().pulseIntervalTicks);

			// Guaranteed progression. Returns false only at the terminal stage.
			boolean escalated = instance.escalate();

			// Re-apply this stage's permanent effects and pulse damage.
			DiseaseBehaviors.applyStage(instance, player, level);

			if (escalated) {
				incurable$announceEscalation(player, disease, instance.stage());
			} else {
				// Terminal stage: an acute, uncurable attack instead of further progression.
				incurable$acuteAttack(player, level, instance);
			}

			switch (disease.behavior()) {
				case "cordyceps" -> incurable$cordycepsPulse(instance, player, level, escalated);
				case "rabies" -> incurable$rabiesPulse(instance, player, level, escalated);
				default -> DiseaseBehaviors.pulse(instance, player, level, escalated);
			}
		}

		// ABSOLUTELY NO CURING HERE. The loop above can only add diseases or make them worse.

		// Publish the (possibly worsened) state to the owner's client for the medical HUD.
		Incurable.sync(player);
	}

	@Unique
	private void incurable$continuousSymptoms(ServerPlayer player, ServerLevel level, MedicalProfile profile) {
		for (DiseaseInstance instance : profile.snapshot()) {
			Disease disease = instance.disease();
			if (disease == null) {
				continue;
			}

			switch (disease.behavior()) {
				// Cordyceps and Rabies are implemented right here, as required.
				case "cordyceps" -> incurable$cordycepsTick(instance, player, level);
				case "rabies" -> incurable$rabiesTick(instance, player, level);
				default -> DiseaseBehaviors.continuous(instance, player, level);
			}
		}
	}

	@Unique
	private void incurable$announceEscalation(ServerPlayer player, Disease disease, int stage) {
		player.sendSystemMessage(Component.literal("Your condition worsens: ")
				.append(Component.translatable(disease.stage(stage).translationKey()))
				.withStyle(ChatFormatting.DARK_RED));
	}

	/** Only used when the disease has already reached its terminal stage. */
	@Unique
	private void incurable$acuteAttack(ServerPlayer player, ServerLevel level, DiseaseInstance instance) {
		Disease disease = instance.disease();
		if (disease == null) {
			return;
		}

		float damage = 2.0F + instance.stage();
		player.hurtServer(level, level.damageSources().magic(), damage);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 15, 0, true, true, true));

		switch (disease.category()) {
			case INFECTIOUS -> player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 20, 1, true, true, true));
			case ENVIRONMENTAL -> player.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 10, 0, true, true, true));
			case CHRONIC -> player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 20, 1, true, true, true));
			case PARASITIC -> player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 20, 2, true, true, true));
			case PSYCHIATRIC -> player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 15, 0, true, true, true));
			case SYNDROME -> player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 20, 2, true, true, true));
			case NEUROLOGICAL -> player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 15, 0, true, true, true));
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WARDEN_ANGRY, SoundSource.PLAYERS, 0.7F, 0.6F);
	}

	// =============================================================================================
	// CORDYCEPS - "the fungus that never lets go"
	// =============================================================================================

	/** One-minute pulse: spread the mycelium and, from stage 1, seize control of the host. */
	@Unique
	private void incurable$cordycepsPulse(DiseaseInstance instance, ServerPlayer player, ServerLevel level, boolean escalated) {
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();

		// The skin is visibly replaced by mycelium.
		level.sendParticles(ParticleTypes.MYCELIUM, x, y, z, 40, 0.5D, 0.9D, 0.5D, 0.02D);
		level.sendParticles(ParticleTypes.WARPED_SPORE, x, y, z, 20, 0.5D, 0.9D, 0.5D, 0.01D);

		if (escalated) {
			level.playSound(null, x, y, z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.HOSTILE, 1.2F, 0.5F);
			player.sendSystemMessage(Component.literal("Something else is moving your body.")
					.withStyle(ChatFormatting.DARK_PURPLE));
		}

		if (instance.stage() >= 1) {
			// Hand control over to the fungus for ~6 seconds.
			instance.setVital("possession", 120.0D);
		}
	}

	/** Continuous: while possessed, the fungus drags the player toward the dark. */
	@Unique
	private void incurable$cordycepsTick(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		double possession = instance.vital("possession", 0.0D);
		if (possession <= 0.0D) {
			return;
		}

		instance.setVital("possession", possession - 1.0D);

		// The fungus can never be cured from the outside: this repeats every pulse, forever.
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, true, true, true));
		player.setSprinting(true);

		incurable$steerTowardDarkness(player, level);

		if (((int) possession) % 20 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SCULK_CLICKING, SoundSource.HOSTILE, 0.6F, 0.7F);
		}
	}

	/** Samples 8 directions and pulls the player toward the least lit one. */
	@Unique
	private void incurable$steerTowardDarkness(ServerPlayer player, ServerLevel level) {
		BlockPos origin = player.blockPosition();

		int bestX = 0;
		int bestZ = 0;
		int bestLight = Integer.MAX_VALUE;

		for (int i = 0; i < 8; i++) {
			double angle = i * (Math.PI / 4.0D);
			int dx = (int) Math.round(Math.cos(angle) * 3.0D);
			int dz = (int) Math.round(Math.sin(angle) * 3.0D);
			int light = level.getMaxLocalRawBrightness(origin.offset(dx, 0, dz));

			if (light < bestLight) {
				bestLight = light;
				bestX = dx;
				bestZ = dz;
			}
		}

		Vec3 direction = new Vec3(bestX, 0.0D, bestZ);
		if (direction.lengthSqr() < 1.0E-6D) {
			direction = new Vec3(1.0D, 0.0D, 0.0D);
		}
		direction = direction.normalize().scale(0.28D);

		Vec3 current = player.getDeltaMovement();
		player.setDeltaMovement(direction.x, current.y, direction.z);
		// These public Entity flags tell the tracking system to push the new velocity to clients.
		player.syncVelocity = true;
		player.needsSync = true;
	}

	/** On death the fully bloomed fungus bursts and infects everyone nearby. */
	@Inject(method = "die", at = @At("HEAD"))
	private void incurable$cordycepsDeath(DamageSource source, CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		MedicalProfile profile = Incurable.profile(player);

		if (!profile.has(DiseaseRegistry.CORDYCEPS.id())) {
			return;
		}

		ServerLevel level = player.level();
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();

		// Spore cloud.
		level.sendParticles(ParticleTypes.MYCELIUM, x, y, z, 250, 2.5D, 1.5D, 2.5D, 0.08D);
		level.sendParticles(ParticleTypes.WARPED_SPORE, x, y, z, 120, 2.5D, 1.5D, 2.5D, 0.05D);
		level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 60, 2.0D, 1.0D, 2.0D, 0.03D);
		level.playSound(null, x, y, z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.HOSTILE, 3.0F, 0.4F);

		for (ServerPlayer other : level.players()) {
			if (other != player && other.distanceToSqr(player) <= 100.0D) {
				Incurable.infect(other, DiseaseRegistry.CORDYCEPS.id());
			}
		}
	}

	// =============================================================================================
	// RABIES - "hydrophobia until cured"
	// =============================================================================================

	@Unique
	private void incurable$rabiesPulse(DiseaseInstance instance, ServerPlayer player, ServerLevel level, boolean escalated) {
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();

		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, x, y, z, 4, 0.4D, 0.4D, 0.4D, 0.0D);

		if (escalated) {
			level.playSound(null, x, y, z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.0F, 1.2F);
			player.sendSystemMessage(Component.literal("Water is your enemy. You cannot drink.")
					.withStyle(ChatFormatting.DARK_RED));
		}

		// Stage 1 = full hydrophobia: standing in water is already lethal.
		if (instance.stage() >= 1 && (player.isInWater() || player.isInWaterOrRain())) {
			incurable$rabiesFear(player, level, 6.0F);
		}
	}

	/** Continuous: entering water or trying to drink floods the player with terror and damage. */
	@Unique
	private void incurable$rabiesTick(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		boolean inWater = player.isInWater() || player.isInWaterOrRain();
		boolean drinking = incurable$isDrinking(player);

		if (!inWater && !drinking) {
			return;
		}

		// Enormous damage, and the fear effect.
		incurable$rabiesFear(player, level, drinking ? 8.0F : 4.0F);
	}

	@Unique
	private boolean incurable$isDrinking(ServerPlayer player) {
		if (!player.isUsingItem()) {
			return false;
		}

		ItemStack stack = player.getUseItem();
		if (stack.isEmpty()) {
			return false;
		}

		return stack.getItem() == Items.POTION
				|| stack.getItem() == Items.MILK_BUCKET
				|| stack.getItem() == Items.HONEY_BOTTLE;
	}

	@Unique
	private void incurable$rabiesFear(ServerPlayer player, ServerLevel level, float damage) {
		player.hurtServer(level, level.damageSources().magic(), damage);

		// The fear effect: darkness, nausea and panic sounds.
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 10, 0, true, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 10, 1, true, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 5, 1, true, true, true));

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0D, player.getZ(),
				20, 0.4D, 0.4D, 0.4D, 0.05D);

		// Panic: shove the player away from the water.
		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(-look.x * 0.6D, 0.35D, -look.z * 0.6D);
		player.syncVelocity = true;
		player.needsSync = true;
	}
}
