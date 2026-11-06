package com.incurable.client;

import com.incurable.Incurable;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/**
 * Client entry point.
 *
 * <p>The only client-side feature is the medical HUD: the server pushes the player's
 * {@code incurable:medical_profile} attachment to their own client, and this element renders it
 * in the top-left corner. All gameplay logic stays server-authoritative.
 */
public final class IncurableClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		HudElementRegistry.addLast(Incurable.id("disease_hud"), new DiseaseHudElement());
	}
}
