package net.righthandedvoid.imperfectnpcs.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.righthandedvoid.imperfectnpcs.registry.ModEntities;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;

public final class NpcAPI {

    private NpcAPI() {

    }

    /**
     * Spawns a new ImperfectNPC in the given level.
     *
     * @param level    The server level to spawn the NPC in.
     * @param position The position where the NPC should spawn.
     * @return The newly spawned NPC.
     */
    public static NPCEntity spawn(ServerLevel level, Vec3 position) {

        NPCEntity npc = ModEntities.NPC.get().spawn(
                level,
                BlockPos.containing(position),
                MobSpawnType.COMMAND
        );

        if (npc == null) {
            throw new IllegalStateException(
                    "Failed to spawn ImperfectNPC at " + position
            );
        }

        // EntityType.spawn() works with a BlockPos, so move it to the
        // exact Vec3 position afterward.
        npc.setPos(
                position.x,
                position.y,
                position.z
        );

        return npc;
    }

    /**
     * Immediately removes an NPC from the world.
     *
     * This is not the same as killing the NPC.
     *
     * @param npc The NPC to remove.
     */
    public static void remove(NPCEntity npc) {
        npc.discard();
    }
}
