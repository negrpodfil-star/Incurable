package com.incurable.medical;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A single status effect bundled into a {@link DiseaseStage}.
 *
 * <p>{@code infinite} stages are re-applied with {@link MobEffectInstance#INFINITE_DURATION}
 * on every one-minute pulse. Because diseases are permanent, those effects simply never expire
 * while the disease is present; they are only removed when the affliction is explicitly cured.
 */
public record StatusEffectSpec(Holder<MobEffect> effect, int amplifier, boolean infinite, boolean ambient) {

	public static StatusEffectSpec endless(Holder<MobEffect> effect, int amplifier) {
		return new StatusEffectSpec(effect, amplifier, true, false);
	}

	public static StatusEffectSpec endlessAmbient(Holder<MobEffect> effect, int amplifier) {
		return new StatusEffectSpec(effect, amplifier, true, true);
	}

	public MobEffectInstance toInstance() {
		// duration = -1 for a permanent affliction symptom; showParticles = true, showIcon = true.
		return new MobEffectInstance(effect, infinite ? MobEffectInstance.INFINITE_DURATION : 20 * 30, amplifier, ambient, true, true);
	}
}
