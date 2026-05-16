package me.LeafPixel.LeafInventory.largeshulker;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.ShulkerBox;
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
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.TileState;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.Locale;

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
        reload(config);
    }

    public void load() {
        store.load();
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
        return enabled && (createPermission == null || createPermission.isBlank() || player.hasPermission(createPermission));
    }

    public boolean canOpenExisting(Player player, ItemStack stack) {
        if (!enabled) {
            return false;
        }

        UUID shulkerId = getShulkerId(stack);
        if (shulkerId == null) {
            return false;
        }

        if (allowOpenWithoutPermission) {
            if (!requireOwnerToOpen) {
                return true;
            }

            UUID owner = getOwner(stack);
            return owner == null || owner.equals(player.getUniqueId());
        }

        return openPermission == null || openPermission.isBlank() || player.hasPermission(openPermission);
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

    public UUID getShulkerId(ItemStack stack) {
        if (stack == null || !isShulkerMaterial(stack.getType())) {
            return null;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }

        String raw = meta.getPersistentDataContainer().get(keys.idKey(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public UUID getOwner(ItemStack stack) {
        if (stack == null || !isShulkerMaterial(stack.getType())) {
            return null;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }

        String raw = meta.getPersistentDataContainer().get(keys.ownerKey(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void createAndOpenFromPlayerInventory(Player player, int slot) {
        if (!canCreate(player)) {
            player.sendMessage("§c你没有创建大容量潜影盒的权限。");
            return;
        }

        ItemStack stack = player.getInventory().getItem(slot);
        if (!isSingleShulker(stack)) {
            return;
        }

        ItemStack large = ensureLargeShulker(stack, player.getUniqueId());
        player.getInventory().setItem(slot, large);

        openFromStack(player, large, LargeShulkerSession.SourceBinding.playerSlot(slot));
    }

    public void createAndOpenFromMainHand(Player player) {
        if (!canCreate(player)) {
            player.sendMessage("§c你没有创建大容量潜影盒的权限。");
            return;
        }

        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!isSingleShulker(stack)) {
            return;
        }

        ItemStack large = ensureLargeShulker(stack, player.getUniqueId());
        player.getInventory().setItemInMainHand(large);

        openFromStack(player, large, LargeShulkerSession.SourceBinding.mainHand());
    }

    public void openExistingFromPlayerInventory(Player player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (!isSingleShulker(stack)) {
            return;
        }

        if (!canOpenExisting(player, stack)) {
            player.sendMessage("§c你没有打开该大容量潜影盒的权限。");
            return;
        }

        openFromStack(player, stack, LargeShulkerSession.SourceBinding.playerSlot(slot));
    }

    public void openExistingFromMainHand(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!isSingleShulker(stack)) {
            return;
        }

        if (!canOpenExisting(player, stack)) {
            player.sendMessage("§c你没有打开该大容量潜影盒的权限。");
            return;
        }

        openFromStack(player, stack, LargeShulkerSession.SourceBinding.mainHand());
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
        closeSession(player.getUniqueId(), session.shulkerId());
        playShulkerClose(player);
    }

    public void handleQuitOrKick(Player player) {
        LargeShulkerSession session = sessionsByPlayer.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        saveSession(session);
        closeSession(player.getUniqueId(), session.shulkerId());
    }

    public void startAutoSave() {
        stopAutoSave();

        autoSaveTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> flushAsync(),
                saveIntervalSeconds * 20L,
                saveIntervalSeconds * 20L
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
    }

    public void flushAsync() {
        Map<UUID, LargeShulkerData> snapshot = store.snapshot();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> store.writeSnapshot(snapshot));
    }

    public void flushNow() {
        store.saveNow();
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

        UUID previousPlayer = activeByShulker.putIfAbsent(shulkerId, player.getUniqueId());
        if (previousPlayer != null && !previousPlayer.equals(player.getUniqueId())) {
            player.sendMessage("§c该大容量潜影盒正在被其他玩家使用。");
            return;
        }

        UUID owner = getOwner(stack);
        LargeShulkerData data = store.getOrCreate(shulkerId, owner, rows);
        data.touch();
        store.put(data);

        LargeShulkerHolder holder = new LargeShulkerHolder(shulkerId);
        Inventory inventory = Bukkit.createInventory(holder, data.size(), Component.text(title));
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
            data = new LargeShulkerData(session.shulkerId(), null, rows);
        }

        data.setItems(session.inventory().getContents());
        data.touch();

        store.put(data);
    }

    private void closeSession(UUID playerId, UUID shulkerId) {
        sessionsByPlayer.remove(playerId);
        activeByShulker.remove(shulkerId, playerId);
    }

    private ItemStack ensureLargeShulker(ItemStack original, UUID owner) {
        ItemStack stack = original.clone();
        ItemMeta meta = stack.getItemMeta();

        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return stack;
        }

        UUID existingId = getShulkerId(stack);
        if (existingId != null) {
            return stack;
        }

        UUID shulkerId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        PersistentDataContainer pdc = blockStateMeta.getPersistentDataContainer();
        pdc.set(keys.idKey(), PersistentDataType.STRING, shulkerId.toString());
        pdc.set(keys.ownerKey(), PersistentDataType.STRING, owner.toString());
        pdc.set(keys.rowsKey(), PersistentDataType.INTEGER, rows);
        pdc.set(keys.versionKey(), PersistentDataType.INTEGER, DATA_VERSION);
        pdc.set(keys.createdAtKey(), PersistentDataType.LONG, now);

        LargeShulkerData data = new LargeShulkerData(shulkerId, owner, rows);
        data.setCreatedAt(now);
        data.setLastAccessTime(now);

        if (blockStateMeta.getBlockState() instanceof ShulkerBox box) {
            ItemStack[] migrated = new ItemStack[data.size()];
            ItemStack[] oldContents = box.getInventory().getContents();

            int len = Math.min(oldContents.length, migrated.length);
            for (int i = 0; i < len; i++) {
                migrated[i] = cloneOrNull(oldContents[i]);
            }

            data.setItems(migrated);

            /*
             * Avoid dual-source storage.
             *
             * After conversion, large shulker contents live in large-shulkers.yml.
             * The vanilla 27-slot BlockState inventory is cleared to avoid duplication
             * through vanilla reads or other plugins.
             */
            box.getInventory().clear();
            blockStateMeta.setBlockState(box);
        }

        stack.setItemMeta(blockStateMeta);
        store.put(data);

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
        player.playSound(player, Sound.BLOCK_SHULKER_BOX_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);
    }

    @SuppressWarnings("null")
    private static void playShulkerClose(Player player) {
        player.playSound(player, Sound.BLOCK_SHULKER_BOX_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.2f);
    }

    public UUID getShulkerId(Block block) {
        if (block == null || !isShulkerMaterial(block.getType())) {
            return null;
        }

        if (!(block.getState() instanceof TileState tile)) {
            return null;
        }

        return getShulkerId(tile);
    }

    public UUID getShulkerId(TileState tile) {
        if (tile == null) {
            return null;
        }

        String raw = tile.getPersistentDataContainer().get(
                keys.idKey(),
                PersistentDataType.STRING
        );

        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isLargeShulkerBlock(Block block) {
        return getShulkerId(block) != null;
    }

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
        Integer rows = itemPdc.get(keys.rowsKey(), PersistentDataType.INTEGER);
        Integer version = itemPdc.get(keys.versionKey(), PersistentDataType.INTEGER);
        Long createdAt = itemPdc.get(keys.createdAtKey(), PersistentDataType.LONG);

        if (id != null) {
            blockPdc.set(keys.idKey(), PersistentDataType.STRING, id);
        }

        if (owner != null) {
            blockPdc.set(keys.ownerKey(), PersistentDataType.STRING, owner);
        }

        if (rows != null) {
            blockPdc.set(keys.rowsKey(), PersistentDataType.INTEGER, rows);
        }

        if (version != null) {
            blockPdc.set(keys.versionKey(), PersistentDataType.INTEGER, version);
        }

        if (createdAt != null) {
            blockPdc.set(keys.createdAtKey(), PersistentDataType.LONG, createdAt);
        }
    }

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

        /*
        * Keep vanilla shulker contents empty.
        */
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

        placedIndex.put(locationKey(location), shulkerId);
    }

    public void removePlaced(Location location) {
        if (location == null) {
            return;
        }

        placedIndex.remove(locationKey(location));
    }

    public UUID getPlaced(Location location) {
        if (location == null) {
            return null;
        }

        return placedIndex.get(locationKey(location));
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
}