package net.righthandedvoid.imperfectnpcs.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.righthandedvoid.imperfectnpcs.ImperfectNPCs;

import java.util.function.Supplier;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ImperfectNPCs.MODID);

    public static final Supplier<CreativeModeTab> MAIN =
            CREATIVE_MODE_TABS.register(
                    "main",
                    () -> CreativeModeTab.builder()

                            .title(
                                    Component.translatable(
                                            "itemGroup." + ImperfectNPCs.MODID + ".main"
                                    )
                            )

                            .icon(
                                    () -> new ItemStack(
                                            ModItems.NPC_SPAWN_EGG.get()
                                    )
                            )

                            .displayItems((parameters, output) -> {
                                output.accept(
                                        ModItems.NPC_SPAWN_EGG.get()
                                );
                            })

                            .build()
            );

    private ModCreativeTabs() {}
}
