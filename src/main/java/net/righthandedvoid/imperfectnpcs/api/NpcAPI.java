package net.righthandedvoid.imperfectnpcs.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;
import net.righthandedvoid.imperfectnpcs.registry.ModEntities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
     * Finds a loaded NPC by its UUID.
     *
     * @return The NPC, or null if no loaded NPC has that UUID.
     */
    public static @Nullable NPCEntity get(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid) instanceof NPCEntity npc ? npc : null;
    }

    /**
     * Finds every living NPC within a radius of a point, closest first.
     */
    public static List<NPCEntity> findNearby(ServerLevel level, Vec3 center, double radius) {
        double radiusSqr = radius * radius;

        List<NPCEntity> found = new ArrayList<>(level.getEntitiesOfClass(
                NPCEntity.class,
                AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                npc -> npc.isAlive() && npc.distanceToSqr(center) <= radiusSqr
        ));

        found.sort(Comparator.comparingDouble(npc -> npc.distanceToSqr(center)));

        return found;
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