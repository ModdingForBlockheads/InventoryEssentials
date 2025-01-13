package net.blay09.mods.inventoryessentials;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.client.BalmClient;
import net.blay09.mods.balm.forge.ForgeLoadContext;
import net.blay09.mods.inventoryessentials.client.InventoryEssentialsClient;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.items.SlotItemHandler;

@Mod(InventoryEssentials.MOD_ID)
public class ForgeInventoryEssentials {

    public ForgeInventoryEssentials(FMLJavaModLoadingContext context) {
        final var loadContext = new ForgeLoadContext(context.getModEventBus());
        PlatformBindings.INSTANCE = new PlatformBindings() {
            @Override
            public boolean isSameInventory(Slot targetSlot, Slot slot) {
                if (targetSlot instanceof SlotItemHandler && slot instanceof SlotItemHandler) {
                    return ((SlotItemHandler) targetSlot).getItemHandler() == ((SlotItemHandler) slot).getItemHandler();
                }

                return slot.isSameInventory(targetSlot);
            }
        };

        Balm.initialize(InventoryEssentials.MOD_ID, loadContext, InventoryEssentials::initialize);
        if (FMLEnvironment.dist.isClient()) {
            BalmClient.initialize(InventoryEssentials.MOD_ID, loadContext, InventoryEssentialsClient::initialize);
        }

        context.registerDisplayTest(IExtensionPoint.DisplayTest.IGNORE_ALL_VERSION);
    }

}
