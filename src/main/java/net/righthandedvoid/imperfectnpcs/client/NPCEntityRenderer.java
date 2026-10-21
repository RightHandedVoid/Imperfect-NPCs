package net.righthandedvoid.imperfectnpcs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
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
    private static final ResourceLocation ALEX_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/player/slim/alex.png"
            );

    private final PlayerModel<NPCEntity> wideModel;
    private final PlayerModel<NPCEntity> slimModel;

    public NPCEntityRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new PlayerModel<>(
                    context.bakeLayer(ModelLayers.PLAYER),
                    false
                ),
                0.5F
        );

        wideModel = new PlayerModel<>(
                context.bakeLayer(ModelLayers.PLAYER),
                false
        );
        slimModel = new PlayerModel<>(
                context.bakeLayer(ModelLayers.PLAYER_SLIM), // bruh
                true
        );
    }

    @Override
    public void render(
            NPCEntity entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight
    ) {
        if (entity.isSlim()) {
            super.model = slimModel;
        } else {
            super.model = wideModel;
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull NPCEntity entity) {
        String skinPath = entity.getSkinPath();
        ResourceLocation skinLocation = null;

        if (!skinPath.isEmpty()) {
            skinLocation = ResourceLocation.tryParse(skinPath);

        }

        if (skinLocation != null) return skinLocation;

        if (entity.isSlim()) {
            skinLocation = ALEX_TEXTURE;

        } else {
            skinLocation = STEVE_TEXTURE;

        }

        return skinLocation;
    }
}
