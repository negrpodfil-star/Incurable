package com.incurable.medical;

/**
 * Broad classification of a disease. Purely data-oriented: it drives language keys and
 * the infection weighting used by {@link MedicalEngine}, but carries no gameplay logic.
 */
public enum DiseaseCategory {
	INFECTIOUS("infectious"),
	ENVIRONMENTAL("environmental"),
	CHRONIC("chronic"),
	PARASITIC("parasitic"),
	PSYCHIATRIC("psychiatric"),
	SYNDROME("syndrome"),
	NEUROLOGICAL("neurological");

	private final String key;

	DiseaseCategory(String key) {
		this.key = key;
	}

	public String key() {
		return key;
	}

	public String translationKey() {
		return "category.incurable." + key;
	}
}
