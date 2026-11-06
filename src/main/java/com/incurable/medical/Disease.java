package com.incurable.medical;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A data-oriented disease definition stored inside {@link DiseaseRegistry}.
 *
 * <p>A {@code Disease} is immutable and shared by every player who carries it. All
 * per-player state (current stage, timers, body vitals) lives in {@link DiseaseInstance}.
 */
public final class Disease {

	private final Identifier id;
	private final DiseaseCategory category;
	private final String behavior;
	private final List<DiseaseStage> stages;

	public Disease(Identifier id, DiseaseCategory category, String behavior, List<DiseaseStage> stages) {
		if (stages.isEmpty()) {
			throw new IllegalArgumentException("Disease " + id + " must define at least one stage");
		}

		this.id = id;
		this.category = category;
		this.behavior = behavior;
		this.stages = List.copyOf(stages);
	}

	public Identifier id() {
		return id;
	}

	public String path() {
		return id.getPath();
	}

	public DiseaseCategory category() {
		return category;
	}

	/**
	 * Behaviour key consumed by {@code DiseaseTickMixin} / {@code DiseaseBehaviors}.
	 * Kept as a string so definitions stay pure data and can later be loaded from JSON.
	 */
	public String behavior() {
		return behavior;
	}

	public List<DiseaseStage> stages() {
		return stages;
	}

	public int stageCount() {
		return stages.size();
	}

	public int maxStage() {
		return stages.size() - 1;
	}

	/** Clamped lookup so a corrupted save can never throw. */
	public DiseaseStage stage(int index) {
		return stages.get(Math.max(0, Math.min(index, stages.size() - 1)));
	}

	@Override
	public String toString() {
		return "Disease[" + id + ", stages=" + stages.size() + "]";
	}
}
