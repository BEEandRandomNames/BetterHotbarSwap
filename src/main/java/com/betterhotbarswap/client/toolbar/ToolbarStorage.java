package com.betterhotbarswap.client.toolbar;

import com.betterhotbarswap.BetterHotbarSwap;
import com.mojang.datafixers.DataFixer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// 9 kayıt yuvası, her biri TAM envanter düzeni: index = PlayerInventory index
// (0-8 hotbar, 9-35 envanter), 36 = sol el (inv 40). Dosya formatı vanilla
// hotbar.nbt ile AYNI ("0".."8" -> eşya listesi + DataVersion), sadece liste 37
// uzunlukta; böylece vanilla DataFixTypes.HOTBAR sürüm yükseltmesinde de eşyaları
// dönüştürür. Eski 9 slotluk dosya da okunur (ilk 9 = hotbar).
public final class ToolbarStorage {
    public static final int SIZE = 37;
    public static final int OFFHAND = 36;

    private final Path file; // 1.20.3+: NbtIo File yerine Path alır (vanilla HotbarStorage da öyle)
    private final DataFixer fixer;
    @SuppressWarnings("unchecked")
    private final DefaultedList<ItemStack>[] bars = new DefaultedList[9];
    // Okunamayan eşyanın ham NBT'si (ör. menüde bilinmeyen mod içeriği): slot boş
    // kalırsa kayıtta AYNEN geri yazılır, dosyadan eşya silinmez.
    private final NbtCompound[][] undecoded = new NbtCompound[9][SIZE];
    private RegistryWrapper.WrapperLookup loadedWith;
    private static RegistryWrapper.WrapperLookup menuLookup;

    public ToolbarStorage(Path file, DataFixer fixer) {
        this.file = file;
        this.fixer = fixer;
        for (int i = 0; i < 9; i++) bars[i] = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    }

    public List<ItemStack> get(int index) {
        if (loadedWith != lookup()) load();
        return bars[index];
    }

    public boolean isEmpty(int index) {
        for (ItemStack s : get(index)) if (!s.isEmpty()) return false;
        return true;
    }

    // 1.20.5+: eşya NBT'si kayıt (registry) bağlamıyla okunur/yazılır. Dünyadayken
    // dünyanınki, menüde vanilla'nın gömülü kayıtları; bağlam değişince yeniden okunur.
    private static RegistryWrapper.WrapperLookup lookup() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) return client.world.getRegistryManager();
        if (menuLookup == null) menuLookup = BuiltinRegistries.createWrapperLookup();
        return menuLookup;
    }

    private void load() {
        RegistryWrapper.WrapperLookup lookup = lookup();
        loadedWith = lookup;
        for (int i = 0; i < 9; i++) {
            Collections.fill(bars[i], ItemStack.EMPTY);
            Arrays.fill(undecoded[i], null);
        }
        try {
            if (!Files.exists(file)) return;
            NbtCompound nbt = NbtIo.read(file);
            if (nbt == null) return;
            nbt = DataFixTypes.HOTBAR.update(fixer, nbt, NbtHelper.getDataVersion(nbt, 1343));
            for (int i = 0; i < 9; i++) {
                NbtList list = nbt.getList(String.valueOf(i), NbtCompound.COMPOUND_TYPE);
                for (int j = 0; j < Math.min(list.size(), SIZE); j++) {
                    NbtCompound c = list.getCompound(j);
                    ItemStack s = ItemStack.fromNbtOrEmpty(lookup, c);
                    bars[i].set(j, s);
                    if (s.isEmpty() && c.contains("id") && !"minecraft:air".equals(c.getString("id"))) undecoded[i][j] = c;
                }
            }
        } catch (Exception e) {
            BetterHotbarSwap.LOGGER.error("Failed to load toolbars", e);
        }
    }

    // Çağıran her zaman önce get() ile aynı bağlamda yüklemiştir.
    public void save() {
        try {
            RegistryWrapper.WrapperLookup lookup = lookup();
            NbtCompound root = NbtHelper.putDataVersion(new NbtCompound());
            for (int i = 0; i < 9; i++) {
                NbtList list = new NbtList();
                for (int j = 0; j < SIZE; j++) {
                    ItemStack s = bars[i].get(j);
                    list.add(s.isEmpty() && undecoded[i][j] != null ? undecoded[i][j] : s.encodeAllowEmpty(lookup));
                }
                root.put(String.valueOf(i), list);
            }
            NbtIo.write(root, file);
        } catch (Exception e) {
            BetterHotbarSwap.LOGGER.error("Failed to save toolbars", e);
        }
    }
}
