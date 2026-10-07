package net.righthandedvoid.imperfectnpcs;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.righthandedvoid.imperfectnpcs.command.ImperfectNPCCommands;
import net.righthandedvoid.imperfectnpcs.registry.ModCreativeTabs;
import net.righthandedvoid.imperfectnpcs.registry.ModItems;
import net.righthandedvoid.imperfectnpcs.registry.ModEntities;
import net.righthandedvoid.imperfectnpcs.scripting.LuaScriptRunner;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptManager;
import org.apache.logging.log4j.core.script.Script;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(PerfectNPCs.MODID)
public class PerfectNPCs {
    public static final String MODID = "perfectnpcs";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static ScriptManager scriptManager = new ScriptManager();

    public PerfectNPCs(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(ModEntities::registerAttributes);

        NeoForge.EVENT_BUS.register(PerfectNPCs.class);
        NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) ->
                        ImperfectNPCCommands.register(
                                event.getDispatcher()
                        )
        );
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) throws IOException {
        scriptManager.onServerStart(event);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        scriptManager.onServerStop(event);
    }
}
