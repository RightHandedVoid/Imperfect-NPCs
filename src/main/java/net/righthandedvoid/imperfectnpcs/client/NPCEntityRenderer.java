package net.righthandedvoid.imperfectnpcs.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;
import org.jetbrains.annotations.NotNull;

public class NPCEntityRenderer extends HumanoidMobRenderer<NPCEntity, PlayerModel<NPCEntity>> {

    private static final ResourceLocation STEVE_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/player/wide/steve.png"
            );

    public NPCEntityRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new PlayerModel<>(
                        context.bakeLayer(ModelLayers.PLAYER),
                        false
                ),
                0.5F
        );
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull NPCEntity entity) {
        return STEVE_TEXTURE;
    }
}
