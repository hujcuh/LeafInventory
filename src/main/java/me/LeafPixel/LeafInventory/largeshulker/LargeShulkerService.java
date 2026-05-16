package me.LeafPixel.LeafInventory.largeshulker;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.LeafPixel.LeafInventory.util.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for 54-slot large shulker boxes.
 *
 * Data is bound to shulkerId, not to a player.
 */
public final class LargeShulkerService {

    public static final int DATA_VERSION = 1;

    private final JavaPlugin plugin;
    private final LargeShulkerKeys keys;
    private final LargeShulkerStore store;
    private final LargeShulkerPlacedStore placedStore;

    private final ConcurrentMap<UUID, LargeShulkerSession> sessionsByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> activeByShulker = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> placedIndex = new ConcurrentHashMap<>();

    private boolean enabled;
    private int rows;
    private String createPermission;
    private String openPermission;
    private boolean allowOpenWithoutPermission;
    private boolean requireOwnerToOpen;
    private int saveIntervalSeconds;
    private String title;

    private ScheduledTask autoSaveTask;

    public LargeShulkerService(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.keys = new LargeShulkerKeys(plugin);
        this.store = new LargeShulkerStore(plugin);
        this.placedStore = new LargeShulkerPlacedStore(plugin);
        reload(config);
    }

    public void load() {
        store.load();
        placedStore.load();

        placedIndex.clear();
        placedIndex.putAll(placedStore.snapshot());
    }

    public void reload(FileConfiguration config) {
        this.enabled = config.getBoolean("largeShulker.enabled", false);
        this.rows = Math.max(1, Math.min(6, config.getInt("largeShulker.rows", 6)));

        this.createPermission = config.getString(
                "largeShulker.permissions.create",
                "leafinventory.shulkerbox.large.create"
        );

        this.openPermission = config.getString(
                "largeShulker.permissions.open",
                "leafinventory.shulkerbox.large.open"
        );

        this.allowOpenWithoutPermission = config.getBoolean("largeShulker.allowOpenWithoutPermission", true);
        this.requireOwnerToOpen = config.getBoolean("largeShulker.requireOwnerToOpen", false);
        this.saveIntervalSeconds = Math.max(5, config.getInt("largeShulker.saveIntervalSeconds", 30));
        this.title = config.getString("largeShulker.title", "Large Shulker Box");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean canCreate(Player player) {
        return enabled
                && (createPermission == null
                || createPermission.isBlank()
                || player.hasPermission(createPermission));
    }

    public boolean canOpenExisting(Player player, ItemStack stack) {
        if (!enabled) {
            return false;
        }

        UUID shulkerId = getShulkerId(stack);

        if (shulkerId == null) {
            return false;
        }

        LargeShulkerData data = store.get(shulkerId);

        if (data == null) {
            return false;
        }

        if (requireOwnerToOpen && data.owner() != null && !data.owner().equals(player.getUniqueId())) {
            return false;
        }

        if (allowOpenWithoutPermission) {
            return true;
        }

        return openPermission == null
                || openPermission.isBlank()
                || player.hasPermission(openPermission);
    }

    public boolean isSessionOpen(UUID playerId) {
        return sessionsByPlayer.containsKey(playerId);
    }

    public LargeShulkerSession getSession(UUID playerId) {
        return sessionsByPlayer.get(playerId);
    }

    public boolean isLargeShulker(ItemStack stack) {
        return getShulkerId(stack) != null;
    }

    @SuppressWarnings("null")
    public UUID getShulkerId(ItemStack stack) {
        if (stack == null || !isShulkerMaterial(stack.getType())) {
            return null;
        }

        ItemMeta meta = stack.getItemMeta();

        if (meta == null) {
            return null;
        }

        String raw = meta.getPersistentDataContainer().get(
                keys.idKey(),
                PersistentDataType.STRING
        );

        return parseUuid(raw);
    }

    @SuppressWarnings("null")
    public UUID getOwner(ItemStack stack) {
        if (stack == null || !isShulkerMaterial(stack.getType())) {
            return null;
        }

        ItemMeta meta = stack.getItemMeta();

        if (meta == null) {
            return null;
        }

        String raw = meta.getPersistentDataContainer().get(
                keys.ownerKey(),
                PersistentDataType.STRING
        );

        return parseUuid(raw);
    }

    @SuppressWarnings("null")
    public UUID getShulkerId(Block block) {
        if (block == null || !isShulkerMaterial(block.getType())) {
            return null;
        }

        if (!(block.getState() instanceof TileState tile)) {
            return null;
        }

        return getShulkerId(tile);
    }

    @SuppressWarnings("null")
    public UUID getShulkerId(TileState tile) {
        if (tile == null) {
            return null;
        }

        String raw = tile.getPersistentDataContainer().get(
                keys.idKey(),
                PersistentDataType.STRING
        );

        return parseUuid(raw);
    }

    public boolean isLargeShulkerBlock(Block block) {
        return getShulkerId(block) != null;
    }

    public void createAndOpenFromPlayerInventory(Player player, int slot) {
        if (!canCreate(player)) {
            player.sendMessage("§c你没有创建大容量潜影盒的权限。");
            return;
        }

        ItemStack original = player.getInventory().getItem(slot);

        if (!isSingleShulker(original)) {
            return;
        }

        ItemStack converted = ensureLargeShulker(original, player.getUniqueId());

        player.getInventory().setItem(slot, converted);

        openFromStack(
                player,
                converted,
                LargeShulkerSession.SourceBinding.playerSlot(slot)
        );
    }

    public void createAndOpenFromMainHand(Player player) {
        if (!canCreate(player)) {
            player.sendMessage("§c你没有创建大容量潜影盒的权限。");
            return;
        }

        ItemStack original = player.getInventory().getItemInMainHand();

        if (!isSingleShulker(original)) {
            return;
        }

        ItemStack converted = ensureLargeShulker(original, player.getUniqueId());

        player.getInventory().setItemInMainHand(converted);

        openFromStack(
                player,
                converted,
                LargeShulkerSession.SourceBinding.mainHand()
        );
    }

    public void openExistingFromPlayerInventory(Player player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);

        if (!isSingleShulker(stack)) {
            return;
        }

        if (!canOpenExisting(player, stack)) {
            player.sendMessage("§c你不能打开这个大容量潜影盒。");
            return;
        }

        openFromStack(
                player,
                stack,
                LargeShulkerSession.SourceBinding.playerSlot(slot)
        );
    }

    public void openExistingFromMainHand(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();

        if (!isSingleShulker(stack)) {
            return;
        }

        if (!canOpenExisting(player, stack)) {
            player.sendMessage("§c你不能打开这个大容量潜影盒。");
            return;
        }

        openFromStack(
                player,
                stack,
                LargeShulkerSession.SourceBinding.mainHand()
        );
    }

    public void openExistingFromBlock(Player player, UUID shulkerId, Location location) {
        if (player == null || shulkerId == null) {
            return;
        }

        if (!enabled) {
            return;
        }

        if (isSessionOpen(player.getUniqueId())) {
            player.sendMessage("§c你已经打开了一个大容量潜影盒。");
            return;
        }

        LargeShulkerData data = store.get(shulkerId);

        if (data == null) {
            player.sendMessage("§c这个大容量潜影盒的数据不存在。");
            return;
        }

        if (requireOwnerToOpen && data.owner() != null && !data.owner().equals(player.getUniqueId())) {
            player.sendMessage("§c你不是这个大容量潜影盒的拥有者。");
            return;
        }

        if (!allowOpenWithoutPermission
                && openPermission != null
                && !openPermission.isBlank()
                && !player.hasPermission(openPermission)) {
            player.sendMessage("§c你没有打开大容量潜影盒的权限。");
            return;
        }

        UUID existing = activeByShulker.putIfAbsent(shulkerId, player.getUniqueId());

        if (existing != null) {
            player.sendMessage("§c这个大容量潜影盒正在被其他玩家使用。");
            return;
        }

        data.touch();

        LargeShulkerHolder holder = new LargeShulkerHolder(shulkerId);

        Inventory inventory = Bukkit.createInventory(
                holder,
                data.size(),
                Component.text(title)
        );

        holder.setInventory(inventory);
        inventory.setContents(data.items());

        String locationKey = location == null ? null : locationKey(location);

        LargeShulkerSession session = new LargeShulkerSession(
                player.getUniqueId(),
                shulkerId,
                inventory,
                LargeShulkerSession.SourceBinding.block(locationKey)
        );

        sessionsByPlayer.put(player.getUniqueId(), session);

        player.openInventory(inventory);
        playShulkerOpen(player);
    }

    public void handleClose(Player player, Inventory inventory) {
        if (!(inventory.getHolder() instanceof LargeShulkerHolder holder)) {
            return;
        }

        LargeShulkerSession session = sessionsByPlayer.get(player.getUniqueId());

        if (session == null) {
            return;
        }

        if (!holder.shulkerId().equals(session.shulkerId())) {
            return;
        }

        saveSession(session);
        closeSession(session.playerId(), session.shulkerId());
        playShulkerClose(player);
    }

    public void handleQuitOrKick(Player player) {
        LargeShulkerSession session = sessionsByPlayer.get(player.getUniqueId());

        if (session == null) {
            return;
        }

        saveSession(session);
        closeSession(session.playerId(), session.shulkerId());
    }

    public void startAutoSave() {
        stopAutoSave();

        if (!enabled) {
            return;
        }

        long ticks = saveIntervalSeconds * 20L;

        autoSaveTask = Scheduler.runGlobalTimer(
                plugin,
                ticks,
                ticks,
                this::flushAsync
        );
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
    }

    public void shutdown() {
        stopAutoSave();

        for (LargeShulkerSession session : sessionsByPlayer.values()) {
            saveSession(session);
        }

        sessionsByPlayer.clear();
        activeByShulker.clear();

        flushNow();
        placedStore.saveNow();
    }

    public void flushAsync() {
        Map<UUID, LargeShulkerData> snapshot = store.snapshot();

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            store.writeSnapshot(snapshot);
            placedStore.saveNow();
        });
    }

    public void flushNow() {
        store.saveNow();
        placedStore.saveNow();
    }

    private void openFromStack(Player player, ItemStack stack, LargeShulkerSession.SourceBinding binding) {
        if (isSessionOpen(player.getUniqueId())) {
            player.sendMessage("§c你已经打开了一个大容量潜影盒。");
            return;
        }

        UUID shulkerId = getShulkerId(stack);

        if (shulkerId == null) {
            return;
        }

        LargeShulkerData data = store.get(shulkerId);

        if (data == null) {
            player.sendMessage("§c这个大容量潜影盒的数据不存在。");
            return;
        }

        UUID existing = activeByShulker.putIfAbsent(shulkerId, player.getUniqueId());

        if (existing != null) {
            player.sendMessage("§c这个大容量潜影盒正在被其他玩家使用。");
            return;
        }

        data.touch();

        LargeShulkerHolder holder = new LargeShulkerHolder(shulkerId);

        Inventory inventory = Bukkit.createInventory(
                holder,
                data.size(),
                Component.text(title)
        );

        holder.setInventory(inventory);
        inventory.setContents(data.items());

        LargeShulkerSession session = new LargeShulkerSession(
                player.getUniqueId(),
                shulkerId,
                inventory,
                binding
        );

        sessionsByPlayer.put(player.getUniqueId(), session);

        player.openInventory(inventory);
        playShulkerOpen(player);
    }

    private void saveSession(LargeShulkerSession session) {
        LargeShulkerData data = store.get(session.shulkerId());

        if (data == null) {
            return;
        }

        data.setItems(session.inventory().getContents());
        data.touch();

        store.put(data);
    }

    private void closeSession(UUID playerId, UUID shulkerId) {
        sessionsByPlayer.remove(playerId);
        activeByShulker.remove(shulkerId, playerId);
    }

    @SuppressWarnings("null")
    private ItemStack ensureLargeShulker(ItemStack original, UUID owner) {
        ItemStack stack = original.clone();
        ItemMeta meta = stack.getItemMeta();

        if (!(meta instanceof BlockStateMeta bsm)) {
            return stack;
        }

        if (!(bsm.getBlockState() instanceof ShulkerBox box)) {
            return stack;
        }

        PersistentDataContainer pdc = bsm.getPersistentDataContainer();

        String existingRawId = pdc.get(keys.idKey(), PersistentDataType.STRING);
        UUID shulkerId = parseUuid(existingRawId);

        boolean newlyCreated = false;

        if (shulkerId == null) {
            shulkerId = UUID.randomUUID();
            newlyCreated = true;
        }

        LargeShulkerData data = store.getOrCreate(shulkerId, owner, rows);

        /*
         * If this is a newly converted vanilla shulker box, migrate its vanilla
         * 27-slot contents into the first 27 slots of the large shulker data.
         */
        if (newlyCreated) {
            ItemStack[] largeItems = new ItemStack[data.size()];
            ItemStack[] vanillaItems = box.getInventory().getContents();

            for (int i = 0; i < vanillaItems.length && i < largeItems.length; i++) {
                largeItems[i] = cloneOrNull(vanillaItems[i]);
            }

            data.setItems(largeItems);
            store.put(data);
        }

        pdc.set(keys.idKey(), PersistentDataType.STRING, shulkerId.toString());

        if (owner != null) {
            pdc.set(keys.ownerKey(), PersistentDataType.STRING, owner.toString());
        }

        pdc.set(keys.rowsKey(), PersistentDataType.INTEGER, data.rows());
        pdc.set(keys.versionKey(), PersistentDataType.INTEGER, DATA_VERSION);
        pdc.set(keys.createdAtKey(), PersistentDataType.LONG, data.createdAt());

        /*
         * Keep vanilla shulker contents empty.
         */
        box.getInventory().clear();
        bsm.setBlockState(box);

        stack.setItemMeta(bsm);

        return stack;
    }

    public boolean isCarrierSlotClick(Player player, LargeShulkerSession session, int rawSlot, int slot) {
        if (session == null || session.sourceBinding() == null) {
            return false;
        }

        if (session.sourceBinding().type() == LargeShulkerSession.SourceBinding.Type.BLOCK) {
            return false;
        }

        if (session.sourceBinding().type() == LargeShulkerSession.SourceBinding.Type.MAIN_HAND) {
            int heldSlot = player.getInventory().getHeldItemSlot();
            return rawSlot >= session.inventory().getSize() && slot == heldSlot;
        }

        if (session.sourceBinding().type() == LargeShulkerSession.SourceBinding.Type.PLAYER_SLOT) {
            return rawSlot >= session.inventory().getSize()
                    && slot == session.sourceBinding().slot();
        }

        return false;
    }

    @SuppressWarnings("null")
    public void copyItemPdcToBlock(ItemStack item, TileState tile) {
        if (item == null || tile == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return;
        }

        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
        PersistentDataContainer blockPdc = tile.getPersistentDataContainer();

        String id = itemPdc.get(keys.idKey(), PersistentDataType.STRING);
        String owner = itemPdc.get(keys.ownerKey(), PersistentDataType.STRING);
        Integer itemRows = itemPdc.get(keys.rowsKey(), PersistentDataType.INTEGER);
        Integer version = itemPdc.get(keys.versionKey(), PersistentDataType.INTEGER);
        Long createdAt = itemPdc.get(keys.createdAtKey(), PersistentDataType.LONG);

        if (id != null) {
            blockPdc.set(keys.idKey(), PersistentDataType.STRING, id);
        }

        if (owner != null) {
            blockPdc.set(keys.ownerKey(), PersistentDataType.STRING, owner);
        }

        if (itemRows != null) {
            blockPdc.set(keys.rowsKey(), PersistentDataType.INTEGER, itemRows);
        }

        if (version != null) {
            blockPdc.set(keys.versionKey(), PersistentDataType.INTEGER, version);
        }

        if (createdAt != null) {
            blockPdc.set(keys.createdAtKey(), PersistentDataType.LONG, createdAt);
        }
    }

    @SuppressWarnings("null")
    public ItemStack createItemFromId(UUID shulkerId, Material material) {
        Material type = isShulkerMaterial(material) ? material : Material.SHULKER_BOX;

        ItemStack item = new ItemStack(type, 1);
        LargeShulkerData data = store.get(shulkerId);

        UUID owner = data != null ? data.owner() : null;
        int itemRows = data != null ? data.rows() : rows;
        long createdAt = data != null ? data.createdAt() : System.currentTimeMillis();

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        pdc.set(keys.idKey(), PersistentDataType.STRING, shulkerId.toString());

        if (owner != null) {
            pdc.set(keys.ownerKey(), PersistentDataType.STRING, owner.toString());
        }

        pdc.set(keys.rowsKey(), PersistentDataType.INTEGER, itemRows);
        pdc.set(keys.versionKey(), PersistentDataType.INTEGER, DATA_VERSION);
        pdc.set(keys.createdAtKey(), PersistentDataType.LONG, createdAt);

        if (meta instanceof BlockStateMeta bsm
                && bsm.getBlockState() instanceof ShulkerBox box) {
            box.getInventory().clear();
            bsm.setBlockState(box);
        }

        item.setItemMeta(meta);

        return item;
    }

    public void recordPlaced(Location location, UUID shulkerId) {
        if (location == null || shulkerId == null) {
            return;
        }

        String key = locationKey(location);

        placedIndex.put(key, shulkerId);
        placedStore.put(key, shulkerId);
    }

    public void removePlaced(Location location) {
        if (location == null) {
            return;
        }

        String key = locationKey(location);

        placedIndex.remove(key);
        placedStore.remove(key);
    }

    public UUID getPlaced(Location location) {
        if (location == null) {
            return null;
        }

        String key = locationKey(location);

        UUID found = placedIndex.get(key);

        if (found != null) {
            return found;
        }

        found = placedStore.get(key);

        if (found != null) {
            placedIndex.put(key, found);
        }

        return found;
    }

    public boolean isValidPlacedLocation(Location location, UUID shulkerId) {
        if (location == null || shulkerId == null) {
            return false;
        }

        UUID indexed = getPlaced(location);

        return shulkerId.equals(indexed);
    }

    public String findPlacedLocationByShulkerId(UUID shulkerId) {
        return placedStore.findLocationByShulkerId(shulkerId);
    }

    @SuppressWarnings("null")
    public void convertBlockToVanilla(Block block) {
        if (block == null || !isShulkerMaterial(block.getType())) {
            return;
        }

        if (!(block.getState() instanceof TileState tile)) {
            return;
        }

        tile.getPersistentDataContainer().remove(keys.idKey());
        tile.getPersistentDataContainer().remove(keys.ownerKey());
        tile.getPersistentDataContainer().remove(keys.rowsKey());
        tile.getPersistentDataContainer().remove(keys.versionKey());
        tile.getPersistentDataContainer().remove(keys.createdAtKey());

        if (tile instanceof ShulkerBox box) {
            box.getInventory().clear();
        }

        tile.update(true, false);

        removePlaced(block.getLocation());
    }

    public static String locationKey(Location location) {
        String world = location.getWorld() == null
                ? "unknown"
                : location.getWorld().getUID().toString();

        return world
                + ":"
                + location.getBlockX()
                + ":"
                + location.getBlockY()
                + ":"
                + location.getBlockZ();
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isSingleShulker(ItemStack stack) {
        return stack != null && stack.getAmount() == 1 && isShulkerMaterial(stack.getType());
    }

    public static boolean isShulkerMaterial(Material material) {
        return switch (material) {
            case SHULKER_BOX,
                 WHITE_SHULKER_BOX,
                 ORANGE_SHULKER_BOX,
                 MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX,
                 LIME_SHULKER_BOX,
                 PINK_SHULKER_BOX,
                 GRAY_SHULKER_BOX,
                 LIGHT_GRAY_SHULKER_BOX,
                 CYAN_SHULKER_BOX,
                 PURPLE_SHULKER_BOX,
                 BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX,
                 GREEN_SHULKER_BOX,
                 RED_SHULKER_BOX,
                 BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    @SuppressWarnings("null")
    private static void playShulkerOpen(Player player) {
        player.playSound(
                player,
                Sound.BLOCK_SHULKER_BOX_OPEN,
                SoundCategory.BLOCKS,
                1.0f,
                1.2f
        );
    }

    @SuppressWarnings("null")
    private static void playShulkerClose(Player player) {
        player.playSound(
                player,
                Sound.BLOCK_SHULKER_BOX_CLOSE,
                SoundCategory.BLOCKS,
                1.0f,
                1.2f
        );
    }
}
