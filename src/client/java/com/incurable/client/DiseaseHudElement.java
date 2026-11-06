package com.incurable.client;

import com.incurable.Incurable;
import com.incurable.config.IncurableConfig;
import com.incurable.medical.Disease;
import com.incurable.medical.DiseaseCategory;
import com.incurable.medical.DiseaseInstance;
import com.incurable.medical.DiseaseRegistry;
import com.incurable.medical.MedicalProfile;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Client-side medical HUD, drawn in the top-left corner.
 *
 * <p>It renders the player's own {@link MedicalProfile} - the attachment is synchronised to the
 * owner's client by the server ({@code syncWith(..., targetOnly())}), so this class never has to
 * ask the server for anything. Vanilla clients simply never receive the attachment and therefore
 * never draw this panel.
 */
public final class DiseaseHudElement implements HudElement {

	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int HEADER_HEIGHT = 13;
	private static final int DOT = 3;
	private static final int GAP = 4;

	private static final int PANEL_BG = 0x90000000;
	private static final int PANEL_BORDER = 0x30FFFFFF;
	private static final int TEXT = 0xFFE8E8E8;
	private static final int TEXT_DIM = 0xFFA0A0A0;
	private static final int TEXT_TERMINAL = 0xFFFF5555;
	private static final int TEXT_HEADER = 0xFFFFE08A;

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, DeltaTracker delta) {
		if (!IncurableConfig.get().showHud) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		MedicalProfile profile = Incurable.profileOrNull(player);
		// The attachment arrives a moment after join; until then show nothing at all.
		if (profile == null) {
			return;
		}

		Font font = client.font;
		int x = 4;
		int y = 4;

		boolean healthy = !profile.hasDiseases();
		Component header = healthy
				? Component.translatable("hud.incurable.title")
				: Component.translatable("hud.incurable.title").append(" (" + profile.size() + ")");

		// Measure the panel first so the background can be drawn behind the text.
		int width = font.width(header);
		int rows = healthy ? 1 : profile.size();

		if (healthy) {
			width = Math.max(width, DOT + GAP + font.width(Component.translatable("hud.incurable.healthy")));
		} else {
			for (DiseaseInstance instance : profile.diseases()) {
				width = Math.max(width, DOT + GAP + font.width(lineFor(instance)));
			}
		}

		int height = HEADER_HEIGHT + rows * LINE_HEIGHT + PADDING;

		// Background + a subtle 1px border.
		ctx.fill(x - PADDING, y - PADDING, x - PADDING + width + PADDING * 2,
				y - PADDING + height + PADDING, PANEL_BG);
		ctx.fill(x - PADDING, y - PADDING, x - PADDING + width + PADDING * 2, y - PADDING + 1, PANEL_BORDER);
		ctx.fill(x - PADDING, y - PADDING + height + PADDING - 1,
				x - PADDING + width + PADDING * 2, y - PADDING + height + PADDING, PANEL_BORDER);

		// Header.
		ctx.text(font, header, x, y, TEXT_HEADER, true);
		y += HEADER_HEIGHT;

		if (healthy) {
			ctx.fill(x, y + 3, x + DOT, y + 3 + DOT, 0xFF55FF55);
			ctx.text(font, Component.translatable("hud.incurable.healthy"), x + DOT + GAP, y, TEXT_DIM, true);
			return;
		}

		// One row per active disease.
		for (DiseaseInstance instance : profile.diseases()) {
			Disease disease = DiseaseRegistry.get(instance.diseaseId());
			int color = colorFor(disease, instance);

			ctx.fill(x, y + 3, x + DOT, y + 3 + DOT, color);
			ctx.text(font, lineFor(instance), x + DOT + GAP, y, instance.isTerminal() ? TEXT_TERMINAL : TEXT, true);

			y += LINE_HEIGHT;
		}
	}

	/** {@code "Influenza  I/II"} - or a raw id when the registry entry is missing. */
	private static Component lineFor(DiseaseInstance instance) {
		Disease disease = DiseaseRegistry.get(instance.diseaseId());

		if (disease == null) {
			return Component.literal(instance.diseaseId().getPath() + "  " + (instance.stage() + 1));
		}

		Component name = Component.translatable("disease.incurable." + disease.path());
		Component stage = Component.translatable(disease.stage(instance.stage()).translationKey());

		if (disease.stageCount() > 1) {
			stage = stage.copy().append(Component.literal(" " + (instance.stage() + 1) + "/" + disease.stageCount())
					.withStyle(ChatFormatting.GRAY));
		}

		return Component.literal("").append(name).append("  ").append(stage);
	}

	private static int colorFor(Disease disease, DiseaseInstance instance) {
		if (instance.isTerminal()) {
			return TEXT_TERMINAL;
		}
		if (disease == null) {
			return TEXT_DIM;
		}

		return switch (disease.category()) {
			case INFECTIOUS -> 0xFFFF5B5B;
			case ENVIRONMENTAL -> 0xFFFFA83B;
			case CHRONIC -> 0xFFFFE066;
			case PARASITIC -> 0xFFB98CFF;
			case PSYCHIATRIC -> 0xFF6FA8FF;
			case SYNDROME -> 0xFF4FE0C8;
			case NEUROLOGICAL -> 0xFFC792FF;
		};
	}
}
