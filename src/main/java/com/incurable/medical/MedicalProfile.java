package com.incurable.medical;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The per-player medical container. One instance is attached to every {@code ServerPlayer}
 * through the Fabric attachment API (see {@code Incurable#MEDICAL_PROFILE}).
 *
 * <p>This class owns the active {@link DiseaseInstance} list and exposes the <b>only</b>
 * removal entry point in the mod: {@link #cure(Identifier)}. Nothing else ever removes a
 * disease, therefore a disease cannot "time out".
 */
public final class MedicalProfile {

	private final List<DiseaseInstance> diseases = new ArrayList<>();

	/** Last pulse index processed by the one-minute cycle (bookkeeping only, never a cure timer). */
	private long lastPulse = -1L;

	/** How many consecutive one-minute pulses the player spent with a weak immune system. */
	private int weakPulses = 0;

	public boolean hasDiseases() {
		return !diseases.isEmpty();
	}

	public int size() {
		return diseases.size();
	}

	/** Read-only live view. */
	public List<DiseaseInstance> diseases() {
		return Collections.unmodifiableList(diseases);
	}

	/**
	 * Snapshot used while iterating in the tick loop, so the pulse handler can mutate
	 * the profile (spawn a disease, cure another one) without a ConcurrentModificationException.
	 */
	public List<DiseaseInstance> snapshot() {
		return new ArrayList<>(diseases);
	}

	public boolean has(Identifier diseaseId) {
		for (DiseaseInstance instance : diseases) {
			if (instance.diseaseId().equals(diseaseId)) {
				return true;
			}
		}
		return false;
	}

	public DiseaseInstance get(Identifier diseaseId) {
		for (DiseaseInstance instance : diseases) {
			if (instance.diseaseId().equals(diseaseId)) {
				return instance;
			}
		}
		return null;
	}

	/**
	 * Adds a disease. Duplicate afflictions are ignored (a second infection of the same
	 * disease does not stack, and the existing instance keeps progressing).
	 *
	 * @return {@code true} if the disease was newly contracted.
	 */
	public boolean infect(Identifier diseaseId) {
		if (diseaseId == null || DiseaseRegistry.get(diseaseId) == null || has(diseaseId)) {
			return false;
		}
		diseases.add(new DiseaseInstance(diseaseId));
		return true;
	}

	public boolean infect(Disease disease) {
		return disease != null && infect(disease.id());
	}

	/**
	 * THE ONLY PATH THAT REMOVES A DISEASE.
	 *
	 * <p>This is intentionally not called anywhere in the tick loop. It must be triggered by
	 * an external medical action: a specific medicine, a serum, a procedure or a unique
	 * gameplay interaction.
	 *
	 * @return {@code true} if a disease was actually cured.
	 */
	public boolean cure(Identifier diseaseId) {
		return diseases.removeIf(instance -> instance.diseaseId().equals(diseaseId));
	}

	/**
	 * Cures every affliction. Reserved for debug commands and for the config option that
	 * wipes diseases on death - never for a passive timer.
	 *
	 * @return number of diseases removed.
	 */
	public int cureAll() {
		int removed = diseases.size();
		diseases.clear();
		return removed;
	}

	public long lastPulse() {
		return lastPulse;
	}

	public void setLastPulse(long lastPulse) {
		this.lastPulse = lastPulse;
	}

	public int weakPulses() {
		return weakPulses;
	}

	public int incrementWeakPulses() {
		return ++weakPulses;
	}

	public void resetWeakPulses() {
		this.weakPulses = 0;
	}

	/**
	 * Deep copy used when publishing the state to clients.
	 *
	 * <p>Fabric only sends an attachment update when {@code !Objects.equals(oldValue, newValue)}.
	 * Because this class is mutable and the server mutates the very instance stored in the
	 * attachment, an in-place update would compare equal to itself and be skipped. Replacing the
	 * attachment with this copy guarantees the HUD receives the new state.
	 */
	public MedicalProfile copy() {
		MedicalProfile copy = new MedicalProfile();
		for (DiseaseInstance instance : diseases) {
			copy.diseases.add(instance.copy());
		}
		copy.lastPulse = this.lastPulse;
		copy.weakPulses = this.weakPulses;
		return copy;
	}

	// ---------------------------------------------------------------------------------------------
	// Persistence: a compact list of "namespace:path=stage" entries.
	// ---------------------------------------------------------------------------------------------

	/**
	 * NBT / save codec. Each entry is {@code namespace:path|stage|pulses|key=value;key=value}.
	 * The legacy {@code namespace:path=stage} format is still accepted when loading.
	 */
	public static final Codec<MedicalProfile> CODEC = Codec.STRING.listOf().xmap(MedicalProfile::fromList, MedicalProfile::toList);

	/**
	 * Network codec used by {@code AttachmentRegistry.Builder#syncWith}. Sends only what the
	 * HUD needs to render: id, stage, pulse count and the behaviour vitals.
	 */
	public static final StreamCodec<RegistryFriendlyByteBuf, MedicalProfile> STREAM_CODEC = StreamCodec.of(
			(buf, profile) -> {
				buf.writeVarInt(profile.diseases.size());
				for (DiseaseInstance instance : profile.diseases) {
					buf.writeUtf(instance.diseaseId().toString());
					buf.writeVarInt(instance.stage());
					buf.writeVarInt(instance.pulses());
					Map<String, Double> vitals = instance.vitalsView();
					buf.writeVarInt(vitals.size());
					for (Map.Entry<String, Double> vital : vitals.entrySet()) {
						buf.writeUtf(vital.getKey());
						buf.writeDouble(vital.getValue());
					}
				}
			},
			buf -> {
				int size = buf.readVarInt();
				MedicalProfile profile = new MedicalProfile();
				for (int i = 0; i < size; i++) {
					Identifier id = Identifier.tryParse(buf.readUtf());
					int stage = buf.readVarInt();
					int pulses = buf.readVarInt();
					int vitalCount = buf.readVarInt();
					Map<String, Double> vitals = new LinkedHashMap<>();
					for (int j = 0; j < vitalCount; j++) {
						vitals.put(buf.readUtf(), buf.readDouble());
					}

					if (id == null || DiseaseRegistry.get(id) == null) {
						continue;
					}

					DiseaseInstance instance = new DiseaseInstance(id, stage);
					instance.setPulses(pulses);
					vitals.forEach(instance::setVital);
					profile.diseases.add(instance);
				}
				return profile;
			});

	private static MedicalProfile fromList(List<String> entries) {
		MedicalProfile profile = new MedicalProfile();

		for (String entry : entries) {
			try {
				profile.diseases.add(parseEntry(entry));
			} catch (RuntimeException ignored) {
				// A single corrupted entry must never break a player's save.
			}
		}
		return profile;
	}

	private static DiseaseInstance parseEntry(String entry) {
		int bar = entry.indexOf('|');

		if (bar < 0) {
			// Legacy format: namespace:path=stage
			int split = entry.lastIndexOf('=');
			if (split <= 0) {
				throw new IllegalArgumentException("bad entry");
			}
			Identifier id = Identifier.parse(entry.substring(0, split));
			int stage = Integer.parseInt(entry.substring(split + 1));
			if (DiseaseRegistry.get(id) == null) {
				throw new IllegalArgumentException("unknown disease");
			}
			return new DiseaseInstance(id, stage);
		}

		String[] parts = entry.split("\\|", -1);
		Identifier id = Identifier.parse(parts[0]);
		if (DiseaseRegistry.get(id) == null) {
			throw new IllegalArgumentException("unknown disease");
		}

		int stage = Integer.parseInt(parts[1]);
		int pulses = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : 0;
		DiseaseInstance instance = new DiseaseInstance(id, stage);
		instance.setPulses(pulses);

		if (parts.length > 3 && !parts[3].isEmpty()) {
			for (String vital : parts[3].split(";")) {
				int eq = vital.indexOf('=');
				if (eq > 0) {
					instance.setVital(vital.substring(0, eq), Double.parseDouble(vital.substring(eq + 1)));
				}
			}
		}
		return instance;
	}

	private List<String> toList() {
		List<String> entries = new ArrayList<>(diseases.size());
		for (DiseaseInstance instance : diseases) {
			StringBuilder builder = new StringBuilder()
					.append(instance.diseaseId()).append('|')
					.append(instance.stage()).append('|')
					.append(instance.pulses());

			Map<String, Double> vitals = instance.vitalsView();
			if (!vitals.isEmpty()) {
				builder.append('|');
				boolean first = true;
				for (Map.Entry<String, Double> vital : vitals.entrySet()) {
					if (!first) {
						builder.append(';');
					}
					first = false;
					builder.append(vital.getKey()).append('=').append(vital.getValue());
				}
			}
			entries.add(builder.toString());
		}
		return entries;
	}

	@Override
	public String toString() {
		return "MedicalProfile" + toList();
	}
}
