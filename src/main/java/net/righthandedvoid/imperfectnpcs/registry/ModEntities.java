package net.righthandedvoid.imperfectnpcs.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.righthandedvoid.imperfectnpcs.ImperfectNPCs;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;

import java.util.function.Supplier;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                Registries.ENTITY_TYPE,
                ImperfectNPCs.MODID
            );

    public static final Supplier<EntityType<NPCEntity>> NPC =
            ENTITY_TYPES.register(
                    "npc",
                    () -> EntityType.Builder
                            .of(NPCEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.8F)
                            .build(
                                    ResourceKey.create(
                                            Registries.ENTITY_TYPE,
                                            ResourceLocation.fromNamespaceAndPath(
                                                    ImperfectNPCs.MODID,
                                                    "npc"
                                            )
                                    ).toString()
                            )
            );

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                NPC.get(),
                NPCEntity.createAttributes().build()
        );
    }

    public ModEntities() {}
}
