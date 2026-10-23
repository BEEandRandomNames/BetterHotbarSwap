package com.betterhotbarswap.client.toolbar;

import com.betterhotbarswap.BetterHotbarSwap;
import com.mojang.datafixers.DataFixer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 9 kayıt yuvası, her biri TAM envanter düzeni: index = PlayerInventory index (0-8 hotbar,
// 9-35 envanter), 36 = sol el (inv 40).
// ORTAK KAYIT (kullanıcı 2026-10-23): tek dosya ToolbarFiles.FILE; bütün sürümler ve
// başlatıcı örnekleri aynı 9 araç çubuğunu görür. Her slot kendi anlık görüntüsüdür:
// {DataVersion, Item (o sürümün ham NBT'si), Count, Name, Mc, Glint, Icon (dokunun PNG'si)}.
// Okurken slot DataFixer ile bu sürüme yükseltilip çözülür. Daha yeni sürümün ya da bu
// oyunda olmayan modun eşyası çözülemez -> YABANCI: yer tutucu (bariyer + CUSTOM_DATA) olarak
// durur, resmi ve adıyla çizilir (ToolbarIcons), yüklenmez, kayıtta AYNEN geri yazılır.
public final class ToolbarStorage {
    public static final int SIZE = 37;
    public static final int OFFHAND = 36;

    // Yabancı slotun kaydı ve çizim bilgisi.
    public record Foreign(NbtCompound record, String name, String mc, boolean glint, byte[] icon, int count, float[] uv,
                          float glintStrength, float glintSpeed) {
    }

    private static final String KEY = "betterhotbarswap_foreign";
    private static final Map<Integer, Foreign> FOREIGN = new HashMap<>();
    private static int nextKey;
    private static final int CURRENT_DV = NbtHelper.getDataVersion(NbtHelper.putDataVersion(new NbtCompound()), 0);
    private static final String MC = FabricLoader.getInstance().getModContainer("minecraft")
        .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");

    private final Path legacy; // eski örnek-içi config dosyası: bir kez içe alınır, silinmez
    private final DataFixer fixer;
    @SuppressWarnings("unchecked")
    private final DefaultedList<ItemStack>[] bars = new DefaultedList[9];
    private NbtList imported = new NbtList(); // içe alınmış eski dosyalar (bir daha alınmaz)
    // Yüklenen gerçek eşyalar ve özgün kayıtları: kaydederken eşit eşya (taşınmış olsa da) özgün
    // kaydını korur; slotun kaydı yalnız envanterden yeniden kaydedilince değişir.
    private final ItemStack[][] origStack = new ItemStack[9][SIZE];
    private final NbtCompound[][] origRec = new NbtCompound[9][SIZE];
    private RegistryWrapper.WrapperLookup loadedWith;
    private static RegistryWrapper.WrapperLookup menuLookup;
    private long loadedTime;
    private long checkedAt;

    public ToolbarStorage(Path file, DataFixer fixer) {
        this.legacy = file;
        this.fixer = fixer;
        for (int i = 0; i < 9; i++) bars[i] = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    }

    public List<ItemStack> get(int index) {
        if (loadedWith != lookup() || stale()) load();
        return bars[index];
    }

    public boolean isEmpty(int index) {
        for (ItemStack s : get(index)) if (!s.isEmpty()) return false;
        return true;
    }

    // Yer tutucunun kaydı; gerçek eşya ya da boş slotsa null.
    public static Foreign foreign(ItemStack s) {
        if (s.isEmpty() || s.getItem() != Items.BARRIER) return null;
        NbtComponent d = s.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound t = d == null ? null : d.copyNbt();
        return t == null || !t.contains(KEY) ? null : FOREIGN.get(t.getInt(KEY));
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
        checkedAt = System.currentTimeMillis();
        for (int i = 0; i < 9; i++) Collections.fill(bars[i], ItemStack.EMPTY);
        try {
            NbtCompound root = Files.exists(ToolbarFiles.FILE) ? NbtIo.read(ToolbarFiles.FILE) : null;
            if (root == null) root = new NbtCompound();
            loadedTime = ToolbarFiles.mtime(ToolbarFiles.FILE);
            imported = root.getList("Imported", NbtElement.STRING_TYPE);
            boolean changed = Files.exists(legacy) && importOld(root, legacy);
            for (Path p : ToolbarFiles.testFiles()) changed |= importOld(root, p);
            for (int i = 0; i < 9; i++) {
                NbtList row = root.getList(String.valueOf(i), NbtElement.COMPOUND_TYPE);
                for (int j = 0; j < SIZE; j++) {
                    NbtCompound rec = j < row.size() ? row.getCompound(j) : new NbtCompound();
                    ItemStack s = decode(rec, lookup);
                    bars[i].set(j, s);
                    boolean real = !s.isEmpty() && foreign(s) == null;
                    origStack[i][j] = real ? s.copy() : null;
                    origRec[i][j] = real ? rec : null;
                    // Görünümü eksik kaydı (resim/atlas yeri yok) aslına uygun okuyan sürüm tamamlar.
                    if (real && rec.getInt("DataVersion") <= CURRENT_DV && !complete(rec) && rec.getInt("NoIconDv") != CURRENT_DV) changed = true;
                }
            }
            if (changed) save();
        } catch (Exception e) {
            BetterHotbarSwap.LOGGER.error("Failed to load toolbars", e);
        }
    }

    // Slot kaydı -> bu sürümün eşyası; çözülemezse yabancı yer tutucu.
    private ItemStack decode(NbtCompound rec, RegistryWrapper.WrapperLookup lookup) {
        if (!rec.contains("Item")) return ItemStack.EMPTY;
        NbtCompound raw = rec.getCompound("Item");
        int dv = rec.contains("DataVersion") ? rec.getInt("DataVersion") : 1343;
        // Eski kayıtların sayısı ham eşyada: 1.20.5+ "count", öncesi "Count".
        int count = rec.contains("Count") ? rec.getInt("Count") : Math.max(1, raw.contains("count") ? raw.getInt("count") : raw.getInt("Count"));
        if (dv > CURRENT_DV) {
            // Daha yeni sürümün kaydı: aynı biçimdeyse olduğu gibi okunur; değilse eşya bu sürümde
            // de varsa yalın hali (bileşensiz) gösterilir ve yüklenebilir. Özgün kayıt korunur.
            try {
                ItemStack s = ItemStack.fromNbt(lookup, raw).orElse(ItemStack.EMPTY);
                if (!s.isEmpty()) return s;
            } catch (Exception ignored) {
            }
            Identifier key = Identifier.tryParse(raw.getString("id"));
            Item item = key == null ? Items.AIR : Registries.ITEM.getOrEmpty(key).orElse(Items.AIR);
            if (item != Items.AIR) return new ItemStack(item, Math.max(1, Math.min(count, item.getMaxCount())));
        } else {
            try {
                // Tek eşyalık hotbar: vanilla'nın HOTBAR yükseltmesi eşya biçimini bu sürüme taşır.
                NbtList one = new NbtList();
                one.add(raw.copy());
                NbtCompound wrap = new NbtCompound();
                wrap.put("0", one);
                wrap = DataFixTypes.HOTBAR.update(fixer, wrap, dv);
                ItemStack s = ItemStack.fromNbtOrEmpty(lookup, wrap.getList("0", NbtElement.COMPOUND_TYPE).getCompound(0));
                if (!s.isEmpty()) return s;
            } catch (Exception ignored) {
            }
        }
        int key = nextKey++;
        FOREIGN.put(key, new Foreign(rec.copy(), rec.contains("Name") ? rec.getString("Name") : raw.getString("id"),
            rec.getString("Mc"), rec.getBoolean("Glint"), rec.contains("Icon") ? rec.getByteArray("Icon") : null, count,
            rec.contains("GSU") ? new float[]{rec.getFloat("GU0"), rec.getFloat("GV0"), rec.getFloat("GSU"), rec.getFloat("GSV")} : null,
            rec.contains("GStr") ? rec.getFloat("GStr") : Float.NaN, rec.contains("GSpd") ? rec.getFloat("GSpd") : Float.NaN));
        ItemStack s = new ItemStack(Items.BARRIER, Math.max(1, Math.min(count, 64)));
        NbtCompound tag = new NbtCompound();
        tag.putInt(KEY, key);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
        return s;
    }

    // İlk taşıma: bu örneğin eski config dosyası (vanilla hotbar biçimi + kök DataVersion) bir
    // kez içe alınır; dolu satırları ortak listedeki BOŞ satırlara konur (diğer örneklerin
    // araç çubukları ezilmez). Dosya yerinde kalır.
    private boolean importOld(NbtCompound root, Path p) {
        String id = p.toAbsolutePath().normalize().toString();
        for (int k = 0; k < imported.size(); k++) if (id.equals(imported.getString(k))) return false;
        imported.add(NbtString.of(id));
        try {
            NbtCompound old = NbtIo.read(p);
            if (old == null) return true;
            int dv = NbtHelper.getDataVersion(old, 1343);
            int free = 0;
            for (int i = 0; i < 9; i++) {
                NbtList items = old.getList(String.valueOf(i), NbtElement.COMPOUND_TYPE);
                NbtList row = new NbtList();
                boolean any = false;
                for (int j = 0; j < Math.min(items.size(), SIZE); j++) {
                    NbtCompound it = items.getCompound(j);
                    NbtCompound rec = new NbtCompound();
                    if (it.contains("id") && !"minecraft:air".equals(it.getString("id"))) {
                        rec.putInt("DataVersion", dv);
                        rec.put("Item", it);
                        any = true;
                    }
                    row.add(rec);
                }
                if (!any || hasRow(root, row)) continue;
                while (free < 9 && !rowEmpty(root.getList(String.valueOf(free), NbtElement.COMPOUND_TYPE))) free++;
                // shortcut: ortak liste doluysa kalan eski satırlar alınmaz (dosya yerinde kalır).
                if (free == 9) break;
                root.put(String.valueOf(free), row);
            }
        } catch (Exception e) {
            BetterHotbarSwap.LOGGER.error("Failed to import old toolbars from " + p, e);
        }
        return true;
    }

    // Aynı satır zaten var mı (aynı eski kayıt iki dosyada: örnek-içi + deneme kopyası).
    private static boolean hasRow(NbtCompound root, NbtList row) {
        for (int r = 0; r < 9; r++) if (root.getList(String.valueOf(r), NbtElement.COMPOUND_TYPE).equals(row)) return true;
        return false;
    }

    // Görünümü tam kayıt: atlas yeri var, parlamalıysa parlama ayarı da.
    private static boolean complete(NbtCompound rec) {
        return rec.contains("GSU") && (!rec.getBoolean("Glint") || rec.contains("GStr"));
    }

    private static boolean rowEmpty(NbtList row) {
        for (int j = 0; j < row.size(); j++) if (row.getCompound(j).contains("Item")) return false;
        return true;
    }

    // Çağıran her zaman önce get() ile aynı bağlamda yüklemiştir.
    public void save() {
        try {
            RegistryWrapper.WrapperLookup lookup = lookup();
            NbtCompound root = new NbtCompound();
            root.putInt("Format", 2);
            root.put("Imported", imported.copy());
            for (int i = 0; i < 9; i++) {
                NbtList row = new NbtList();
                boolean[] used = new boolean[SIZE];
                ItemStack[] nextStack = new ItemStack[SIZE];
                NbtCompound[] nextRec = new NbtCompound[SIZE];
                for (int j = 0; j < SIZE; j++) {
                    ItemStack s = bars[i].get(j);
                    NbtCompound rec = record(s, original(i, s, used), lookup);
                    row.add(rec);
                    if (!s.isEmpty() && foreign(s) == null) {
                        nextStack[j] = s.copy();
                        nextRec[j] = rec;
                    }
                }
                origStack[i] = nextStack;
                origRec[i] = nextRec;
                root.put(String.valueOf(i), row);
            }
            NbtIo.write(root, ToolbarFiles.FILE);
            loadedTime = ToolbarFiles.mtime(ToolbarFiles.FILE);
        } catch (Exception e) {
            BetterHotbarSwap.LOGGER.error("Failed to save toolbars", e);
        }
    }

    // Bu satırda yüklenmiş, değerce eşit (taşınmış olabilir) eşyanın özgün kaydı; yoksa null.
    private NbtCompound original(int row, ItemStack s, boolean[] used) {
        if (s.isEmpty() || foreign(s) != null) return null;
        for (int k = 0; k < SIZE; k++) {
            if (used[k] || origStack[row][k] == null || !ItemStack.areEqual(origStack[row][k], s)) continue;
            used[k] = true;
            return origRec[row][k];
        }
        return null;
    }

    // Eşya -> slot kaydı (anlık görüntü). Yabancı yer tutucunun ve dokunulmamış eşyanın özgün
    // kaydı korunur (o sürümün verisi); eksik görünüm alanları bu sürüm aslına uygun
    // okuyabildiyse (dv <= bu sürüm) tamamlanır. Yeni eşya bu sürümün kaydını alır.
    private static NbtCompound record(ItemStack s, NbtCompound orig, RegistryWrapper.WrapperLookup lookup) {
        Foreign f = foreign(s);
        if (f != null) return f.record();
        NbtCompound rec = new NbtCompound();
        if (s.isEmpty()) return rec;
        if (orig != null) {
            if (orig.getInt("DataVersion") > CURRENT_DV || complete(orig) || orig.getInt("NoIconDv") == CURRENT_DV) return orig;
            rec = orig.copy();
        } else {
            rec.putInt("DataVersion", CURRENT_DV);
            rec.put("Item", s.encodeAllowEmpty(lookup));
            rec.putString("Mc", MC);
        }
        rec.putInt("Count", s.getCount());
        rec.putString("Name", s.getName().getString());
        if (s.hasGlint()) {
            rec.putBoolean("Glint", true);
            // Kaydedildiği oyunun parlama ayarı: başka sürümde aynı güçte/hızda görünsün.
            rec.putFloat("GStr", ToolbarIcons.glintStrength());
            rec.putFloat("GSpd", ToolbarIcons.glintSpeed());
        }
        ToolbarIcons.Capture cap = ToolbarIcons.capture(s);
        if (cap != null) {
            rec.putByteArray("Icon", cap.png());
            // Atlastaki yer: vanilla parlama deseni buna bağlı (ToolbarIcons.animate).
            rec.putFloat("GU0", cap.uv()[0]);
            rec.putFloat("GV0", cap.uv()[1]);
            rec.putFloat("GSU", cap.uv()[2]);
            rec.putFloat("GSV", cap.uv()[3]);
        } else {
            rec.putInt("NoIconDv", CURRENT_DV); // bu sürüm denedi, alamadı
        }
        return rec;
    }

    // Başka bir sürüm/örnek kaydettiyse yeniden okunur (en fazla 2 sn'de bir bakılır).
    private boolean stale() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 2000) return false;
        checkedAt = now;
        return ToolbarFiles.mtime(ToolbarFiles.FILE) != loadedTime;
    }
}
