package net.righthandedvoid.imperfectnpcs;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.righthandedvoid.imperfectnpcs.command.ImperfectNPCCommands;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;
import net.righthandedvoid.imperfectnpcs.registry.ModCreativeTabs;
import net.righthandedvoid.imperfectnpcs.registry.ModEntities;
import net.righthandedvoid.imperfectnpcs.registry.ModItems;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptManager;
import org.slf4j.Logger;

@Mod(ImperfectNPCs.MODID)
public class ImperfectNPCs {
    public static final String MODID = "imperfectnpcs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImperfectNPCs(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(ModEntities::registerAttributes);

        NeoForge.EVENT_BUS.register(ImperfectNPCs.class);
        NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) ->
                        ImperfectNPCCommands.register(
                                event.getDispatcher()
                        )
        );
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ScriptManager.onServerStart(event);
    }

    /**
     * Fires when any entity unloads, dies and despawns, is removed, or changes
     * dimension. An NPC's script instance goes with it; if the NPC comes back
     * (its chunk loads again), the script starts fresh on its next tick.
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof NPCEntity npc) {
            ScriptManager.onNpcRemoved(npc.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ScriptManager.onServerStop(event);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ScriptManager.tick(event.getServer());
    }
}