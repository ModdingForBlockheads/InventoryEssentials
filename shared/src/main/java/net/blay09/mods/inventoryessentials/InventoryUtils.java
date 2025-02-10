package net.blay09.mods.inventoryessentials;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class InventoryUtils {

    public static boolean isSameInventory(Slot targetSlot, Slot slot) {
        return isSameInventory(targetSlot, slot, false);
    }

    public static boolean isSameInventory(Slot targetSlot, Slot slot, boolean treatHotBarAsSeparate) {
        boolean isTargetPlayerInventory = targetSlot.container instanceof Inventory;
        boolean isTargetHotBar = isTargetPlayerInventory && Inventory.isHotbarSlot(targetSlot.getContainerSlot());
        boolean isPlayerInventory = slot.container instanceof Inventory;
        boolean isHotBar = isPlayerInventory && Inventory.isHotbarSlot(slot.getContainerSlot());

        if (isTargetPlayerInventory && isPlayerInventory && treatHotBarAsSeparate) {
            return isHotBar == isTargetHotBar;
        }

        return PlatformBindings.INSTANCE.isSameInventory(targetSlot, slot);
    }

    public static boolean containerContainsPlayerInventory(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() >= 9 && slot.getContainerSlot() < 37) {
                return true;
            }
        }

        return false;
    }

    public static Map<EquipmentSlot, Slot> findMatchingArmorSetSlots(AbstractContainerMenu menu, Slot baseSlot) {
        final var result = new HashMap<EquipmentSlot, Slot>();
        final var equipmentSlots = Arrays.stream(EquipmentSlot.values()).filter(EquipmentSlot::isArmor).toList();
        final var baseItem = baseSlot.getItem();
        if (baseItem.getItem() instanceof Equipable equipable) {
            result.put(equipable.getEquipmentSlot(), baseSlot);
        }

        for (final var slot : menu.slots) {
            if (menu instanceof InventoryMenu && slot.index >= InventoryMenu.ARMOR_SLOT_START && slot.index < InventoryMenu.ARMOR_SLOT_END) {
                continue;
            }

            final var slotStack = slot.getItem();
            if (slotStack.getItem() instanceof Equipable equipable && equipable.getEquipmentSlot().isArmor()) {
                if (isMatchingArmorSet(baseItem, slotStack) && !result.containsKey(equipable.getEquipmentSlot())) {
                    result.put(equipable.getEquipmentSlot(), slot);
                }
            }
            if (result.size() >= equipmentSlots.size()) {
                break;
            }
        }

        return result;
    }

    private static boolean isMatchingArmorSet(ItemStack baseItem, ItemStack otherItem) {
        if (baseItem.getItem() instanceof ArmorItem baseArmorItem && otherItem.getItem() instanceof ArmorItem otherArmorItem) {
            return baseArmorItem.getMaterial() == otherArmorItem.getMaterial();
        }

        return false;
    }
}
