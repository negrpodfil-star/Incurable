package com.incurable.medical;

import com.incurable.Incurable;
import com.incurable.config.IncurableConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Non-tick-critical medical helpers: immunity evaluation, contextual infection rolls,
 * the cure API and the hallucination janitor.
 *
 * <p>The mandatory one-minute (1200-tick) progression cycle itself lives in
 * {@code com.incurable.mixin.DiseaseTickMixin}; this class only supports it.
 */
public final class MedicalEngine {

	/** Irreversible damage per radiation pulse, in half-hearts. */
	private static final double RADIATION_HEART_PENALTY = 2.0D;

	private static final List<Hallucination> HALLUCINATIONS = new ArrayList<>();

	private MedicalEngine() {
	}

	// ---------------------------------------------------------------------------------------------
	// Immunity
	// ---------------------------------------------------------------------------------------------

	/**
	 * 0 (immunocompromised) .. 100 (healthy). Derived from food, saturation, sleep, ambient
	 * temperature, local light and current dimension.
	 *
	 * <p>Calibrated against {@code immunityThreshold} (default 50): a fully fed, rested player
	 * in daylight sits at ~55-60 and stays healthy, while anyone hungry, hurt, in the dark or
	 * in the Nether drops below the threshold and becomes a valid infection target.
	 */
	public static int immunity(ServerPlayer player) {
		int immunity = 30;

		int food = player.getFoodData().getFoodLevel();
		immunity += food / 2;                       // up to +10 for a full stomach
		immunity += (int) Math.min(8.0F, player.getFoodData().getSaturationLevel() * 2.0F);

		if (player.getSleepTimer() > 0 || player.isSleeping()) {
			immunity += 8;
		}

		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		Biome biome = level.getBiome(pos).value();
		float temperature = biome.getBaseTemperature();

		if (temperature < 0.3F) {
			immunity -= 10; // freezing biomes weaken the body
		} else if (temperature > 1.2F) {
			immunity -= 5; // scorching heat
		} else {
			immunity += 3;
		}

		int light = level.getMaxLocalRawBrightness(pos);
		immunity += light > 8 ? 4 : -6;

		if (level.dimension() == Level.NETHER) {
			immunity -= 15;
		}

		float healthRatio = player.getMaxHealth() <= 0.0F ? 0.0F : player.getHealth() / player.getMaxHealth();
		immunity -= (int) ((1.0F - healthRatio) * 25.0F);

		return Math.max(0, Math.min(100, immunity));
	}

	// ---------------------------------------------------------------------------------------------
	// Infection
	// ---------------------------------------------------------------------------------------------

	/**
	 * Called on a pulse when the player has no active disease. Returns {@code true} if a new
	 * disease was contracted.
	 */
	public static boolean rollInfection(ServerPlayer player, MedicalProfile profile) {
		IncurableConfig config = IncurableConfig.get();
		int immunity = immunity(player);

		if (immunity >= config.immunityThreshold) {
			profile.resetWeakPulses();
			return false;
		}

		int weakPulses = profile.incrementWeakPulses();
		double chance = Math.min(1.0D, config.infectionChance + weakPulses * 0.1D);

		if (player.getRandom().nextDouble() >= chance) {
			return false;
		}

		Disease disease = pickContextualDisease(player, player.getRandom());
		// Goes through Incurable#infect so the first symptoms, the chat warning and the client
		// sync (HUD) all happen immediately instead of on the next pulse.
		return disease != null && com.incurable.Incurable.infect(player, disease.id());
	}

	/**
	 * Biome / dimension aware weighting so that, for example, Cordyceps shows up in fungal
	 * biomes and radiation sickness near redstone or in the Nether.
	 */
	public static Disease pickContextualDisease(ServerPlayer player, RandomSource random) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();

		if (level.dimension() == Level.NETHER && random.nextFloat() < 0.6F) {
			return DiseaseRegistry.RADIATION_SICKNESS;
		}

		if (level.getBiome(pos).is(net.minecraft.world.level.biome.Biomes.MUSHROOM_FIELDS) && random.nextFloat() < 0.6F) {
			return DiseaseRegistry.CORDYCEPS;
		}

		if ((player.isInWater() || player.isInWaterOrRain()) && random.nextFloat() < 0.5F) {
			return DiseaseRegistry.CHOLERA;
		}

		return DiseaseRegistry.random(random);
	}

	/** Forced infection, used by the command and by contagious stage behaviours. */
	public static boolean infect(ServerPlayer player, Identifier diseaseId) {
		return profileOf(player).infect(diseaseId);
	}

	// ---------------------------------------------------------------------------------------------
	// Cure API - the one and only way out of a disease
	// ---------------------------------------------------------------------------------------------

	public static boolean cure(ServerPlayer player, Identifier diseaseId) {
		boolean cured = profileOf(player).cure(diseaseId);
		if (cured && IncurableConfig.get().debugLogging) {
			Incurable.LOGGER.info("[Incurable] {} was cured of {}", player.getScoreboardName(), diseaseId);
		}
		return cured;
	}

	public static int cureAll(ServerPlayer player) {
		return profileOf(player).cureAll();
	}

	// ---------------------------------------------------------------------------------------------
	// Radiation heart loss (permanent, shared by DiseaseBehaviors)
	// ---------------------------------------------------------------------------------------------

	/**
	 * Permanently reduces the maximum health via a saved attribute modifier. The player can
	 * never regain these hearts: the loss is only limited by {@code minimumMaxHealth}.
	 *
	 * @return {@code true} if a heart was actually burned away.
	 */
	public static boolean applyRadiationHeartLoss(ServerPlayer player, DiseaseInstance instance) {
		if (player.getMaxHealth() <= (float) IncurableConfig.get().minimumMaxHealth) {
			return false;
		}

		var attribute = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (attribute == null) {
			return false;
		}

		int applied = (int) instance.vital("heartLoss", 0.0D);
		Identifier modifierId = Incurable.id("radiation_heart_loss_" + applied);

		if (attribute.getModifier(modifierId) == null) {
			attribute.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
					modifierId, -RADIATION_HEART_PENALTY,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		}

		instance.setVital("heartLoss", applied + 1);
		return true;
	}

	// ---------------------------------------------------------------------------------------------
	// Hallucinations (schizophrenia)
	// ---------------------------------------------------------------------------------------------

	private record Hallucination(UUID owner, Mob mob, long expireAt) {
	}

	/**
	 * Spawns a hostile-looking mob behind the player. It is tracked and silently removed when it
	 * reaches the victim (vanishing right before the hit) or when its timer runs out.
	 */
	public static void spawnHallucination(ServerPlayer player, RandomSource random) {
		ServerLevel level = player.level();

		double angle = random.nextDouble() * Math.PI * 2.0D;
		int dx = (int) Math.round(Math.cos(angle) * 6.0D);
		int dz = (int) Math.round(Math.sin(angle) * 6.0D);
		BlockPos spawnPos = player.blockPosition().offset(dx, 0, dz);

		Mob mob = switch (random.nextInt(3)) {
			case 0 -> EntityTypes.CREEPER.spawn(level, spawnPos, EntitySpawnReason.EVENT);
			case 1 -> EntityTypes.ZOMBIE.spawn(level, spawnPos, EntitySpawnReason.EVENT);
			default -> EntityTypes.PHANTOM.spawn(level, spawnPos, EntitySpawnReason.EVENT);
		};

		if (mob == null) {
			return;
		}

		mob.setTarget(player);
		mob.setPersistenceRequired();
		HALLUCINATIONS.add(new Hallucination(player.getUUID(), mob, level.getGameTime() + 100L));
	}

	/** Called once per server tick: extremely cheap while no hallucinations exist. */
	public static void onServerTick(MinecraftServer server) {
		if (HALLUCINATIONS.isEmpty()) {
			return;
		}

		long now = server.getTickCount();
		Iterator<Hallucination> iterator = HALLUCINATIONS.iterator();

		while (iterator.hasNext()) {
			Hallucination hallucination = iterator.next();
			Mob mob = hallucination.mob();
			ServerPlayer owner = server.getPlayerList().getPlayer(hallucination.owner());

			boolean gone = mob.isRemoved() || now > hallucination.expireAt() || owner == null
					|| (owner != null && mob.distanceToSqr(owner) <= 4.0D);

			if (gone) {
				mob.discard();
				iterator.remove();
			}
		}
	}

	public static void forget(UUID owner) {
		Iterator<Hallucination> iterator = HALLUCINATIONS.iterator();
		while (iterator.hasNext()) {
			Hallucination hallucination = iterator.next();
			if (hallucination.owner().equals(owner)) {
				hallucination.mob().discard();
				iterator.remove();
			}
		}
	}

	private static MedicalProfile profileOf(ServerPlayer player) {
		return Incurable.profile(player);
	}
}
