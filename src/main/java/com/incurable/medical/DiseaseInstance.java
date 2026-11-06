package com.incurable.medical;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A disease object attached to a single player.
 *
 * <h2>INVARIANTS (the core rule of the whole mod)</h2>
 * <ol>
 *     <li>The disease is {@link #isPermanent() permanent}. {@link #permanent} is {@code final}
 *     and initialised to {@code true}; there is deliberately no setter and no method that
 *     clears it. The only way to remove a {@code DiseaseInstance} from a player is
 *     {@link MedicalProfile#cure(Identifier)}, which is never called by a timer.</li>
 *     <li>{@link #ticksInStage} and {@link #totalTicks} are monotonically increasing. They are
 *     only ever advanced with a strictly positive delta; no method subtracts, clamps down or
 *     resets them on its own. They are reset <b>only</b> as a side effect of
 *     {@link #escalate()} - i.e. when the disease gets <b>worse</b>.</li>
 *     <li>{@link #stage} can only move upward. {@link #escalate()} returns {@code false} once the
 *     terminal stage is reached; it never returns to a lighter stage.</li>
 * </ol>
 * Consequently the one-minute tick cycle can only ever make the player sicker.
 */
public final class DiseaseInstance {

	/** One progression step == 1 real minute == 1200 ticks. */
	public static final long TICKS_PER_STAGE = 1200L;

	private final Identifier diseaseId;

	/** Current stage index. Only {@link #escalate()} may change it, and only upward. */
	private int stage;

	/** Ticks spent in the current stage. Monotonic until an escalation happens. */
	private long ticksInStage;

	/** Total ticks the player has carried this disease. Never decreases. */
	private long totalTicks;

	/** When {@code true} the instance forbids spontaneous removal. Always true. */
	private final boolean permanent = true;

	/** Number of one-minute pulses this instance has survived. Never decreases. */
	private int pulses;

	/** Behaviour-specific scalars, e.g. {@code sugar}, {@code possession}, {@code heartLoss}. */
	private final Map<String, Double> vitals = new HashMap<>();

	public DiseaseInstance(Identifier diseaseId) {
		this(diseaseId, 0);
	}

	public DiseaseInstance(Identifier diseaseId, int stage) {
		this.diseaseId = diseaseId;
		this.stage = Math.max(0, stage);
		this.ticksInStage = 0L;
		this.totalTicks = 0L;
		this.pulses = 0;
	}

	public Identifier diseaseId() {
		return diseaseId;
	}

	/** Resolves the immutable definition; nullable if the registry entry disappeared. */
	public Disease disease() {
		return DiseaseRegistry.get(diseaseId);
	}

	public int stage() {
		return stage;
	}

	public long ticksInStage() {
		return ticksInStage;
	}

	public long totalTicks() {
		return totalTicks;
	}

	public int pulses() {
		return pulses;
	}

	/** {@code true} forever - this is the architectural guarantee, not a gameplay toggle. */
	public boolean isPermanent() {
		return permanent;
	}

	public boolean isTerminal() {
		Disease definition = disease();
		return definition != null && definition.stage(stage).terminal();
	}

	/**
	 * Advances the disease clock. {@code deltaTicks} must be positive; anything else is ignored.
	 * There is intentionally no counterpart that could rewind the clock.
	 */
	public void advance(long deltaTicks) {
		if (!permanent || deltaTicks <= 0L) {
			return;
		}

		this.ticksInStage += deltaTicks;
		this.totalTicks += deltaTicks;
		this.pulses++;
	}

	/**
	 * Moves the disease one stage closer to its terminal form.
	 *
	 * @return {@code true} if the disease progressed, {@code false} if it is already terminal.
	 */
	public boolean escalate() {
		Disease definition = disease();

		if (definition == null || stage >= definition.maxStage()) {
			return false;
		}

		this.stage++;
		// Reset is allowed here ONLY because the disease just became harder.
		this.ticksInStage = 0L;
		return true;
	}

	public double vital(String key, double fallback) {
		return vitals.getOrDefault(key, fallback);
	}

	public double addVital(String key, double delta) {
		double value = vital(key, 0.0) + delta;
		vitals.put(key, value);
		return value;
	}

	public void setVital(String key, double value) {
		vitals.put(key, value);
	}

	/** Used when restoring a persisted / synchronised instance. */
	public void setPulses(int pulses) {
		this.pulses = Math.max(0, pulses);
	}

	/**
	 * Creates an independent copy of this instance.
	 *
	 * <p>Used when publishing the player's medical state to the client: Fabric's attachment
	 * change detection compares the old and the new value with {@code Objects.equals}, so the
	 * mutable instance must be replaced by a fresh object for the update to be sent.
	 */
	public DiseaseInstance copy() {
		DiseaseInstance copy = new DiseaseInstance(diseaseId, stage);
		copy.ticksInStage = this.ticksInStage;
		copy.totalTicks = this.totalTicks;
		copy.pulses = this.pulses;
		copy.vitals.putAll(this.vitals);
		return copy;
	}

	public Map<String, Double> vitalsView() {
		return Collections.unmodifiableMap(vitals);
	}

	@Override
	public String toString() {
		return "DiseaseInstance[" + diseaseId + " stage=" + stage + "/" + (disease() == null ? "?" : disease().maxStage())
				+ " ticks=" + ticksInStage + " pulses=" + pulses + " permanent=" + permanent + "]";
	}
}
