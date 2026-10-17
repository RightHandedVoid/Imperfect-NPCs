package net.righthandedvoid.imperfectnpcs.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The scripting-facing view of an NPC. Every language binding wraps this
 * class instead of touching NPCEntity directly, so the API is defined once.
 *
 * A ScriptNPC is a handle, not the entity itself: it stores the NPC's UUID and
 * looks the entity up on every call. A handle a script keeps around therefore
 * keeps working after the NPC's chunk unloads and loads again, which creates a
 * brand-new NPCEntity object with the same UUID.
 */
public final class ScriptNPC {
    private final ServerLevel level;
    private final UUID uuid;

    public ScriptNPC(NPCEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("Scripts can only control server-side NPCs");
        }

        this.level = serverLevel;
        this.uuid = entity.getUUID();
    }

    /** The live entity, or null if it's dead, removed, or in an unloaded chunk. */
    private @Nullable NPCEntity resolve() {
        if (level.getEntity(uuid) instanceof NPCEntity npc && npc.isAlive()) return npc;

        return null;
    }

    /** False while the NPC is dead, removed, or unloaded. */
    public boolean isValid() { return resolve() != null; }

    private NPCEntity entity() {
        NPCEntity npc = resolve();

        if (npc == null) throw new IllegalStateException("This NPC no longer exists or isn't loaded");

        return npc;
    }

    public String getUUID() { return uuid.toString(); }

    public String getName() {
        Component name = entity().getCustomName();

        return name == null ? "" : name.getString();
    }

    public void setName(String name) { entity().setNpcName(name); }

    public String getSkin() { return entity().getSkinPath(); }

    public void setSkin(String skin) {
        if (ResourceLocation.tryParse(skin) == null) {
            throw new IllegalArgumentException("Invalid skin location: " + skin);
        }

        entity().setSkinPath(skin);
    }

    public double getX() { return entity().getX(); }

    public double getY() { return entity().getY(); }

    public double getZ() { return entity().getZ(); }

    public void teleport(double x, double y, double z) { entity().teleportTo(x, y, z); }

    /** Starts pathfinding toward a point. Returns false if no path was found. */
    public boolean walkTo(double x, double y, double z, double speed) {
        return entity().getNavigation().moveTo(x, y, z, speed);
    }

    public float getHealth() { return entity().getHealth(); }

    public void setHealth(float health) { entity().setHealth(health); }

    public void remove() { entity().discard(); }

    // Two handles to the same NPC are equal, so scripts can compare them
    // and use them as table keys.
    @Override
    public boolean equals(Object other) {
        return other instanceof ScriptNPC npc && uuid.equals(npc.uuid);
    }

    @Override
    public int hashCode() { return uuid.hashCode(); }
}