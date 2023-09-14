package net.blay09.mods.inventoryessentials;

import net.blay09.mods.balm.api.config.BalmConfigData;
import net.blay09.mods.balm.api.config.Comment;
import net.blay09.mods.balm.api.config.Config;

@Config(InventoryEssentials.MOD_ID)
public class InventoryEssentialsConfigData implements BalmConfigData {

    @Comment("Use the client implementation even on servers that have the mod installed - only useful for development purposes.")
    public boolean forceClientImplementation;

    @Comment("Should space-clicking move all items even if an empty slot was clicked?")
    public boolean allowBulkTransferAllOnEmptySlot = false;

    @Comment("You can now go into your Controls and unbind the respective Inventory Essentials keys instead!")
    public boolean whereDidTheConfigsGo = true;

}

