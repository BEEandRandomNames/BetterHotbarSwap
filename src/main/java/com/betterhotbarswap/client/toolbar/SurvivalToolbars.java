package com.betterhotbarswap.client.toolbar;

import com.betterhotbarswap.client.debug.BHSDebug;
import com.betterhotbarswap.client.input.VanillaClickExecutor;
import com.betterhotbarswap.config.BHSConfig;
import net.minecraft.client.MinecraftClient;
import com.betterhotbarswap.client.BetterHotbarSwapClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;

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
    public static void onStartTick(MinecraftClient client) {
        if (!BHSConfig.survivalToolbars) return;
        if (client.player == null || client.currentScreen != null) return;
        KeyBinding modSave = BetterHotbarSwapClient.getToolbarSaveKey();
        KeyBinding modLoad = BetterHotbarSwapClient.getToolbarLoadKey();
        boolean mod = modSave.isPressed() || modLoad.isPressed();
        boolean vanilla = !client.player.isCreative();
        boolean save = modSave.isPressed() || (vanilla && client.options.saveToolbarActivatorKey.isPressed());
        boolean load = modLoad.isPressed() || (vanilla && client.options.loadToolbarActivatorKey.isPressed());
        if (!save && !load) return;
        // Mesajdaki "geri yükle" tuşu, kaydedilen tuş ailesinden.
        KeyBinding loadKey = mod ? modLoad : client.options.loadToolbarActivatorKey;
        for (int i = 0; i < 9; i++) {
            boolean hit = false;
            while (client.options.hotbarKeys[i].wasPressed()) hit = true;
            if (!hit) continue;
            if (save) save(client, i, loadKey);
            else load(client, i);
        }
    }

    private static ToolbarStorage storage;

    public static ToolbarStorage storage(MinecraftClient client) {
        if (storage == null) {
            storage = new ToolbarStorage(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("betterhotbarswap_toolbars.nbt"), client.getDataFixer());
        }
        return storage;
    }

    // Şablon index -> PlayerInventory index (36 = sol el = inv 40).
    public static int invIndex(int t) {
        return t == ToolbarStorage.OFFHAND ? 40 : t;
    }

    public static boolean isEmpty(MinecraftClient client, int index) {
        return storage(client).isEmpty(index);
    }

    public static void save(MinecraftClient client, int index, KeyBinding loadKey) {
        if (client.player == null) return;
        List<ItemStack> tpl = storage(client).get(index);
        for (int t = 0; t < ToolbarStorage.SIZE; t++) tpl.set(t, client.player.getInventory().getStack(invIndex(t)).copy());
        storage(client).save();
        // Vanilla'nın kendi mesajı: "Araç çubuğu kaydedildi (X+N ile geri yükle)".
        client.inGameHud.setOverlayMessage(Text.translatable("inventory.hotbarSaved",
            loadKey.getBoundKeyLocalizedText(),
            client.options.hotbarKeys[index].getBoundKeyLocalizedText()), false);
        BHSDebug.log("TOOLBAR save " + (index + 1));
    }

    // Kayıt yuvasını boşaltır (sadece modun deposu; creative'inkiler etkilenmez).
    public static void delete(MinecraftClient client, int index) {
        List<ItemStack> tpl = storage(client).get(index);
        for (int t = 0; t < ToolbarStorage.SIZE; t++) tpl.set(t, ItemStack.EMPTY);
        storage(client).save();
        BHSDebug.log("TOOLBAR delete " + (index + 1));
    }

    // İlk boş kayıt yuvası (Ekle düğmesi); hepsi doluysa -1.
    public static int firstFree(MinecraftClient client) {
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

    public static void load(MinecraftClient client, int index) {
        if (client.player == null || client.interactionManager == null) return;
        List<ItemStack> tpl = storage(client).get(index);
        if (storage(client).isEmpty(index)) {
            message(client, Text.translatable("betterhotbarswap.toolbar.empty", index + 1));
            return;
        }
        PlayerInventory inv = client.player.getInventory();
        ScreenHandler h = client.player.currentScreenHandler;
        if (!h.getCursorStack().isEmpty()) return;
        boolean[] locked = new boolean[ToolbarStorage.SIZE];
        int wanted = 0;
        for (int t = 0; t < ToolbarStorage.SIZE; t++) {
            if (tpl.get(t).isEmpty()) continue;
            wanted++;
            // Zaten doğru olanlar kilitlenir (kaynak olarak çalınmaz).
            if (exact(inv.getStack(invIndex(t)), tpl.get(t))) locked[t] = true;
        }
        // pass 0: birebir (item+NBT), pass 1: aynı item.
        for (int pass = 0; pass < 2; pass++) {
            for (int t : ORDER) {
                ItemStack want = tpl.get(t);
                if (locked[t] || want.isEmpty()) continue;
                if (pass == 1 && sameItem(inv.getStack(invIndex(t)), want)) {
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
        message(client, Text.translatable("betterhotbarswap.toolbar.loaded", index + 1, placed, wanted));
        BHSDebug.log("TOOLBAR load " + (index + 1) + " " + placed + "/" + wanted);
    }

    // İki envanter slotunu takas eder (imleç boş başlar, boş biter). SWAP düğmesi
    // sadece hotbar (0-8) ya da sol el (40) olabilir; envanter<->envanter için
    // PICKUP üçlüsü (al, hedefe bırak/takas, kalanı kaynağa bırak).
    private static boolean swap(MinecraftClient client, ScreenHandler h, PlayerInventory inv, int a, int b) {
        int idA = VanillaClickExecutor.realSlotId(h, inv, a);
        int idB = VanillaClickExecutor.realSlotId(h, inv, b);
        if (idA == -1 || idB == -1) return false;
        if (a < 9 || a == 40) {
            client.interactionManager.clickSlot(h.syncId, idB, a, SlotActionType.SWAP, client.player);
        } else if (b < 9 || b == 40) {
            client.interactionManager.clickSlot(h.syncId, idA, b, SlotActionType.SWAP, client.player);
        } else {
            client.interactionManager.clickSlot(h.syncId, idB, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(h.syncId, idA, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(h.syncId, idB, 0, SlotActionType.PICKUP, client.player);
        }
        return true;
    }

    // Kaynak: kilitsiz herhangi bir şablon slotu; en büyük yığın tercih edilir.
    private static int findSource(PlayerInventory inv, ItemStack want, boolean exactOnly, boolean[] locked, int target) {
        int best = -1;
        int bestCount = 0;
        for (int t = 0; t < ToolbarStorage.SIZE; t++) {
            if (locked[t] || t == target) continue;
            ItemStack s = inv.getStack(invIndex(t));
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
        return sameItem(a, b) && java.util.Objects.equals(a.getNbt(), b.getNbt());
    }

    private static void message(MinecraftClient client, Text text) {
        client.inGameHud.setOverlayMessage(text, false);
    }
}
