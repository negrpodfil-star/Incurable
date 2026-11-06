package com.incurable.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.incurable.Incurable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Small JSON config for the medical engine. Deliberately simple: everything the pulse loop
 * needs to know lives here so server owners can tune the difficulty without a rebuild.
 */
public final class IncurableConfig {

	private static final Path DIR = Paths.get("config");
	private static final Path FILE = DIR.resolve("incurable.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** When false, diseases are wiped if the player dies (see {@code Incurable#onInitialize}). */
	public boolean persistAcrossDeath = true;

	/** Length of the mandatory progression cycle. Default: 1 minute = 1200 ticks. */
	public int pulseIntervalTicks = 1200;

	/** Immunity below this value (0-100) makes the infection roll succeed. */
	public int immunityThreshold = 50;

	/**
	 * Probability (0-1) to contract a disease on a pulse when immunity is broken.
	 * Defaults to 1.0: the design requires that a critically weakened immune system
	 * GUARANTEES a new infection.
	 */
	public double infectionChance = 1.0;

	/** Probability (0-1) that a disease infects a nearby victim when it is contagious. */
	public double contagionChance = 0.35;

	/** Hard floor for the radiation heart-loss mechanic (20 = 10 hearts). */
	public double minimumMaxHealth = 4.0;

	/** Verbose logging of every pulse / infection / cure. */
	public boolean debugLogging = false;

	/** Client-side: draw the medical HUD in the top-left corner. */
	public boolean showHud = true;

	private static IncurableConfig instance;

	public static IncurableConfig get() {
		if (instance == null) {
			instance = new IncurableConfig();
		}
		return instance;
	}

	public void load() {
		try {
			if (!Files.exists(FILE)) {
				save();
				return;
			}

			JsonObject root = GSON.fromJson(Files.readString(FILE), JsonObject.class);
			if (root == null) {
				return;
			}

			persistAcrossDeath = read(root, "persistAcrossDeath", persistAcrossDeath);
			pulseIntervalTicks = Math.max(20, read(root, "pulseIntervalTicks", pulseIntervalTicks));

			// Migration: an old config carrying the previous 5-minute default is bumped to the
			// new 1-minute cycle, so existing worlds pick up the change automatically.
			if (pulseIntervalTicks == 6000) {
				pulseIntervalTicks = 1200;
				Incurable.LOGGER.info("[Incurable] Migrated pulseIntervalTicks 6000 -> 1200 (1 minute)");
			}
			immunityThreshold = read(root, "immunityThreshold", immunityThreshold);
			// Migration: the old default (35) was unreachable for a healthy player, which made
			// infections effectively impossible - bump legacy configs to the new value.
			if (immunityThreshold <= 35) {
				immunityThreshold = 50;
				Incurable.LOGGER.info("[Incurable] Migrated immunityThreshold to 50");
			}
			infectionChance = read(root, "infectionChance", infectionChance);
			contagionChance = read(root, "contagionChance", contagionChance);
			minimumMaxHealth = read(root, "minimumMaxHealth", minimumMaxHealth);
			debugLogging = read(root, "debugLogging", debugLogging);
			showHud = read(root, "showHud", showHud);
		} catch (IOException | RuntimeException exception) {
			Incurable.LOGGER.warn("[Incurable] Could not load config, using defaults", exception);
		}
	}

	public void save() {
		try {
			Files.createDirectories(DIR);
			JsonObject root = new JsonObject();
			root.addProperty("persistAcrossDeath", persistAcrossDeath);
			root.addProperty("pulseIntervalTicks", pulseIntervalTicks);
			root.addProperty("immunityThreshold", immunityThreshold);
			root.addProperty("infectionChance", infectionChance);
			root.addProperty("contagionChance", contagionChance);
			root.addProperty("minimumMaxHealth", minimumMaxHealth);
			root.addProperty("debugLogging", debugLogging);
			root.addProperty("showHud", showHud);
			Files.writeString(FILE, GSON.toJson(root));
		} catch (IOException exception) {
			Incurable.LOGGER.warn("[Incurable] Could not save config", exception);
		}
	}

	private static boolean read(JsonObject root, String key, boolean fallback) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}

	private static int read(JsonObject root, String key, int fallback) {
		return root.has(key) ? root.get(key).getAsInt() : fallback;
	}

	private static double read(JsonObject root, String key, double fallback) {
		return root.has(key) ? root.get(key).getAsDouble() : fallback;
	}
}
