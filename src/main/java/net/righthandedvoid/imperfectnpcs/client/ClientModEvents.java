package net.righthandedvoid.imperfectnpcs.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.righthandedvoid.imperfectnpcs.ImperfectNPCs;
import net.righthandedvoid.imperfectnpcs.registry.ModEntities;

@EventBusSubscriber(
        modid = ImperfectNPCs.MODID,
        value = Dist.CLIENT
)
public final class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event
    ) {
        event.registerEntityRenderer(
                ModEntities.NPC.get(),
                NPCEntityRenderer::new
        );
    }

    private ClientModEvents() {}
}
