package net.righthandedvoid.imperfectnpcs.registry;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.righthandedvoid.imperfectnpcs.ImperfectNPCs;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(
                ImperfectNPCs.MODID
            );

    public static final DeferredItem<Item> NPC_SPAWN_EGG =
            ITEMS.register(
                    "npc_spawn_egg",
                    () -> new DeferredSpawnEggItem(
                            ModEntities.NPC,
                            0x54170A,
                            0xD62602,
                            new Item.Properties()
                    )
            );

    private ModItems() {}
}
