package com.incurable.command;

import com.incurable.Incurable;
import com.incurable.medical.Disease;
import com.incurable.medical.DiseaseInstance;
import com.incurable.medical.DiseaseRegistry;
import com.incurable.medical.MedicalEngine;
import com.incurable.medical.MedicalProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Operator tooling: {@code /incurable diagnose|infect|cure|immunity|list}.
 *
 * <p>The {@code cure} sub-command is the only place in the whole mod that removes a disease -
 * exactly like a real medicine would.
 */
public final class IncurableCommand {

	private IncurableCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("incurable")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("diagnose")
						.then(Commands.argument("target", EntityArgument.player())
								.executes(context -> diagnose(context.getSource(), EntityArgument.getPlayer(context, "target")))))
				.then(Commands.literal("infect")
						.then(Commands.argument("target", EntityArgument.player())
								.then(Commands.argument("disease", StringArgumentType.word())
										.executes(context -> infect(context.getSource(), EntityArgument.getPlayer(context, "target"),
												StringArgumentType.getString(context, "disease"))))))
				.then(Commands.literal("cure")
						.then(Commands.argument("target", EntityArgument.player())
								.then(Commands.literal("all")
										.executes(context -> cureAll(context.getSource(), EntityArgument.getPlayer(context, "target"))))
								.then(Commands.argument("disease", StringArgumentType.word())
										.executes(context -> cure(context.getSource(), EntityArgument.getPlayer(context, "target"),
												StringArgumentType.getString(context, "disease"))))))
				.then(Commands.literal("immunity")
						.then(Commands.argument("target", EntityArgument.player())
								.executes(context -> immunity(context.getSource(), EntityArgument.getPlayer(context, "target")))))
				.then(Commands.literal("list")
						.executes(context -> list(context.getSource()))));
	}

	private static int diagnose(CommandSourceStack source, ServerPlayer target) {
		MedicalProfile profile = Incurable.profile(target);

		if (!profile.hasDiseases()) {
			source.sendSuccess(() -> Component.literal(target.getScoreboardName() + " is healthy (immunity " + MedicalEngine.immunity(target) + "%)")
					.withStyle(ChatFormatting.GREEN), false);
			return 1;
		}

		source.sendSuccess(() -> Component.literal(target.getScoreboardName() + " is suffering from:").withStyle(ChatFormatting.DARK_RED), false);
		for (DiseaseInstance instance : profile.diseases()) {
			Disease disease = instance.disease();
			String name = disease == null ? instance.diseaseId().toString() : disease.id().getPath();
			source.sendSuccess(() -> Component.literal(" - " + name
					+ " [" + instance.stage() + "/" + (disease == null ? "?" : disease.maxStage()) + "]"
					+ " stage-ticks=" + instance.ticksInStage()
					+ " pulses=" + instance.pulses()
					+ (instance.isTerminal() ? " (TERMINAL)" : "")
					+ (instance.isPermanent() ? " [permanent]" : ""))
					.withStyle(ChatFormatting.RED), false);
		}
		return 1;
	}

	private static int infect(CommandSourceStack source, ServerPlayer target, String diseaseName) {
		Identifier id = resolve(diseaseName);
		Disease disease = DiseaseRegistry.get(id);

		if (disease == null) {
			source.sendFailure(Component.literal("Unknown disease: " + diseaseName));
			return 0;
		}

		boolean infected = Incurable.profile(target).infect(disease);
		if (infected) {
			source.sendSuccess(() -> Component.literal("Infected " + target.getScoreboardName() + " with " + disease.path())
					.withStyle(ChatFormatting.DARK_RED), true);
			return 1;
		}

		source.sendFailure(Component.literal(target.getScoreboardName() + " already has that disease."));
		return 0;
	}

	private static int cure(CommandSourceStack source, ServerPlayer target, String diseaseName) {
		Identifier id = resolve(diseaseName);

		if (MedicalEngine.cure(target, id)) {
			source.sendSuccess(() -> Component.literal("Cured " + target.getScoreboardName() + " of " + id.getPath())
					.withStyle(ChatFormatting.GREEN), true);
			return 1;
		}

		source.sendFailure(Component.literal(target.getScoreboardName() + " does not have that disease."));
		return 0;
	}

	private static int cureAll(CommandSourceStack source, ServerPlayer target) {
		int removed = MedicalEngine.cureAll(target);
		source.sendSuccess(() -> Component.literal("Cured " + removed + " disease(s) from " + target.getScoreboardName())
				.withStyle(ChatFormatting.GREEN), true);
		return removed;
	}

	private static int immunity(CommandSourceStack source, ServerPlayer target) {
		int immunity = MedicalEngine.immunity(target);
		source.sendSuccess(() -> Component.literal(target.getScoreboardName() + " immunity: " + immunity + "%")
				.withStyle(immunity < 35 ? ChatFormatting.RED : ChatFormatting.GREEN), false);
		return immunity;
	}

	private static int list(CommandSourceStack source) {
		for (Disease disease : DiseaseRegistry.all()) {
			source.sendSuccess(() -> Component.literal(disease.id() + " (" + disease.category().key()
					+ ", " + disease.stageCount() + " stages)").withStyle(ChatFormatting.GRAY), false);
		}
		return DiseaseRegistry.all().size();
	}

	private static Identifier resolve(String name) {
		if (name.indexOf(':') >= 0) {
			Identifier parsed = Identifier.tryParse(name);
			return parsed != null ? parsed : Incurable.id(name);
		}
		return Incurable.id(name);
	}
}
