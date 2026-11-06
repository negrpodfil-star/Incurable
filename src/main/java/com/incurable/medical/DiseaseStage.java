package com.incurable.medical;

import java.util.List;

/**
 * One stage of a {@link Disease}. Stage 0 is the incubation / mild form, the final stage is
 * usually {@link #terminal()}. A disease is guaranteed to move forward one stage on every
 * one-minute pulse until it reaches its terminal stage - it can never move backwards.
 */
public record DiseaseStage(String translationKey, List<StatusEffectSpec> effects, float pulseDamage, boolean terminal) {

	public static DiseaseStage of(String path, float pulseDamage, boolean terminal, StatusEffectSpec... effects) {
		return new DiseaseStage("stage.incurable." + path, List.of(effects), pulseDamage, terminal);
	}

	public static DiseaseStage of(String path, StatusEffectSpec... effects) {
		return of(path, 0.0f, false, effects);
	}
}
