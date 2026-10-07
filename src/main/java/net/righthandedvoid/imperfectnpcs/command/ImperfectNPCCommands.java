package net.righthandedvoid.imperfectnpcs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.righthandedvoid.imperfectnpcs.api.NpcAPI;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;

import java.util.Collection;

public final class ImperfectNPCCommands {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
                Commands.literal("imperfectnpcs")
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
        );
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
