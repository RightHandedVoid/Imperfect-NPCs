package net.righthandedvoid.imperfectnpcs.command;

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

import java.util.Collection;
import java.util.List;

public final class ImperfectNPCCommands {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(Commands.literal("npcs")
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
                        ImperfectNPCCommands::removeNPCs
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
                        Commands.literal("name")
                        .then(
                            Commands.argument(
                                "name",
                                StringArgumentType.string()
                            )
                            .executes(
                                ImperfectNPCCommands::setNPCName
                            )
                        )
                    )
                    .then(
                        Commands.literal("wide_arms")
                        .then(
                            Commands.argument(
                                "wide_arms",
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

        dispatcher.register(Commands.literal("script")
            .requires(cs -> cs.hasPermission(2))

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
        boolean has_wide_arms = BoolArgumentType.getBool(context, "wide_arms");

        if (entity instanceof NPCEntity npc) {
            npc.setHasWideArms(has_wide_arms);

            source.sendSuccess(
                    () -> Component.literal(
                            "Successfully changed the arms style!"
                    ),
                    true
            );

            return 1;
        } else {
            source.sendFailure(
                    Component.literal("Failed to change the arms style! Selected Entity is not an NPC!")
            );

            return 0;
        }
    }

    private static int setNPCName(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        Entity entity = EntityArgument.getEntity(context, "entity");
        String name = StringArgumentType.getString(context, "name");

        if (entity instanceof NPCEntity npc) {
            npc.setNpcName(name);

            source.sendSuccess(
                    () -> Component.literal(
                            "Successfully changed the name!"
                    ),
                    true
            );

            return 1;
        } else {
            source.sendFailure(
                    Component.literal("Failed to change the name! Selected Entity is not an NPC!")
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
        StringBuilder builder = new StringBuilder();

        builder.append(ScriptManager.getReloadLog());
        if (ScriptManager.getReloadErrorCount() > 0) builder.append(" Use \"/script errors\" to see them.");

        source.sendSuccess(
                () -> Component.literal(
                        builder.toString()
                ),
                true
        );

        return 1;
    }

    private static int runScript(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        String targetScript = StringArgumentType.getString(context, "script");
        List<String> foundScripts = ScriptManager.getFoundScripts();

        if (!foundScripts.contains(targetScript)) {
            source.sendFailure(
                    Component.literal("Script not found.")
            );

            return 0;
        }

        ScriptRecord scriptResult = ScriptManager.runScript(targetScript);

        if (scriptResult.scriptSuccess()) {
            source.sendSuccess(
                () -> Component.literal(
                        "Successfully ran " + targetScript
                ),
                true
            );

            return 1;

        } else {
            source.sendFailure(
                    Component.literal(
                            "Script Error: " + scriptResult.message()
                    )
            );

            return 0;
        }
    }

    private static int spawnNpc(
            CommandContext<CommandSourceStack> context
    ) {
        CommandSourceStack source = context.getSource();

        Vec3 position = Vec3Argument.getVec3(context, "position");

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

    private static int removeNPCs(
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
