package com.betterhotbarswap.client.toolbar;

import com.betterhotbarswap.BetterHotbarSwap;
import com.mojang.datafixers.DataFixer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 9 kayıt yuvası, her biri TAM envanter düzeni: index = Inventory index (0-8 hotbar,
// 9-35 envanter), 36 = sol el (inv 40).
// ORTAK KAYIT (kullanıcı 2026-10-23): tek dosya ToolbarFiles.FILE; bütün sürümler ve
// başlatıcı örnekleri aynı 9 araç çubuğunu görür. Her slot kendi anlık görüntüsüdür:
// {DataVersion, Item (o sürümün ham NBT'si), Count, Name, Mc, Glint, Icon (dokunun PNG'si)}.
// Okurken slot DataFixer ile bu sürüme yükseltilip çözülür. Daha yeni sürümün ya da bu
// oyunda olmayan modun eşyası çözülemez -> YABANCI: yer tutucu (bariyer + özel veri) olarak
// durur, resmi ve adıyla çizilir (ToolbarIcons), yüklenmez, kayıtta AYNEN geri yazılır.
public final class ToolbarStorage {
    public static final int SIZE = 37;
    public static final int OFFHAND = 36;

    // Yabancı slotun kaydı ve çizim bilgisi.
    public record Foreign(CompoundTag record, String name, String mc, boolean glint, byte[] icon, int count, float[] uv,
                          float glintStrength, float glintSpeed) {
    }

    private static final String KEY = "betterhotbarswap_foreign";
    private static final Map<Integer, Foreign> FOREIGN = new HashMap<>();
    private static int nextKey;
    private static final int CURRENT_DV = NbtUtils.getDataVersion(NbtUtils.addCurrentDataVersion(new CompoundTag()), 0);
    private static final String MC = FabricLoader.getInstance().getModContainer("minecraft")
        .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");

    private final Path legacy; // eski örnek-içi config dosyası: bir kez içe alınır, silinmez
    private final DataFixer fixer;
    @SuppressWarnings("unchecked")
    private final NonNullList<ItemStack>[] bars = new NonNullList[9];
    private ListTag imported = new ListTag(); // içe alınmış eski dosyalar (bir daha alınmaz)
    // Yüklenen gerçek eşyalar ve özgün kayıtları: kaydederken eşit eşya (taşınmış olsa da) özgün
    // kaydını korur; slotun kaydı yalnız envanterden yeniden kaydedilince değişir.
    private final ItemStack[][] origStack = new ItemStack[9][SIZE];
    private final CompoundTag[][] origRec = new CompoundTag[9][SIZE];
    private HolderLookup.Provider loadedWith;
    private static HolderLookup.Provider menuLookup;
    private long loadedTime;
    private long checkedAt;

    public ToolbarStorage(Path file, DataFixer fixer) {
        this.legacy = file;
        this.fixer = fixer;
        for (int i = 0; i < 9; i++) bars[i] = NonNullList.withSize(SIZE, ItemStack.EMPTY);
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
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        int key = d == null ? -1 : d.copyTag().getIntOr(KEY, -1);
        return key < 0 ? null : FOREIGN.get(key);
    }

    // 1.20.5+: eşya NBT'si kayıt (registry) bağlamıyla okunur/yazılır. Dünyadayken
    // dünyanınki, menüde vanilla'nın gömülü kayıtları; bağlam değişince yeniden okunur.
    private static HolderLookup.Provider lookup() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) return client.level.registryAccess();
        if (menuLookup == null) menuLookup = VanillaRegistries.createWorldLookup();
        return menuLookup;
    }

    private void load() {
        HolderLookup.Provider lookup = lookup();
        loadedWith = lookup;
        checkedAt = System.currentTimeMillis();
        for (int i = 0; i < 9; i++) Collections.fill(bars[i], ItemStack.EMPTY);
        try {
            CompoundTag root = Files.exists(ToolbarFiles.FILE) ? NbtIo.read(ToolbarFiles.FILE) : null;
            if (root == null) root = new CompoundTag();
            loadedTime = ToolbarFiles.mtime(ToolbarFiles.FILE);
            imported = root.getListOrEmpty("Imported");
            boolean changed = Files.exists(legacy) && importOld(root, legacy);
            for (Path p : ToolbarFiles.testFiles()) changed |= importOld(root, p);
            for (int i = 0; i < 9; i++) {
                ListTag row = root.getListOrEmpty(String.valueOf(i));
                for (int j = 0; j < SIZE; j++) {
                    CompoundTag rec = j < row.size() ? row.getCompoundOrEmpty(j) : new CompoundTag();
                    ItemStack s = decode(rec, lookup);
                    bars[i].set(j, s);
                    boolean real = !s.isEmpty() && foreign(s) == null;
                    origStack[i][j] = real ? s.copy() : null;
                    origRec[i][j] = real ? rec : null;
                    // Görünümü eksik kaydı (resim/atlas yeri yok) aslına uygun okuyan sürüm tamamlar.
                    if (real && rec.getIntOr("DataVersion", 0) <= CURRENT_DV && !complete(rec) && rec.getIntOr("NoIconDv", 0) != CURRENT_DV) changed = true;
                }
            }
            if (changed) save();
        } catch (Exception e) {
            BetterHotbarSwap.LOGGER.error("Failed to load toolbars", e);
        }
    }

    // Slot kaydı -> bu sürümün eşyası; çözülemezse yabancı yer tutucu.
    private ItemStack decode(CompoundTag rec, HolderLookup.Provider lookup) {
        if (!rec.contains("Item")) return ItemStack.EMPTY;
        CompoundTag raw = rec.getCompoundOrEmpty("Item");
        int dv = rec.getIntOr("DataVersion", 1343);
        // Eski kayıtların sayısı ham eşyada: 1.20.5+ "count", öncesi "Count".
        int count = rec.getIntOr("Count", raw.getIntOr("count", raw.getByteOr("Count", (byte) 1)));
        if (dv > CURRENT_DV) {
            // Daha yeni sürümün kaydı: aynı biçimdeyse olduğu gibi okunur; değilse eşya bu sürümde
            // de varsa yalın hali (bileşensiz) gösterilir ve yüklenebilir. Özgün kayıt korunur.
            try {
                ItemStack s = ItemStack.OPTIONAL_CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE), raw).result().orElse(ItemStack.EMPTY);
                if (!s.isEmpty()) return s;
            } catch (Exception ignored) {
            }
            Identifier key = Identifier.tryParse(raw.getStringOr("id", ""));
            Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
            if (item != Items.AIR) return new ItemStack(item, Math.max(1, Math.min(count, item.getDefaultMaxStackSize())));
        } else {
            try {
                // Tek eşyalık hotbar: vanilla'nın HOTBAR yükseltmesi eşya biçimini bu sürüme taşır.
                ListTag one = new ListTag();
                one.add(raw.copy());
                CompoundTag wrap = new CompoundTag();
                wrap.put("0", one);
                wrap = DataFixTypes.HOTBAR.updateToCurrentVersion(fixer, wrap, dv);
                ItemStack s = ItemStack.OPTIONAL_CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE),
                    wrap.getListOrEmpty("0").getCompoundOrEmpty(0)).result().orElse(ItemStack.EMPTY);
                if (!s.isEmpty()) return s;
            } catch (Exception ignored) {
            }
        }
        int key = nextKey++;
        FOREIGN.put(key, new Foreign(rec.copy(), rec.getStringOr("Name", raw.getStringOr("id", "?")),
            rec.getStringOr("Mc", ""), rec.getBooleanOr("Glint", false), rec.getByteArray("Icon").orElse(null), count,
            rec.contains("GSU") ? new float[]{rec.getFloatOr("GU0", 0f), rec.getFloatOr("GV0", 0f), rec.getFloatOr("GSU", 0f), rec.getFloatOr("GSV", 0f)} : null,
            rec.contains("GStr") ? rec.getFloatOr("GStr", 0f) : Float.NaN, rec.contains("GSpd") ? rec.getFloatOr("GSpd", 0f) : Float.NaN));
        ItemStack s = new ItemStack(Items.BARRIER, Math.max(1, Math.min(count, 64)));
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY, key);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    // İlk taşıma: bu örneğin eski config dosyası (vanilla hotbar biçimi + kök DataVersion) bir
    // kez içe alınır; dolu satırları ortak listedeki BOŞ satırlara konur (diğer örneklerin
    // araç çubukları ezilmez). Dosya yerinde kalır.
    private boolean importOld(CompoundTag root, Path p) {
        String id = p.toAbsolutePath().normalize().toString();
        for (int k = 0; k < imported.size(); k++) if (id.equals(imported.getStringOr(k, ""))) return false;
        imported.add(StringTag.valueOf(id));
        try {
            CompoundTag old = NbtIo.read(p);
            if (old == null) return true;
            int dv = NbtUtils.getDataVersion(old, 1343);
            int free = 0;
            for (int i = 0; i < 9; i++) {
                ListTag items = old.getListOrEmpty(String.valueOf(i));
                ListTag row = new ListTag();
                boolean any = false;
                for (int j = 0; j < Math.min(items.size(), SIZE); j++) {
                    CompoundTag it = items.getCompoundOrEmpty(j);
                    CompoundTag rec = new CompoundTag();
                    if (!"minecraft:air".equals(it.getStringOr("id", "minecraft:air"))) {
                        rec.putInt("DataVersion", dv);
                        rec.put("Item", it);
                        any = true;
                    }
                    row.add(rec);
                }
                if (!any || hasRow(root, row)) continue;
                while (free < 9 && !rowEmpty(root.getListOrEmpty(String.valueOf(free)))) free++;
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
    private static boolean hasRow(CompoundTag root, ListTag row) {
        for (int r = 0; r < 9; r++) if (root.getListOrEmpty(String.valueOf(r)).equals(row)) return true;
        return false;
    }

    // Görünümü tam kayıt: atlas yeri var, parlamalıysa parlama ayarı da.
    private static boolean complete(CompoundTag rec) {
        return rec.contains("GSU") && (!rec.getBooleanOr("Glint", false) || rec.contains("GStr"));
    }

    private static boolean rowEmpty(ListTag row) {
        for (int j = 0; j < row.size(); j++) if (row.getCompoundOrEmpty(j).contains("Item")) return false;
        return true;
    }

    // Çağıran her zaman önce get() ile aynı bağlamda yüklemiştir.
    public void save() {
        try {
            HolderLookup.Provider lookup = lookup();
            CompoundTag root = new CompoundTag();
            root.putInt("Format", 2);
            root.put("Imported", imported.copy());
            for (int i = 0; i < 9; i++) {
                ListTag row = new ListTag();
                boolean[] used = new boolean[SIZE];
                ItemStack[] nextStack = new ItemStack[SIZE];
                CompoundTag[] nextRec = new CompoundTag[SIZE];
                for (int j = 0; j < SIZE; j++) {
                    ItemStack s = bars[i].get(j);
                    CompoundTag rec = record(s, original(i, s, used), lookup);
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
    private CompoundTag original(int row, ItemStack s, boolean[] used) {
        if (s.isEmpty() || foreign(s) != null) return null;
        for (int k = 0; k < SIZE; k++) {
            if (used[k] || origStack[row][k] == null || !ItemStack.matches(origStack[row][k], s)) continue;
            used[k] = true;
            return origRec[row][k];
        }
        return null;
    }

    // Eşya -> slot kaydı (anlık görüntü). Yabancı yer tutucunun ve dokunulmamış eşyanın özgün
    // kaydı korunur (o sürümün verisi); eksik görünüm alanları bu sürüm aslına uygun
    // okuyabildiyse (dv <= bu sürüm) tamamlanır. Yeni eşya bu sürümün kaydını alır.
    private static CompoundTag record(ItemStack s, CompoundTag orig, HolderLookup.Provider lookup) {
        Foreign f = foreign(s);
        if (f != null) return f.record();
        CompoundTag rec = new CompoundTag();
        if (s.isEmpty()) return rec;
        if (orig != null) {
            if (orig.getIntOr("DataVersion", 0) > CURRENT_DV || complete(orig) || orig.getIntOr("NoIconDv", 0) == CURRENT_DV) return orig;
            rec = orig.copy();
        } else {
            rec.putInt("DataVersion", CURRENT_DV);
            rec.put("Item", ItemStack.OPTIONAL_CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), s).getOrThrow());
            rec.putString("Mc", MC);
        }
        rec.putInt("Count", s.getCount());
        rec.putString("Name", s.getHoverName().getString());
        if (s.hasFoil()) {
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
