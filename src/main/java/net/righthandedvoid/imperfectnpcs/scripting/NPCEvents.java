package net.righthandedvoid.imperfectnpcs.scripting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.righthandedvoid.imperfectnpcs.api.ScriptNPC;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns things that happen to an NPC into script events. NPCEntity calls these
 * (on the server only), and they hand the events to ScriptManager.
 *
 * Built-in events and their fields (every event also has `npc`):
 *   init     - the script just started on this NPC
 *   tick     - fired every TICK_EVENT_INTERVAL ticks while the NPC is alive
 *   interact - a player right-clicked the NPC: player
 *   damaged  - the NPC was hurt and survived: amount, source, attacker
 *   died     - the NPC died: source, attacker
 *
 * `player` and `attacker` are tables with name, uuid and type; attacker is nil
 * when no entity caused the damage (falling, for example). `source` is
 * Minecraft's damage type name, like "player", "mob", "fall" or "lava".
 */
public final class NPCEvents {
    /** How often the tick event fires, in ticks. Every tick would be costly for little gain. */
    public static final int TICK_EVENT_INTERVAL = 10;

    private NPCEvents() {
    }

    public static void onTick(NPCEntity npc) {
        // Dying NPCs keep ticking for about a second during their death animation.
        if (!npc.isAlive()) return;

        ScriptManager.tickNpc(npc);

        if (npc.tickCount % TICK_EVENT_INTERVAL == 0) fire(npc, "tick", new HashMap<>());
    }

    public static void onInteract(NPCEntity npc, Player player) {
        Map<String, Object> data = new HashMap<>();
        data.put("player", describe(player));

        fire(npc, "interact", data);
    }

    public static void onDamaged(NPCEntity npc, DamageSource source, float amount) {
        Map<String, Object> data = new HashMap<>();
        data.put("amount", amount);
        data.put("source", source.getMsgId());
        data.put("attacker", source.getEntity() == null ? null : describe(source.getEntity()));

        fire(npc, "damaged", data);
    }

    public static void onDied(NPCEntity npc, DamageSource source) {
        Map<String, Object> data = new HashMap<>();
        data.put("source", source.getMsgId());
        data.put("attacker", source.getEntity() == null ? null : describe(source.getEntity()));

        fire(npc, "died", data);
    }

    private static void fire(NPCEntity npc, String name, Map<String, Object> data) {
        if (npc.getScriptName().isEmpty()) return;

        data.put("npc", new ScriptNPC(npc));

        ScriptManager.fireNpcEvent(npc, new ScriptEvent(name, data));
    }

    /** The basic facts about an entity that scripts get, until there's a proper player/entity API. */
    private static Map<String, Object> describe(Entity entity) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", entity.getName().getString());
        info.put("uuid", entity.getStringUUID());
        info.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());

        return info;
    }
}