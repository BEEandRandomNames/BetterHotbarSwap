package com.betterhotbarswap.client.toolbar;

import com.betterhotbarswap.BetterHotbarSwap;
import com.mojang.datafixers.DataFixer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
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
    private final NonNullList<ItemStack>[] bars = new NonNullList[9];
    // Okunamayan eşyanın ham NBT'si (ör. menüde bilinmeyen mod içeriği): slot boş
    // kalırsa kayıtta AYNEN geri yazılır, dosyadan eşya silinmez.
    private final CompoundTag[][] undecoded = new CompoundTag[9][SIZE];
    private HolderLookup.Provider loadedWith;
    private static HolderLookup.Provider menuLookup;

    public ToolbarStorage(Path file, DataFixer fixer) {
        this.file = file;
        this.fixer = fixer;
        for (int i = 0; i < 9; i++) bars[i] = NonNullList.withSize(SIZE, ItemStack.EMPTY);
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
    private static HolderLookup.Provider lookup() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) return client.level.registryAccess();
        if (menuLookup == null) menuLookup = VanillaRegistries.createLookup();
        return menuLookup;
    }

    private void load() {
        HolderLookup.Provider lookup = lookup();
        loadedWith = lookup;
        for (int i = 0; i < 9; i++) {
            Collections.fill(bars[i], ItemStack.EMPTY);
            Arrays.fill(undecoded[i], null);
        }
        try {
            if (!Files.exists(file)) return;
            CompoundTag nbt = NbtIo.read(file);
            if (nbt == null) return;
            nbt = DataFixTypes.HOTBAR.updateToCurrentVersion(fixer, nbt, NbtUtils.getDataVersion(nbt, 1343));
            for (int i = 0; i < 9; i++) {
                ListTag list = nbt.getListOrEmpty(String.valueOf(i));
                for (int j = 0; j < Math.min(list.size(), SIZE); j++) {
                    CompoundTag c = list.getCompoundOrEmpty(j);
                    ItemStack s = ItemStack.OPTIONAL_CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE), c).result().orElse(ItemStack.EMPTY);
                    bars[i].set(j, s);
                    if (s.isEmpty() && c.contains("id") && !"minecraft:air".equals(c.getStringOr("id", ""))) undecoded[i][j] = c;
                }
            }
        } catch (Exception e) {
            BetterHotbarSwap.LOGGER.error("Failed to load toolbars", e);
        }
    }

    // Çağıran her zaman önce get() ile aynı bağlamda yüklemiştir.
    public void save() {
        try {
            HolderLookup.Provider lookup = lookup();
            CompoundTag root = NbtUtils.addCurrentDataVersion(new CompoundTag());
            for (int i = 0; i < 9; i++) {
                ListTag list = new ListTag();
                for (int j = 0; j < SIZE; j++) {
                    ItemStack s = bars[i].get(j);
                    list.add(s.isEmpty() && undecoded[i][j] != null ? undecoded[i][j] : ItemStack.OPTIONAL_CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), s).getOrThrow());
                }
                root.put(String.valueOf(i), list);
            }
            NbtIo.write(root, file);
        } catch (Exception e) {
            BetterHotbarSwap.LOGGER.error("Failed to save toolbars", e);
        }
    }
}
