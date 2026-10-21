package com.betterhotbarswap.client.toolbar;

import com.betterhotbarswap.BetterHotbarSwap;
import com.mojang.datafixers.DataFixer;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.collection.DefaultedList;

import java.io.File;
import java.util.List;

// 9 kayıt yuvası, her biri TAM envanter düzeni: index = PlayerInventory index
// (0-8 hotbar, 9-35 envanter), 36 = sol el (inv 40). Dosya formatı vanilla
// hotbar.nbt ile AYNI ("0".."8" -> eşya listesi + DataVersion), sadece liste 37
// uzunlukta; böylece vanilla DataFixTypes.HOTBAR sürüm yükseltmesinde de eşyaları
// dönüştürür. Eski 9 slotluk dosya da okunur (ilk 9 = hotbar).
public final class ToolbarStorage {
    public static final int SIZE = 37;
    public static final int OFFHAND = 36;

    private final File file;
    private final DataFixer fixer;
    @SuppressWarnings("unchecked")
    private final DefaultedList<ItemStack>[] bars = new DefaultedList[9];
    private boolean loaded;

    public ToolbarStorage(File file, DataFixer fixer) {
        this.file = file;
        this.fixer = fixer;
        for (int i = 0; i < 9; i++) bars[i] = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    }

    public List<ItemStack> get(int index) {
        if (!loaded) load();
        return bars[index];
    }

    public boolean isEmpty(int index) {
        for (ItemStack s : get(index)) if (!s.isEmpty()) return false;
        return true;
    }

    private void load() {
        loaded = true;
        try {
            NbtCompound nbt = NbtIo.read(file);
            if (nbt == null) return;
            nbt = DataFixTypes.HOTBAR.update(fixer, nbt, NbtHelper.getDataVersion(nbt, 1343));
            for (int i = 0; i < 9; i++) {
                NbtList list = nbt.getList(String.valueOf(i), NbtCompound.COMPOUND_TYPE);
                for (int j = 0; j < Math.min(list.size(), SIZE); j++) {
                    bars[i].set(j, ItemStack.fromNbt(list.getCompound(j)));
                }
            }
        } catch (Exception e) {
            BetterHotbarSwap.LOGGER.error("Failed to load toolbars", e);
        }
    }

    public void save() {
        try {
            NbtCompound root = NbtHelper.putDataVersion(new NbtCompound());
            for (int i = 0; i < 9; i++) {
                NbtList list = new NbtList();
                for (ItemStack s : get(i)) list.add(s.writeNbt(new NbtCompound()));
                root.put(String.valueOf(i), list);
            }
            NbtIo.write(root, file);
        } catch (Exception e) {
            BetterHotbarSwap.LOGGER.error("Failed to save toolbars", e);
        }
    }
}
