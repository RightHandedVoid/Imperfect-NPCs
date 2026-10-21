package net.righthandedvoid.imperfectnpcs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.righthandedvoid.imperfectnpcs.api.NpcAPI;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptManager;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptRecord;

import java.util.ArrayList;
import java.util.Collection;

public final class ImperfectNPCCommands {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
            Commands.literal("npcs")
                .requires(cs -> cs.hasPermission(2))

                .then(
                    Commands.literal("spawn")
                        .then(
                            Commands.argument(
                                "position",
                                    Vec3Argument.vec3()
                                )
                                .executes(
                                    ImperfectNPCCommands::spawnNpc
                                )
                            )
                )

                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument(
                                "targets",
                                EntityArgument.entities()
                            )
                            .executes(
                                ImperfectNPCCommands::removeNpcs
                            )
                        )
                )

                .then(
                    Commands.literal("edit")
                        .then(
                            Commands.argument(
                                    "entity",
                                    EntityArgument.entity()
                            )
                            .then(
                                Commands.literal("skin")
                                .then(
                                    Commands.argument(
                                            "skin",
                                            ResourceLocationArgument.id()
                                        )
                                        .executes(
                                            ImperfectNPCCommands::setNPCSkin
                                        )
                                )

                            )
                            .then(
                                Commands.literal("arms")
                                .then(
                                    Commands.argument(
                                        "arms",
                                        BoolArgumentType.bool()
                                    )
                                    .executes(
                                        ImperfectNPCCommands::setNPCArms
                                    )
                                )
                            )
                        )
                )
        );

        dispatcher.register(
            Commands.literal("scripts")
                .requires(commandSourceStack -> commandSourceStack.hasPermission(2))

                .then(
                    Commands.literal("run")
                        .then(
                            Commands.argument(
                                    "script",
                                    StringArgumentType.greedyString()
                                )
                                .suggests(
                                    (context, builder) ->
                                        SharedSuggestionProvider.suggest(
                                            ScriptManager.getFoundScripts(),
                                            builder
                                        )
                                )
                                .executes(
                                    ImperfectNPCCommands::runScript
                                )
                        )
                )

                .then(
                    Commands.literal("reload")
                        .executes(
                            ImperfectNPCCommands::reloadScripts
                        )
                )

                .then(
                    Commands.literal("errors")
                        .executes(
                            ImperfectNPCCommands::listErrors
                        )
                )
        );
    }

    private static int setNPCArms(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        Entity entity = EntityArgument.getEntity(context, "entity");
        boolean isSlim = BoolArgumentType.getBool(context, "arms");

        if (entity instanceof NPCEntity npc) {
            npc.setSlimArms(isSlim);

            source.sendSuccess(
                () -> Component.literal(
                    "Successfully changed the skin's arms!"
                ),
                true
            );

            return 1;
        } else {
            source.sendFailure(
                Component.literal("Failed to change the skin's arms! Selected Entity is not an NPC!")
            );

            return 0;
        }
    }

    private static int setNPCSkin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        Entity entity = EntityArgument.getEntity(context, "entity");
        ResourceLocation skin = ResourceLocationArgument.getId(context, "skin");

        if (entity instanceof NPCEntity npc) {
            npc.setSkinPath(skin.toString());

            source.sendSuccess(
                    () -> Component.literal(
                            "Successfully changed the skin!"
                    ),
                    true
            );

            return 1;
        } else {
            source.sendFailure(
                    Component.literal("Failed to change the skin! Selected Entity is not an NPC!")
            );

            return 0;
        }
    }

    private static int listErrors(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(
                () -> Component.literal(
                        ScriptManager.getReloadLog()
                ),
                false
        );

        return 1;
    }

    private static int reloadScripts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        ScriptManager.reloadScripts();

        if (ScriptManager.getReloadErrorCount() > 0) {
            source.sendSuccess(
                    () -> Component.literal(
                            ScriptManager.getReloadLogSlim() + " Use \"/imperfectnpcs errors\" to see them."
                    ),
                    true
            );
        } else {
            source.sendSuccess(
                    () -> Component.literal(
                            ScriptManager.getReloadLogSlim()
                    ),
                    true
            );
        }

        return 1;
    }

    private static int runScript(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        String targetScript = StringArgumentType.getString(context, "script");

        if (!ScriptManager.getFoundScripts().contains(targetScript)) {
            source.sendFailure(
                    Component.literal("Script not found.")
            );

            return 0;
        }

        ScriptRecord scriptResult = ScriptManager.runScript(targetScript, source);

        if (scriptResult.scriptSuccess()) {
            source.sendSuccess(
                () -> Component.literal(
                        "Successfully ran " + targetScript
                ),
                true
            );

        } else {
            source.sendFailure(
                    Component.literal(
                            "Script Error: " + scriptResult.message()
                    )
            );
        }

        return scriptResult.scriptSuccess() ? 1 : 0;
    }

    private static int spawnNpc(
            CommandContext<CommandSourceStack> context
    ) {
        CommandSourceStack source = context.getSource();

        Vec3 position =
                Vec3Argument.getVec3(context, "position");

        NPCEntity npc = NpcAPI.spawn(
                source.getLevel(),
                position
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Spawned NPC " + npc.getUUID()
                ),
                true
        );

        return 1;
    }

    private static int removeNpcs(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {

        Collection<? extends Entity> entities =
                EntityArgument.getEntities(context, "targets");

        int removed = 0;

        for (Entity entity : entities) {
            if (entity instanceof NPCEntity npc) {
                NpcAPI.remove(npc);
                removed++;
            }
        }

        final int count = removed;

        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Removed " + count + " NPC(s)"
                ),
                true
        );

        return removed;
    }
}
