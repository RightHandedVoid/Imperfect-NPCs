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

        wideModel = this.getModel();
        slimModel = new PlayerModel<>(
                context.bakeLayer(ModelLayers.PLAYER_SLIM),
                true
        );
    }

    @Override
    public void render(
            NPCEntity entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight
    ) {
        if (!entity.getHasWideArms()) {
            model = slimModel;
        } else {
            model = wideModel;
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull NPCEntity entity) {
        String skinPath = entity.getSkinPath();
        ResourceLocation skinLocation;

        if (skinPath.isEmpty()) {
            skinLocation = STEVE_TEXTURE;
        } else {
            skinLocation = ResourceLocation.tryParse(skinPath);

            if (skinLocation == null) skinLocation = STEVE_TEXTURE;
        }

        return skinLocation;
    }
}
