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
        return player!= null && slot.inventory == player.getInventory();
    }

    public static boolean isHotbarSlot(Slot slot, PlayerEntity player) {
        return isPlayerInventorySlot(slot, player) && slot.getIndex() >= 0 && slot.getIndex() < PlayerInventory.getHotbarSize();
    }

    public static boolean isMainInventorySlot(Slot slot, PlayerEntity player) {
        return isPlayerInventorySlot(slot, player) && slot.getIndex() >= PlayerInventory.getHotbarSize() && slot.getIndex() < 36;
    }

    // NOT: adı creative kalsa da bu, survival dahil HER ekranda "alınabilir
    // container slotu" demektir (büyü masası lapis slotu, sandık vb. dahil).
    // Atama sistemi bilerek output/input/depo ayırmaz: hepsi aynı kural
    // zincirinden geçer (MERGE/PRIORITY/OVERFLOW); SADECE shift-bulk output'a
    // özeldir (üretim). Evrensellik ilkesi gereği blok-özel dal YOKTUR.
    public static boolean isCreativeListSlot(Slot slot, PlayerEntity player) {
        if (slot == null || player == null) return false;
        // Creative listesi: player inventory DEĞİL, stack var, ve alınabilir
        return slot.inventory!= player.getInventory()
                && slot.hasStack()
                && slot.canTakeItems(player);
    }

    public static boolean isProbableOutputSlot(Slot slot, PlayerEntity player) {
        return isProbableOutputSlot(slot, player, false);
    }

    // inCreativeScreen: Creative ekranındaki oyuncu-dışı slotlar liste slotudur,
    // output sayılmaz. Diğer ekranlarda davranışsal heuristic geçerlidir.
    // (Bug #21: eski sırada creative-kontrol CraftingResultSlot dalını öldürüyordu.)
    public static boolean isProbableOutputSlot(Slot slot, PlayerEntity player, boolean inCreativeScreen) {
        if (slot == null || player == null || !slot.hasStack() || !slot.canTakeItems(player)) {
            return false;
        }

        // Kesin output her şeyden önce (ölü dal olmamalı).
        if (slot instanceof CraftingResultSlot) {
            return true;
        }

        if (isPlayerInventorySlot(slot, player)) {
            return false;
        }

        if (inCreativeScreen) {
            return false;
        }

        ItemStack stack = slot.getStack();
        return !stack.isEmpty() && !slot.canInsert(stack);
    }

    public static boolean isGeneralStorageSlot(Slot slot, PlayerEntity player, boolean includeHotbar) {
        if (slot == null || player == null ||!slot.canTakeItems(player)) {
            return false;
        }

        if (includeHotbar && isHotbarSlot(slot, player)) {
            return true;
        }
        return isMainInventorySlot(slot, player);
    }

    // Zirh/offhand ASLA kaynak olmaz (vanilla yapsin, test matrisi). Bug #31.
    public static boolean isArmorOrOffhand(Slot slot, PlayerEntity player) {
        if (slot == null || player == null) return false;
        if (slot.inventory != player.getInventory()) return false;
        int idx = slot.getIndex();
        return idx >= 36 && idx <= 40;
    }

    // YENI: Bu slot hotbar'a atanabilir mi? (Creative + Survival hepsi)
    public static boolean isAssignableSlot(Slot slot, PlayerEntity player) {
        if (slot == null ||!slot.hasStack()) return false;
        if (isArmorOrOffhand(slot, player)) return false;
        return isPlayerInventorySlot(slot, player)
                || isCreativeListSlot(slot, player)
                || isProbableOutputSlot(slot, player);
    }
}