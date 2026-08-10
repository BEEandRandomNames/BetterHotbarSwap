package com.betterhotbarswap.client.input;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;

public final class SlotClassifier {
    private SlotClassifier() {
    }

    public static boolean isPlayerInventorySlot(Slot slot, PlayerEntity player) {
        return player != null && slot.inventory == player.getInventory();
    }

    public static boolean isHotbarSlot(Slot slot, PlayerEntity player) {
        return isPlayerInventorySlot(slot, player) && slot.getIndex() >= 0 && slot.getIndex() < PlayerInventory.getHotbarSize();
    }

    public static boolean isMainInventorySlot(Slot slot, PlayerEntity player) {
        return isPlayerInventorySlot(slot, player) && slot.getIndex() >= PlayerInventory.getHotbarSize() && slot.getIndex() < 36;
    }

    public static boolean isProbableOutputSlot(Slot slot, PlayerEntity player) {
        if (slot == null || player == null || !slot.hasStack() || !slot.canTakeItems(player)) {
            return false;
        }

        if (slot instanceof CraftingResultSlot) {
            return true;
        }

        ItemStack stack = slot.getStack();
        return !isPlayerInventorySlot(slot, player) && !stack.isEmpty() && !slot.canInsert(stack);
    }

    public static boolean isGeneralStorageSlot(Slot slot, PlayerEntity player, boolean includeHotbar) {
        if (slot == null || player == null || !slot.canTakeItems(player)) {
            return false;
        }

        if (includeHotbar && isHotbarSlot(slot, player)) {
            return true;
        }
        return isMainInventorySlot(slot, player);
    }
}
