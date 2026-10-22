package com.betterhotbarswap.client.toolbar;

import com.betterhotbarswap.client.debug.BHSDebug;
import com.betterhotbarswap.client.input.VanillaClickExecutor;
import com.betterhotbarswap.config.BHSConfig;
import com.betterhotbarswap.client.BetterHotbarSwapClient;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

// Kayıtlı araç çubukları (creative'in "Kaydedilmiş Araç Çubukları" fikri, her modda).
// Her kayıt TAM envanter düzenidir: hotbar + envanter 27 slot + sol el (ToolbarStorage).
// Depo: modun KENDİ dosyası config/betterhotbarswap_toolbars.nbt (creative'in
// hotbar.nbt'sinden AYRI).
// Tuşlar: modun kendi Kaydet/Yükle tuşları (varsayılan N/M) HER modda; vanilla'nın
// C/X'i sadece survival'da (creative'de vanilla'nın kendi işi, dokunulmaz).
//
// YÜKLEME (her modda, creative dahil): eşya ÜRETİLMEZ/KOPYALANMAZ; envanterde VAR
// olanlar şablondaki yerlerine TAKAS ile taşınır (permütasyon = kayıpsız). Sıra:
// hotbar, sol el, envanter. Önce birebir eşleşme (item+NBT), sonra aynı item.
// Bulunamayan slot olduğu gibi kalır.
public final class SurvivalToolbars {
    private SurvivalToolbars() {
    }

    // START_CLIENT_TICK: vanilla handleInputEvents'ten ÖNCE çalışır; kısayolun
    // wasPressed'ini tüketir -> vanilla slot seçmez / creative çubuğu vermez.
    // 26.2+: açık ekran ve ekran açma Minecraft.gui altında (gui.screen(), gui.setScreen).
    public static void onStartTick(Minecraft client) {
        if (!BHSConfig.survivalToolbars) return;
        if (client.player == null || client.gui.screen() != null) return;
        KeyMapping modSave = BetterHotbarSwapClient.getToolbarSaveKey();
        KeyMapping modLoad = BetterHotbarSwapClient.getToolbarLoadKey();
        boolean mod = modSave.isDown() || modLoad.isDown();
        boolean vanilla = !client.player.isCreative();
        boolean save = modSave.isDown() || (vanilla && client.options.keySaveHotbarActivator.isDown());
        boolean load = modLoad.isDown() || (vanilla && client.options.keyLoadHotbarActivator.isDown());
        if (!save && !load) return;
        // Mesajdaki "geri yükle" tuşu, kaydedilen tuş ailesinden.
        KeyMapping loadKey = mod ? modLoad : client.options.keyLoadHotbarActivator;
        for (int i = 0; i < 9; i++) {
            boolean hit = false;
            while (client.options.keyHotbarSlots[i].consumeClick()) hit = true;
            if (!hit) continue;
            if (save) save(client, i, loadKey);
            else load(client, i);
        }
    }

    private static ToolbarStorage storage;

    public static ToolbarStorage storage(Minecraft client) {
        if (storage == null) {
            storage = new ToolbarStorage(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("betterhotbarswap_toolbars.nbt"), client.getFixerUpper());
        }
        return storage;
    }

    // Şablon index -> PlayerInventory index (36 = sol el = inv 40).
    public static int invIndex(int t) {
        return t == ToolbarStorage.OFFHAND ? 40 : t;
    }

    public static boolean isEmpty(Minecraft client, int index) {
        return storage(client).isEmpty(index);
    }

    public static void save(Minecraft client, int index, KeyMapping loadKey) {
        if (client.player == null) return;
        List<ItemStack> tpl = storage(client).get(index);
        for (int t = 0; t < ToolbarStorage.SIZE; t++) tpl.set(t, client.player.getInventory().getItem(invIndex(t)).copy());
        storage(client).save();
        // Vanilla'nın kendi mesajı: "Araç çubuğu kaydedildi (X+N ile geri yükle)".
        client.player.sendOverlayMessage(Component.translatable("inventory.hotbarSaved",
            loadKey.getTranslatedKeyMessage(),
            client.options.keyHotbarSlots[index].getTranslatedKeyMessage()));
        BHSDebug.log("TOOLBAR save " + (index + 1));
    }

    // Kayıt yuvasını boşaltır (sadece modun deposu; creative'inkiler etkilenmez).
    public static void delete(Minecraft client, int index) {
        List<ItemStack> tpl = storage(client).get(index);
        for (int t = 0; t < ToolbarStorage.SIZE; t++) tpl.set(t, ItemStack.EMPTY);
        storage(client).save();
        BHSDebug.log("TOOLBAR delete " + (index + 1));
    }

    // İlk boş kayıt yuvası (Ekle düğmesi); hepsi doluysa -1.
    public static int firstFree(Minecraft client) {
        for (int i = 0; i < 9; i++) if (isEmpty(client, i)) return i;
        return -1;
    }

    // Yerleştirme sırası: hotbar (en önemli), sol el, envanter.
    private static final int[] ORDER = new int[ToolbarStorage.SIZE];

    static {
        int k = 0;
        for (int t = 0; t < 9; t++) ORDER[k++] = t;
        ORDER[k++] = ToolbarStorage.OFFHAND;
        for (int t = 9; t < 36; t++) ORDER[k++] = t;
    }

    public static void load(Minecraft client, int index) {
        if (client.player == null || client.gameMode == null) return;
        List<ItemStack> tpl = storage(client).get(index);
        if (storage(client).isEmpty(index)) {
            message(client, Component.translatable("betterhotbarswap.toolbar.empty", index + 1));
            return;
        }
        Inventory inv = client.player.getInventory();
        AbstractContainerMenu h = client.player.containerMenu;
        if (!h.getCarried().isEmpty()) return;
        boolean[] locked = new boolean[ToolbarStorage.SIZE];
        int wanted = 0;
        for (int t = 0; t < ToolbarStorage.SIZE; t++) {
            if (tpl.get(t).isEmpty()) continue;
            wanted++;
            // Zaten doğru olanlar kilitlenir (kaynak olarak çalınmaz).
            if (exact(inv.getItem(invIndex(t)), tpl.get(t))) locked[t] = true;
        }
        // pass 0: birebir (item+NBT), pass 1: aynı item.
        for (int pass = 0; pass < 2; pass++) {
            for (int t : ORDER) {
                ItemStack want = tpl.get(t);
                if (locked[t] || want.isEmpty()) continue;
                if (pass == 1 && sameItem(inv.getItem(invIndex(t)), want)) {
                    locked[t] = true;
                    continue;
                }
                int src = findSource(inv, want, pass == 0, locked, t);
                if (src == -1) continue;
                if (!swap(client, h, inv, invIndex(t), invIndex(src))) continue;
                locked[t] = true;
            }
        }
        int placed = 0;
        for (int t = 0; t < ToolbarStorage.SIZE; t++) if (locked[t] && !tpl.get(t).isEmpty()) placed++;
        message(client, Component.translatable("betterhotbarswap.toolbar.loaded", index + 1, placed, wanted));
        BHSDebug.log("TOOLBAR load " + (index + 1) + " " + placed + "/" + wanted);
    }

    // İki envanter slotunu takas eder (imleç boş başlar, boş biter). SWAP düğmesi
    // sadece hotbar (0-8) ya da sol el (40) olabilir; envanter<->envanter için
    // PICKUP üçlüsü (al, hedefe bırak/takas, kalanı kaynağa bırak).
    private static boolean swap(Minecraft client, AbstractContainerMenu h, Inventory inv, int a, int b) {
        int idA = VanillaClickExecutor.realSlotId(h, inv, a);
        int idB = VanillaClickExecutor.realSlotId(h, inv, b);
        if (idA == -1 || idB == -1) return false;
        if (a < 9 || a == 40) {
            client.gameMode.handleContainerInput(h.containerId, idB, a, ContainerInput.SWAP, client.player);
        } else if (b < 9 || b == 40) {
            client.gameMode.handleContainerInput(h.containerId, idA, b, ContainerInput.SWAP, client.player);
        } else {
            client.gameMode.handleContainerInput(h.containerId, idB, 0, ContainerInput.PICKUP, client.player);
            client.gameMode.handleContainerInput(h.containerId, idA, 0, ContainerInput.PICKUP, client.player);
            client.gameMode.handleContainerInput(h.containerId, idB, 0, ContainerInput.PICKUP, client.player);
        }
        return true;
    }

    // Kaynak: kilitsiz herhangi bir şablon slotu; en büyük yığın tercih edilir.
    private static int findSource(Inventory inv, ItemStack want, boolean exactOnly, boolean[] locked, int target) {
        int best = -1;
        int bestCount = 0;
        for (int t = 0; t < ToolbarStorage.SIZE; t++) {
            if (locked[t] || t == target) continue;
            ItemStack s = inv.getItem(invIndex(t));
            if (!(exactOnly ? exact(s, want) : sameItem(s, want))) continue;
            if (s.getCount() > bestCount) {
                bestCount = s.getCount();
                best = t;
            }
        }
        return best;
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && a.getItem() == b.getItem();
    }

    private static boolean exact(ItemStack a, ItemStack b) {
        return sameItem(a, b) && ItemStack.isSameItemSameComponents(a, b);
    }

    private static void message(Minecraft client, Component text) {
        client.player.sendOverlayMessage(text);
    }
}
