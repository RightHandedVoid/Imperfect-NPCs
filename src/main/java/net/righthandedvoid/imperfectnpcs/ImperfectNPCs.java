package net.righthandedvoid.imperfectnpcs;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.righthandedvoid.imperfectnpcs.command.ImperfectNPCCommands;
import net.righthandedvoid.imperfectnpcs.registry.ModCreativeTabs;
import net.righthandedvoid.imperfectnpcs.registry.ModEntities;
import net.righthandedvoid.imperfectnpcs.registry.ModItems;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ScriptManager.onServerStop(event);
    }
}
