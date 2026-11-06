package com.incurable.medical;

import com.incurable.Incurable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Behaviour implementations for every disease <b>except</b> Cordyceps and Rabies, whose
 * logic lives directly inside {@code DiseaseTickMixin} as required.
 *
 * <p>Nothing in this class ever removes a disease. All of it can only make the player worse.
 */
public final class DiseaseBehaviors {

	private DiseaseBehaviors() {
	}

	// ---------------------------------------------------------------------------------------------
	// Generic stage application (runs on every one-minute pulse)
	// ---------------------------------------------------------------------------------------------

	/** Re-applies the current stage's permanent effects and its guaranteed pulse damage. */
	public static void applyStage(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		Disease disease = instance.disease();
		if (disease == null) {
			return;
		}

		DiseaseStage stage = disease.stage(instance.stage());

		for (StatusEffectSpec spec : stage.effects()) {
			player.addEffect(spec.toInstance());
		}

		if (stage.pulseDamage() > 0.0F) {
			player.hurtServer(level, level.damageSources().magic(), stage.pulseDamage());
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Per-pulse behaviour (every minute)
	// ---------------------------------------------------------------------------------------------

	public static void pulse(DiseaseInstance instance, ServerPlayer player, ServerLevel level, boolean escalated) {
		Disease disease = instance.disease();
		if (disease == null) {
			return;
		}

		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();
		int stage = instance.stage();

		switch (disease.behavior()) {
			case "influenza" -> pulseInfluenza(instance, player, level, x, y, z, stage);
			case "cholera" -> pulseCholera(instance, player, level);
			case "plague" -> pulsePlague(instance, player, level, x, y, z, stage);
			case "radiation" -> pulseRadiation(instance, player, level, stage);
			case "silicosis" -> pulseSilicosis(instance, player, level, x, y, z);
			case "diabetes" -> pulseDiabetes(instance, player);
			case "cardiac" -> pulseCardiac(instance, player);
			case "schizophrenia" -> pulseSchizophrenia(instance, player, level, x, y, z);
			case "amnesia" -> pulseAmnesia(instance, player, level);
			case "cerebral_palsy" -> pulseCerebralPalsy(instance, player, level);
			case "tourette" -> pulseTourette(instance, player, level);
			case "narcolepsy" -> pulseNarcolepsy(instance, player, level);
			case "migraine" -> pulseMigraine(instance, player, level);
			case "epilepsy" -> pulseEpilepsy(instance, player, level);
			case "alzheimer" -> pulseAlzheimer(instance, player, level);
			case "paranoia" -> pulseParanoia(instance, player, level);
			case "ptsd" -> pulsePtsd(instance, player, level);
			case "insomnia" -> pulseInsomnia(instance, player, level);
			case "anemia" -> pulseAnemia(instance, player, level);
			case "cirrhosis" -> pulseCirrhosis(instance, player, level);
			case "tuberculosis" -> pulseTuberculosis(instance, player, level, x, y, z);
			case "tetanus" -> pulseTetanus(instance, player, level);
			case "gangrene" -> pulseGangrene(instance, player, level);
			case "scurvy" -> pulseScurvy(instance, player, level);
			default -> {
				// Cordyceps / Rabies are handled inside the mixin; "none" does nothing.
			}
		}
	}

	private static void pulseInfluenza(DiseaseInstance instance, ServerPlayer player, ServerLevel level,
									   double x, double y, double z, int stage) {
		// Coughing and sneezing.
		level.sendParticles(ParticleTypes.SNEEZE, x, y, z, 6, 0.3D, 0.2D, 0.3D, 0.01D);
		level.playSound(null, x, y, z, SoundEvents.HUSK_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.4F);

		// Airborne transmission within 6 blocks of the coughing host.
		if (player.getRandom().nextFloat() < 0.35F) {
			for (ServerPlayer other : level.players()) {
				if (other != player && other.distanceToSqr(player) <= 36.0D) {
					Incurable.infect(other, DiseaseRegistry.INFLUENZA.id());
				}
			}
		}

		if (stage >= 1) {
			// Pneumonia: the oxygen bar drains even on dry land.
			player.setAirSupply(0);
			player.sendSystemMessage(Component.literal("Your lungs fill with fluid...").withStyle(ChatFormatting.RED));
		}
	}

	private static void pulseCholera(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		// Food can never be restored while infected: saturation is stripped and digestion is
		// blocked by the permanent Hunger V effect applied in applyStage().
		FoodData food = player.getFoodData();
		food.setSaturation(0.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.4F, 0.6F);
	}

	private static void pulsePlague(DiseaseInstance instance, ServerPlayer player, ServerLevel level,
									double x, double y, double z, int stage) {
		if (stage >= 1) {
			// Sepsis: rot particles and contact spread.
			level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 12, 0.4D, 0.6D, 0.4D, 0.01D);

			for (ServerPlayer other : level.players()) {
				if (other != player && other.distanceToSqr(player) <= 9.0D
						&& player.getRandom().nextDouble() < com.incurable.config.IncurableConfig.get().contagionChance) {
					Incurable.infect(other, DiseaseRegistry.PLAGUE.id());
				}
			}
		}
	}

	private static void pulseRadiation(DiseaseInstance instance, ServerPlayer player, ServerLevel level, int stage) {
		// Irreversibly burns away maximum health.
		boolean lost = MedicalEngine.applyRadiationHeartLoss(player, instance);

		if (lost) {
			player.sendSystemMessage(Component.literal("Your body withers from the inside...").withStyle(ChatFormatting.DARK_RED));
		}

		// Internal bleeding: random burst damage.
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.0D, player.getZ(),
				8, 0.3D, 0.5D, 0.3D, 0.02D);
		player.hurtServer(level, level.damageSources().magic(), 1.0F + stage);
	}

	private static void pulseSilicosis(DiseaseInstance instance, ServerPlayer player, ServerLevel level,
									   double x, double y, double z) {
		level.sendParticles(ParticleTypes.WHITE_SMOKE, x, y, z, 10, 0.3D, 0.2D, 0.3D, 0.01D);
		level.playSound(null, x, y, z, SoundEvents.HUSK_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.8F);
	}

	private static void pulseDiabetes(DiseaseInstance instance, ServerPlayer player) {
		FoodData food = player.getFoodData();
		double sugar = instance.vital("sugar", 55.0D);

		// Sweet, saturated diet raises blood sugar; an empty stomach drops it.
		sugar += food.getSaturationLevel() > 4.0F ? 12.0D : -6.0D;
		sugar += (food.getFoodLevel() - 12) * 0.8D;
		sugar = Math.max(0.0D, Math.min(120.0D, sugar));
		instance.setVital("sugar", sugar);

		boolean imbalance = sugar > 65.0D || sugar < 25.0D;

		if (imbalance) {
			// Permanent Blindness + Slowness until the level is balanced again.
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 60 * 5, 0, true, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 60 * 5, 0, true, true, true));
			player.sendSystemMessage(Component.literal("Your vision blurs (blood sugar: " + (int) sugar + ")")
					.withStyle(ChatFormatting.GOLD));
		} else {
			player.removeEffect(MobEffects.BLINDNESS);
			player.removeEffect(MobEffects.SLOWNESS);
		}
	}

	private static void pulseCardiac(DiseaseInstance instance, ServerPlayer player) {
		if (instance.vital("onset", 0.0D) == 0.0D) {
			instance.setVital("onset", 1.0D);
			// Instant onset: drop to half a heart.
			player.setHealth(1.0F);
			player.sendSystemMessage(Component.literal("Your heart stops for a moment!").withStyle(ChatFormatting.DARK_RED));
		}
		player.setSprinting(false);
	}

	private static void pulseSchizophrenia(DiseaseInstance instance, ServerPlayer player, ServerLevel level,
										   double x, double y, double z) {
		// Footsteps behind the player.
		level.playSound(null, x, y, z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.7F, 1.2F);

		// A phantom hostile that vanishes right before it strikes.
		MedicalEngine.spawnHallucination(player, player.getRandom());
	}

	private static void pulseAmnesia(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		// The player forgets their crafts for a while.
		int forgotten = player.getRecipeBook().removeRecipes(level.getServer().getRecipeManager().getRecipes(), player);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 20, 0, true, true, true));

		if (forgotten > 0) {
			player.sendSystemMessage(Component.literal("You cannot remember how to craft...").withStyle(ChatFormatting.GRAY));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Extended catalogue (syndromes, neurological, systemic)
	// ---------------------------------------------------------------------------------------------

	private static void pulseCerebralPalsy(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		// A muscle spasm briefly locks the limbs.
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 4, 4, true, true, true));
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.0D, player.getZ(),
				4, 0.3D, 0.4D, 0.3D, 0.0D);
		player.sendSystemMessage(Component.literal("Your limbs lock up...").withStyle(ChatFormatting.GRAY));
	}

	private static void pulseTourette(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		// An involuntary tic: a shout and a short burst of nausea.
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.HUSK_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.9F);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.5D, player.getZ(),
				6, 0.3D, 0.3D, 0.3D, 0.0D);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 3, 0, true, true, true));
	}

	private static void pulseNarcolepsy(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		// A sleep attack: the player briefly loses control.
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 5, 4, true, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 3, 0, true, true, true));
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.5F, 0.7F);
		player.sendSystemMessage(Component.literal("You cannot keep your eyes open...").withStyle(ChatFormatting.GRAY));
	}

	private static void pulseMigraine(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		// A blinding aura; bright light makes it worse.
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 4, 0, true, true, true));
		level.sendParticles(ParticleTypes.WHITE_SMOKE, player.getX(), player.getY() + 1.6D, player.getZ(),
				8, 0.3D, 0.2D, 0.3D, 0.01D);

		if (level.getMaxLocalRawBrightness(player.blockPosition()) > 12) {
			player.hurtServer(level, level.damageSources().magic(), 2.0F);
			player.sendSystemMessage(Component.literal("The light burns through your skull.").withStyle(ChatFormatting.RED));
		}
	}

	private static void pulseEpilepsy(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		// A full seizure: immobilised, nauseous and physically hurt.
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 4, 5, true, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 6, 1, true, true, true));
		player.hurtServer(level, level.damageSources().magic(), 2.0F + instance.stage());
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.5D, player.getZ(),
				10, 0.4D, 0.4D, 0.4D, 0.02D);
	}

	private static void pulseAlzheimer(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		int forgotten = player.getRecipeBook().removeRecipes(level.getServer().getRecipeManager().getRecipes(), player);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 20, 0, true, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 20, 1, true, true, true));

		if (forgotten > 0) {
			player.sendSystemMessage(Component.literal("Where... am I? What was I making?").withStyle(ChatFormatting.GRAY));
		}
	}

	private static void pulseParanoia(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.9F, 1.4F);
		MedicalEngine.spawnHallucination(player, player.getRandom());
		player.sendSystemMessage(Component.literal("Someone is following you.").withStyle(ChatFormatting.DARK_PURPLE));
	}

	private static void pulsePtsd(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		// A panic attack: nausea, trembling and a rush of adrenaline.
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 12, 1, true, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 6, 1, true, true, true));
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 0.8F, 1.6F);
	}

	private static void pulseInsomnia(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		// Exhaustion; at night the mind conjures threats.
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60, 1, true, true, true));

		if (level.isDarkOutside()) {
			MedicalEngine.spawnHallucination(player, player.getRandom());
		}
	}

	private static void pulseAnemia(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.0D, player.getZ(),
				6, 0.3D, 0.4D, 0.3D, 0.01D);
		player.hurtServer(level, level.damageSources().magic(), 0.5F + instance.stage());
	}

	private static void pulseCirrhosis(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0D, player.getZ(),
				5, 0.3D, 0.4D, 0.3D, 0.01D);
		player.hurtServer(level, level.damageSources().magic(), 1.0F + instance.stage());
		player.getFoodData().setSaturation(0.0F);
	}

	private static void pulseTuberculosis(DiseaseInstance instance, ServerPlayer player, ServerLevel level,
										  double x, double y, double z) {
		level.sendParticles(ParticleTypes.SNEEZE, x, y, z, 8, 0.3D, 0.2D, 0.3D, 0.01D);
		level.playSound(null, x, y, z, SoundEvents.HUSK_AMBIENT, SoundSource.PLAYERS, 0.9F, 1.1F);

		// Airborne like influenza, but slower and less contagious.
		if (player.getRandom().nextFloat() < 0.25F) {
			for (ServerPlayer other : level.players()) {
				if (other != player && other.distanceToSqr(player) <= 16.0D) {
					Incurable.infect(other, DiseaseRegistry.TUBERCULOSIS.id());
				}
			}
		}

		if (instance.stage() >= 1) {
			player.setAirSupply(Math.max(0, player.getAirSupply() - 40));
		}
	}

	private static void pulseTetanus(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		// Lockjaw: the whole body spasms and freezes.
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 3, 5, true, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 6, 1, true, true, true));
		player.hurtServer(level, level.damageSources().magic(), 2.0F + instance.stage());
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WARDEN_ANGRY, SoundSource.PLAYERS, 0.6F, 1.7F);
	}

	private static void pulseGangrene(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.8D, player.getZ(),
				12, 0.4D, 0.5D, 0.4D, 0.01D);
		level.sendParticles(ParticleTypes.WHITE_SMOKE, player.getX(), player.getY() + 0.8D, player.getZ(),
				6, 0.4D, 0.4D, 0.4D, 0.01D);
	}

	private static void pulseScurvy(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.0D, player.getZ(),
				5, 0.3D, 0.4D, 0.3D, 0.01D);
		player.hurtServer(level, level.damageSources().magic(), 0.5F + instance.stage());
		player.sendSystemMessage(Component.literal("Your gums bleed...").withStyle(ChatFormatting.RED));
	}

	// ---------------------------------------------------------------------------------------------
	// Continuous behaviour (staggered, ~every 4 ticks per player)
	// ---------------------------------------------------------------------------------------------

	public static void continuous(DiseaseInstance instance, ServerPlayer player, ServerLevel level) {
		Disease disease = instance.disease();
		if (disease == null) {
			return;
		}

		switch (disease.behavior()) {
			case "cholera" -> {
				FoodData food = player.getFoodData();
				if (food.getSaturationLevel() > 0.0F) {
					food.setSaturation(0.0F);
				}
				// Running tears the body apart.
				if (player.isSprinting()) {
					player.hurtServer(level, level.damageSources().generic(), 1.0F);
				}
			}
			case "influenza" -> {
				if (instance.stage() >= 1 && !player.isInWater()) {
					player.setAirSupply(0);
				}
			}
			case "cardiac" -> player.setSprinting(false);
			case "cerebral_palsy" -> {
				// The limbs cannot sustain a sprint or a long jump.
				if (player.isSprinting() && player.getRandom().nextFloat() < 0.15F) {
					player.setSprinting(false);
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 2, 3, true, true, true));
				}
			}
			case "migraine" -> {
				// Light sensitivity: brightness slowly burns the player.
				if ((player.tickCount % 40 == 0) && level.getMaxLocalRawBrightness(player.blockPosition()) > 12) {
					player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 6, 0, true, true, true));
				}
			}
			case "epilepsy" -> {
				// Random micro-seizures: short, blinding and immobilising.
				if (player.getRandom().nextFloat() < 0.01F) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 4, true, true, true));
					player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 1, true, true, true));
				}
			}
			case "tourette" -> {
				if (player.getRandom().nextFloat() < 0.02F) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.HUSK_AMBIENT, SoundSource.PLAYERS, 0.7F, 1.8F);
				}
			}
			case "tetanus" -> {
				// Muscle rigidity: sprinting is impossible.
				if (player.isSprinting()) {
					player.setSprinting(false);
				}
			}
			case "tuberculosis" -> {
				if (instance.stage() >= 1 && !player.isInWater()) {
					player.setAirSupply(Math.max(0, player.getAirSupply() - 1));
				}
			}
			case "anemia" -> {
				if (player.isSprinting() && player.getRandom().nextFloat() < 0.05F) {
					player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 3, 0, true, true, true));
				}
			}
			default -> {
			}
		}
	}
}
