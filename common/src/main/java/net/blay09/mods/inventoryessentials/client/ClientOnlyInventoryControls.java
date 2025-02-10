package net.blay09.mods.inventoryessentials.client;

import net.blay09.mods.inventoryessentials.InventoryEssentialsConfig;
import net.blay09.mods.inventoryessentials.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ClientOnlyInventoryControls implements InventoryControls {

    @Override
    public boolean singleTransfer(AbstractContainerScreen<?> screen, Slot clickedSlot) {
        AbstractContainerMenu menu = screen.getMenu();
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        if (!clickedSlot.mayPickup(player)) {
            return false;
        }

        ItemStack targetStack = clickedSlot.getItem().copy();
        // If clicked stack only has a count of one to begin with, just do a normal shift-click on it
        if (targetStack.getCount() == 1) {
            slotClick(menu, clickedSlot, 0, ClickType.QUICK_MOVE);
            return true;
        }

        Slot fallbackSlot = null;

        // Go through all slots in the container
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            // Skip the clicked slot, skip slots that do not accept the clicked item, skip slots that are of the same inventory (since we're moving between inventories), and skip slots that are already full
            if (!isValidTargetSlot(slot) || slot == clickedSlot || !slot.mayPlace(targetStack) || InventoryUtils.isSameInventory(clickedSlot, slot)
                    || stack.getCount() >= Math.min(slot.getMaxStackSize(), slot.getMaxStackSize(stack))) {
                continue;
            }

            // Prefer inputting into an existing stack if the items match
            if (ItemStack.isSameItemSameComponents(targetStack, stack)) {
                slotClick(menu, clickedSlot, 1, ClickType.PICKUP);
                slotClick(menu, slot, 1, ClickType.PICKUP);
                slotClick(menu, clickedSlot, 0, ClickType.PICKUP);
                return true;
            } else if (!slot.hasItem() && fallbackSlot == null) {
                // Remember the first empty slot and move the item there later in case we didn't find an existing stack
                fallbackSlot = slot;
            }
        }

        // There was no existing stack, so move the item into the first empty slot we found
        if (fallbackSlot != null) {
            slotClick(menu, clickedSlot, 1, ClickType.PICKUP);
            slotClick(menu, fallbackSlot, 1, ClickType.PICKUP);
            slotClick(menu, clickedSlot, 0, ClickType.PICKUP);
            return true;
        }

        return false;
    }

    @Override
    public boolean bulkTransferByType(AbstractContainerScreen<?> screen, Slot clickedSlot) {
        ItemStack targetStack = clickedSlot.getItem().copy();
        AbstractContainerMenu menu = screen.getMenu();
        List<Slot> transferSlots = new ArrayList<>();
        transferSlots.add(clickedSlot);
        for (Slot slot : menu.slots) {
            if (slot == clickedSlot || !isValidTargetSlot(slot)) {
                continue;
            }

            if (InventoryUtils.isSameInventory(slot, clickedSlot)) {
                ItemStack stack = slot.getItem();
                if (ItemStack.isSameItemSameComponents(targetStack, stack)) {
                    transferSlots.add(slot);
                }
            }
        }

        for (Slot transferSlot : transferSlots) {
            slotClick(menu, transferSlot, 0, ClickType.QUICK_MOVE);
        }

        return true;
    }

    @Override
    public boolean bulkTransferAll(AbstractContainerScreen<?> screen, Slot clickedSlot) {
        if (!clickedSlot.hasItem() && !InventoryEssentialsConfig.getActive().allowBulkTransferAllOnEmptySlot) {
            return false;
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        AbstractContainerMenu menu = screen.getMenu();

        boolean isProbablyMovingToPlayerInventory = false;
        // If the clicked slot is *not* from the player inventory,
        if (!(clickedSlot.container instanceof Inventory)) {
            // Search for any slot that belongs to the player inventory area (not hotbar)
            isProbablyMovingToPlayerInventory = InventoryUtils.containerContainsPlayerInventory(menu);
        }

        final var clickedEquippable = clickedSlot.getItem().get(DataComponents.EQUIPPABLE);
        boolean clickedAnArmorItem = clickedEquippable != null && clickedEquippable.slot().isArmor();
        boolean isInsideInventory = menu instanceof InventoryMenu;

        boolean movedAny = false;

        // If we're probably transferring to the player inventory, use transfer-to-inventory behaviour instead of just shift-clicking the items
        if (isProbablyMovingToPlayerInventory) {
            // To avoid O(n²), find empty and non-empty slots beforehand in one loop iteration
            Deque<Slot> emptySlots = new ArrayDeque<>();
            List<Slot> nonEmptySlots = new ArrayList<>();
            for (Slot slot : menu.slots) {
                if (InventoryUtils.isSameInventory(slot, clickedSlot) || !(slot.container instanceof Inventory) || !isValidTargetSlot(slot)) {
                    continue;
                }

                if (slot.hasItem()) {
                    nonEmptySlots.add(slot);
                } else if (!Inventory.isHotbarSlot(slot.getContainerSlot())) {
                    emptySlots.add(slot);
                }
            }

            // Now go through each slot that is accessible and belongs to the same inventory as the clicked slot
            NonNullList<Slot> slots = menu.slots;
            for (int i = slots.size() - 1; i >= 0; i--) {
                Slot slot = slots.get(i);
                if (!slot.mayPickup(player)) {
                    continue;
                }

                if (InventoryUtils.isSameInventory(slot, clickedSlot, true)) {
                    // and bulk-transfer each of them using the prefer-inventory behaviour
                    if (bulkTransferPreferInventory(menu, player.getInventory(), emptySlots, nonEmptySlots, slot)) {
                        movedAny = true;
                    }
                }
            }
        } else if (clickedAnArmorItem && isInsideInventory) {
            if (!InventoryEssentialsConfig.getActive().bulkTransferArmorSets) {
                return false;
            }

            // If holding an item in hand already, do nothing
            if (!menu.getCarried().isEmpty()) {
                return false;
            }

            // When clicking an equipped armor, un-equip all
            if (clickedSlot.index >= InventoryMenu.ARMOR_SLOT_START && clickedSlot.index < InventoryMenu.ARMOR_SLOT_END) {
                for (int i = InventoryMenu.ARMOR_SLOT_START; i < InventoryMenu.ARMOR_SLOT_END; i++) {
                    slotClick(menu, i, 0, ClickType.QUICK_MOVE);
                }
                return true;
            }

            // Swap current armor with clicked armor set
            final var armorSlots = InventoryUtils.findMatchingArmorSetSlots(menu, clickedSlot);
            final var equipmentSlots = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
            for (int i = InventoryMenu.ARMOR_SLOT_START; i < InventoryMenu.ARMOR_SLOT_END; i++) {
                final var equipmentSlot = equipmentSlots.get(i - InventoryMenu.ARMOR_SLOT_START);
                final var swapSlot = armorSlots.get(equipmentSlot);
                if (swapSlot != null) {
                    slotClick(menu, i, 0, ClickType.PICKUP);
                    slotClick(menu, swapSlot, 0, ClickType.PICKUP);
                    slotClick(menu, i, 0, ClickType.PICKUP);
                }
            }

            movedAny = true;
        } else {
            // Just a normal inventory-to-inventory transfer, simply shift-click the items
            for (Slot slot : menu.slots) {
                if (!slot.mayPickup(player) || !isValidTargetSlot(slot)) {
                    continue;
                }

                if (InventoryUtils.isSameInventory(slot, clickedSlot, true)) {
                    slotClick(menu, slot, 0, ClickType.QUICK_MOVE);
                    movedAny = true;
                }
            }

        }

        return movedAny;
    }

    private boolean bulkTransferPreferInventory(AbstractContainerMenu menu, Inventory inventory, Deque<Slot> emptySlots, List<Slot> nonEmptySlots, Slot slot) {
        ItemStack targetStack = slot.getItem().copy();
        if (targetStack.isEmpty()) {
            return false;
        }

        slotClick(menu, slot, 0, ClickType.PICKUP);

        for (Slot nonEmptySlot : nonEmptySlots) {
            ItemStack stack = nonEmptySlot.getItem();
            if (ItemStack.isSameItemSameComponents(targetStack, stack)) {
                boolean hasSpaceLeft = stack.getCount() < Math.min(slot.getMaxStackSize(), slot.getMaxStackSize(stack));
                if (!hasSpaceLeft) {
                    continue;
                }

                slotClick(menu, nonEmptySlot, 0, ClickType.PICKUP);
                ItemStack mouseItem = menu.getCarried();
                if (mouseItem.isEmpty()) {
                    return true;
                }
            }
        }

        for (Iterator<Slot> iterator = emptySlots.descendingIterator(); iterator.hasNext(); ) {
            Slot emptySlot = iterator.next();
            slotClick(menu, emptySlot, 0, ClickType.PICKUP);
            if (emptySlot.hasItem()) {
                nonEmptySlots.add(emptySlot);
                iterator.remove();
            }

            ItemStack mouseItem = menu.getCarried();
            if (mouseItem.isEmpty()) {
                return true;
            }
        }

        ItemStack mouseItem = menu.getCarried();
        if (!mouseItem.isEmpty()) {
            slotClick(menu, slot, 0, ClickType.PICKUP);
        }

        return false;
    }

    @Override
    public void dragTransfer(AbstractContainerScreen<?> screen, Slot clickedSlot) {
        slotClick(screen.getMenu(), clickedSlot, 0, ClickType.QUICK_MOVE);
    }

    protected void slotClick(AbstractContainerMenu menu, Slot slot, int mouseButton, ClickType clickType) {
        slotClick(menu, slot.index, mouseButton, clickType);
    }

    protected void slotClick(AbstractContainerMenu menu, int slotIndex, int mouseButton, ClickType clickType) {
        Player player = Minecraft.getInstance().player;
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (player != null && gameMode != null && (slotIndex >= 0 && slotIndex < menu.slots.size() || slotIndex == -999)) {
            gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, mouseButton, clickType, player);
        }
    }

    @Override
    public boolean dropByType(AbstractContainerScreen<?> screen, Slot hoverSlot) {
        ItemStack targetStack = hoverSlot.getItem().copy();
        AbstractContainerMenu menu = screen.getMenu();
        List<Slot> transferSlots = new ArrayList<>();
        transferSlots.add(hoverSlot);
        for (Slot slot : menu.slots) {
            if (slot == hoverSlot || !isValidTargetSlot(slot)) {
                continue;
            }

            if (InventoryUtils.isSameInventory(slot, hoverSlot)) {
                ItemStack stack = slot.getItem();
                if (ItemStack.isSameItemSameComponents(targetStack, stack)) {
                    transferSlots.add(slot);
                }
            }
        }

        for (Slot transferSlot : transferSlots) {
            slotClick(menu, transferSlot, 1, ClickType.THROW);
        }

        return true;
    }

    @Override
    public boolean dropByType(AbstractContainerScreen<?> screen, ItemStack targetStack) {
        if (targetStack.isEmpty()) {
            return false;
        }

        AbstractContainerMenu menu = screen.getMenu();
        List<Slot> transferSlots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (ItemStack.isSameItemSameComponents(targetStack, stack) && isValidTargetSlot(slot)) {
                transferSlots.add(slot);
            }
        }

        slotClick(menu, -999, 0, ClickType.PICKUP);
        for (Slot transferSlot : transferSlots) {
            slotClick(menu, transferSlot, 1, ClickType.THROW);
        }

        return true;
    }

    protected boolean isValidTargetSlot(Slot slot) {
        return true;
    }
}
