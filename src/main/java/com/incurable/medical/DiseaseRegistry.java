package com.incurable.medical;

import com.incurable.Incurable;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;

import static com.incurable.medical.DiseaseStage.of;
import static com.incurable.medical.StatusEffectSpec.endless;
import static com.incurable.medical.StatusEffectSpec.endlessAmbient;

/**
 * Registration hub for the whole Global Medical Engine.
 *
 * <p>Diseases are registered into a real, mod-owned {@link Registry} created with Fabric's
 * {@code FabricRegistryBuilder}. That keeps the catalogue data-oriented: gameplay code only
 * ever refers to a disease by its {@link Identifier}, so new diseases can be appended here
 * (or, later, loaded from a datapack) without touching the tick loop.
 */
public final class DiseaseRegistry {

	/** Registry id: {@code incurable:disease}. */
	public static final ResourceKey<Registry<Disease>> DISEASE_KEY = ResourceKey.createRegistryKey(Incurable.id("disease"));

	/** Registry-backed storage. Defaults to {@code incurable:none} for unknown ids. */
	public static final DefaultedMappedRegistry<Disease> REGISTRY =
			FabricRegistryBuilder.createDefaulted(DISEASE_KEY, Incurable.id("none")).buildAndRegister();

	// Catalogue handles (resolved at bootstrap).
	public static Disease NONE;
	public static Disease INFLUENZA;
	public static Disease CHOLERA;
	public static Disease PLAGUE;
	public static Disease RADIATION_SICKNESS;
	public static Disease SILICOSIS;
	public static Disease DIABETES;
	public static Disease CARDIAC_ARREST;
	public static Disease CORDYCEPS;
	public static Disease RABIES;
	public static Disease SCHIZOPHRENIA;
	public static Disease AMNESIA;

	// Extended catalogue: syndromes and neurological / systemic conditions.
	public static Disease CEREBRAL_PALSY;
	public static Disease TOURETTE;
	public static Disease NARCOLEPSY;
	public static Disease MIGRAINE;
	public static Disease EPILEPSY;
	public static Disease ALZHEIMER;
	public static Disease PARANOIA;
	public static Disease PTSD;
	public static Disease INSOMNIA;
	public static Disease ANEMIA;
	public static Disease CIRRHOSIS;
	public static Disease TUBERCULOSIS;
	public static Disease TETANUS;
	public static Disease GANGRENE;
	public static Disease SCURVY;

	private static boolean bootstrapped;

	private DiseaseRegistry() {
	}

	/** Safe to call once from {@code Incurable#onInitialize()}. */
	public static void bootstrap() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;

		NONE = register(new Disease(Incurable.id("none"), DiseaseCategory.CHRONIC, "none", List.of(
				of("none", endless(MobEffects.WEAKNESS, 0))
		)));

		// ----- Category 1: infectious / viral -----------------------------------------------------

		INFLUENZA = register(new Disease(Incurable.id("influenza"), DiseaseCategory.INFECTIOUS, "influenza", List.of(
				// Stage 0 - flu: fever and weakness.
				of("influenza.0", 0.5f, false,
						endless(MobEffects.WEAKNESS, 0),
						endless(MobEffects.SLOWNESS, 0)),
				// Stage 1 - pneumonia: the oxygen bar drains even on dry land, plus heavy suffocation.
				of("influenza.1", 2.0f, true,
						endless(MobEffects.SLOWNESS, 1),
						endless(MobEffects.NAUSEA, 0),
						endless(MobEffects.HUNGER, 0))
		)));

		CHOLERA = register(new Disease(Incurable.id("cholera"), DiseaseCategory.INFECTIOUS, "cholera", List.of(
				// Stage 0 - permanent hunger V, food cannot recover.
				of("cholera.0", 1.0f, false,
						endless(MobEffects.HUNGER, 4)),
				// Stage 1 - dehydration: extra damage and crippling weakness.
				of("cholera.1", 2.5f, true,
						endless(MobEffects.HUNGER, 4),
						endless(MobEffects.WEAKNESS, 1),
						endless(MobEffects.NAUSEA, 0))
		)));

		PLAGUE = register(new Disease(Incurable.id("plague"), DiseaseCategory.INFECTIOUS, "plague", List.of(
				// Stage 0 - buboes: raw wounds that hurt on every jump.
				of("plague.0", 1.5f, false,
						endless(MobEffects.WEAKNESS, 0)),
				// Stage 1 - sepsis: constant agony and it spreads to everyone you touch.
				of("plague.1", 3.0f, true,
						endless(MobEffects.WITHER, 0),
						endless(MobEffects.WEAKNESS, 1))
		)));

		// ----- Category 2: environmental / radiation ----------------------------------------------

		RADIATION_SICKNESS = register(new Disease(Incurable.id("radiation_sickness"), DiseaseCategory.ENVIRONMENTAL, "radiation", List.of(
				// Every pulse permanently burns away maximum health - it never comes back.
				of("radiation_sickness.0", 1.0f, false,
						endless(MobEffects.NAUSEA, 0)),
				of("radiation_sickness.1", 2.0f, false,
						endless(MobEffects.NAUSEA, 0),
						endless(MobEffects.WEAKNESS, 1)),
				of("radiation_sickness.2", 3.0f, true,
						endless(MobEffects.WITHER, 0),
						endless(MobEffects.WEAKNESS, 1))
		)));

		SILICOSIS = register(new Disease(Incurable.id("silicosis"), DiseaseCategory.ENVIRONMENTAL, "silicosis", List.of(
				// Stage 0 - a miner's cough.
				of("silicosis.0", 0.5f, false,
						endless(MobEffects.MINING_FATIGUE, 0)),
				// Stage 1 - the lungs stiffen: wheezing, slowness and constant damage.
				of("silicosis.1", 1.5f, true,
						endless(MobEffects.MINING_FATIGUE, 1),
						endless(MobEffects.SLOWNESS, 0))
		)));

		// ----- Category 3: chronic / systemic -----------------------------------------------------

		DIABETES = register(new Disease(Incurable.id("diabetes"), DiseaseCategory.CHRONIC, "diabetes", List.of(
				// Symptoms are driven dynamically by the "sugar" vital, not by static effects.
				of("diabetes.0"),
				of("diabetes.1", 1.0f, true,
						endless(MobEffects.NAUSEA, 0))
		)));

		CARDIAC_ARREST = register(new Disease(Incurable.id("cardiac_arrest"), DiseaseCategory.CHRONIC, "cardiac", List.of(
				// Instant onset: health drops to half a heart and sprinting is forbidden.
				of("cardiac_arrest.0", 0.0f, true,
						endless(MobEffects.SLOWNESS, 2),
						endless(MobEffects.WEAKNESS, 1))
		)));

		// ----- Category 4: parasitic / fungal -----------------------------------------------------

		CORDYCEPS = register(new Disease(Incurable.id("cordyceps"), DiseaseCategory.PARASITIC, "cordyceps", List.of(
				// Stage 0 - the fungus takes root under the skin.
				of("cordyceps.0", 0.5f, false,
						endless(MobEffects.HUNGER, 0)),
				// Stage 1 - mycelium replaces flesh; the host starts losing control.
				of("cordyceps.1", 1.5f, false,
						endless(MobEffects.HUNGER, 1),
						endless(MobEffects.NAUSEA, 0)),
				// Stage 2 - full bloom: the body is no longer yours.
				of("cordyceps.2", 3.0f, true,
						endless(MobEffects.WITHER, 1),
						endlessAmbient(MobEffects.DARKNESS, 0))
		)));

		RABIES = register(new Disease(Incurable.id("rabies"), DiseaseCategory.PARASITIC, "rabies", List.of(
				of("rabies.0", 1.0f, false,
						endless(MobEffects.WEAKNESS, 0)),
				// Stage 1 - hydrophobia: water and any drink become lethal.
				of("rabies.1", 2.0f, true,
						endless(MobEffects.WEAKNESS, 1),
						endless(MobEffects.NAUSEA, 0))
		)));

		// ----- Category 5: psychiatric / neurological ---------------------------------------------

		SCHIZOPHRENIA = register(new Disease(Incurable.id("schizophrenia"), DiseaseCategory.PSYCHIATRIC, "schizophrenia", List.of(
				of("schizophrenia.0", 0.0f, false,
						endless(MobEffects.NAUSEA, 0)),
				// Terminal: the hallucinations become hostile.
				of("schizophrenia.1", 1.0f, true,
						endless(MobEffects.NAUSEA, 1),
						endless(MobEffects.WEAKNESS, 0))
		)));

		AMNESIA = register(new Disease(Incurable.id("amnesia"), DiseaseCategory.PSYCHIATRIC, "amnesia", List.of(
				of("amnesia.0", 0.0f, false,
						endless(MobEffects.NAUSEA, 0)),
				of("amnesia.1", 0.5f, true,
						endless(MobEffects.BLINDNESS, 0))
		)));

		// ----- Category 6: syndromes --------------------------------------------------------------

		CEREBRAL_PALSY = register(new Disease(Incurable.id("cerebral_palsy"), DiseaseCategory.SYNDROME, "cerebral_palsy", List.of(
				of("cerebral_palsy.0", 0.0f, false,
						endless(MobEffects.SLOWNESS, 0),
						endless(MobEffects.MINING_FATIGUE, 0)),
				of("cerebral_palsy.1", 1.0f, true,
						endless(MobEffects.SLOWNESS, 2),
						endless(MobEffects.WEAKNESS, 1),
						endless(MobEffects.MINING_FATIGUE, 1))
		)));

		TOURETTE = register(new Disease(Incurable.id("tourette"), DiseaseCategory.SYNDROME, "tourette", List.of(
				of("tourette.0", 0.0f, false,
						endless(MobEffects.NAUSEA, 0)),
				of("tourette.1", 0.5f, true,
						endless(MobEffects.NAUSEA, 0),
						endless(MobEffects.WEAKNESS, 0))
		)));

		// ----- Category 7: neurological ------------------------------------------------------------

		NARCOLEPSY = register(new Disease(Incurable.id("narcolepsy"), DiseaseCategory.NEUROLOGICAL, "narcolepsy", List.of(
				of("narcolepsy.0", 0.0f, false,
						endless(MobEffects.WEAKNESS, 0)),
				of("narcolepsy.1", 1.0f, true,
						endless(MobEffects.SLOWNESS, 1),
						endless(MobEffects.NAUSEA, 0))
		)));

		MIGRAINE = register(new Disease(Incurable.id("migraine"), DiseaseCategory.NEUROLOGICAL, "migraine", List.of(
				of("migraine.0", 0.0f, false),
				of("migraine.1", 0.5f, true,
						endless(MobEffects.NAUSEA, 0))
		)));

		EPILEPSY = register(new Disease(Incurable.id("epilepsy"), DiseaseCategory.NEUROLOGICAL, "epilepsy", List.of(
				of("epilepsy.0", 0.0f, false),
				of("epilepsy.1", 1.0f, true,
						endless(MobEffects.WEAKNESS, 0))
		)));

		INSOMNIA = register(new Disease(Incurable.id("insomnia"), DiseaseCategory.NEUROLOGICAL, "insomnia", List.of(
				of("insomnia.0", 0.0f, false,
						endless(MobEffects.WEAKNESS, 0)),
				of("insomnia.1", 0.5f, true,
						endless(MobEffects.WEAKNESS, 1),
						endless(MobEffects.NAUSEA, 0))
		)));

		// ----- Extended psychiatric ---------------------------------------------------------------

		ALZHEIMER = register(new Disease(Incurable.id("alzheimer"), DiseaseCategory.PSYCHIATRIC, "alzheimer", List.of(
				of("alzheimer.0", 0.0f, false,
						endless(MobEffects.NAUSEA, 0)),
				of("alzheimer.1", 0.0f, true,
						endless(MobEffects.NAUSEA, 0),
						endless(MobEffects.SLOWNESS, 1),
						endless(MobEffects.BLINDNESS, 0))
		)));

		PARANOIA = register(new Disease(Incurable.id("paranoia"), DiseaseCategory.PSYCHIATRIC, "paranoia", List.of(
				of("paranoia.0", 0.0f, false),
				of("paranoia.1", 1.0f, true,
						endless(MobEffects.NAUSEA, 0))
		)));

		PTSD = register(new Disease(Incurable.id("ptsd"), DiseaseCategory.PSYCHIATRIC, "ptsd", List.of(
				of("ptsd.0", 0.0f, false),
				of("ptsd.1", 1.0f, true,
						endless(MobEffects.WEAKNESS, 0))
		)));

		// ----- Extended chronic / systemic ---------------------------------------------------------

		ANEMIA = register(new Disease(Incurable.id("anemia"), DiseaseCategory.CHRONIC, "anemia", List.of(
				of("anemia.0", 0.0f, false,
						endless(MobEffects.WEAKNESS, 0)),
				of("anemia.1", 0.5f, true,
						endless(MobEffects.WEAKNESS, 1),
						endless(MobEffects.MINING_FATIGUE, 1))
		)));

		CIRRHOSIS = register(new Disease(Incurable.id("cirrhosis"), DiseaseCategory.CHRONIC, "cirrhosis", List.of(
				of("cirrhosis.0", 0.5f, false,
						endless(MobEffects.HUNGER, 0)),
				of("cirrhosis.1", 1.5f, true,
						endless(MobEffects.HUNGER, 1),
						endless(MobEffects.NAUSEA, 0))
		)));

		SCURVY = register(new Disease(Incurable.id("scurvy"), DiseaseCategory.CHRONIC, "scurvy", List.of(
				of("scurvy.0", 0.0f, false,
						endless(MobEffects.WEAKNESS, 0)),
				of("scurvy.1", 1.0f, true,
						endless(MobEffects.WEAKNESS, 1),
						endless(MobEffects.MINING_FATIGUE, 1))
		)));

		// ----- Extended infectious ----------------------------------------------------------------

		TUBERCULOSIS = register(new Disease(Incurable.id("tuberculosis"), DiseaseCategory.INFECTIOUS, "tuberculosis", List.of(
				of("tuberculosis.0", 0.5f, false,
						endless(MobEffects.WEAKNESS, 0)),
				of("tuberculosis.1", 2.0f, true,
						endless(MobEffects.WEAKNESS, 1),
						endless(MobEffects.SLOWNESS, 0))
		)));

		TETANUS = register(new Disease(Incurable.id("tetanus"), DiseaseCategory.INFECTIOUS, "tetanus", List.of(
				of("tetanus.0", 1.0f, false,
						endless(MobEffects.WEAKNESS, 0)),
				of("tetanus.1", 2.0f, true,
						endless(MobEffects.SLOWNESS, 2),
						endless(MobEffects.WEAKNESS, 1))
		)));

		GANGRENE = register(new Disease(Incurable.id("gangrene"), DiseaseCategory.INFECTIOUS, "gangrene", List.of(
				of("gangrene.0", 1.0f, false,
						endless(MobEffects.WEAKNESS, 0)),
				of("gangrene.1", 2.0f, false,
						endless(MobEffects.WITHER, 0),
						endless(MobEffects.SLOWNESS, 1)),
				of("gangrene.2", 3.0f, true,
						endless(MobEffects.WITHER, 1),
						endless(MobEffects.WEAKNESS, 1))
		)));

		Incurable.LOGGER.info("[Incurable] Registered {} diseases", REGISTRY.keySet().size());
	}

	public static Disease register(Disease disease) {
		return Registry.register(REGISTRY, disease.id(), disease);
	}

	/** Null-safe lookup; falls back to {@code incurable:none} for unknown ids. */
	public static Disease get(Identifier id) {
		if (id == null) {
			return null;
		}
		Disease disease = REGISTRY.getValue(id);
		return disease == NONE ? null : disease;
	}

	public static Disease getOrDefault(Identifier id) {
		return REGISTRY.getValue(id);
	}

	public static List<Disease> all() {
		List<Disease> result = new ArrayList<>();
		REGISTRY.stream().filter(disease -> disease != NONE).forEach(result::add);
		return result;
	}

	/** Used by the infection roll when the player is healthy but immunocompromised. */
	public static Disease random(RandomSource random) {
		List<Disease> pool = all();
		return pool.get(random.nextInt(pool.size()));
	}
}
