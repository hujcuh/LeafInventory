
package me.LeafPixel.LeafInventory;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Legacy listener for 1.21 ~ 1.21.3:
 * - DOES NOT reference org.bukkit.inventory.view.builder.* (builder API)
 * - Opens menus using MenuType#create(...) and fallbacks (openWorkbench/openEnchanting when needed)
 */
public final class InventoryListenerLegacy implements Listener {

    private final LeafInventory plugin;
    private final WorkstationManager workstationManager;

    // Shulker tracking
    private final Map<UUID, ItemStack> openShulkerBoxes = new HashMap<>();
    private final Map<UUID, String> openShulkerTokens = new HashMap<>();

    // Config flags
    private final boolean enableShulkerbox;
    private final boolean enableEnderChest;
    private final boolean enableCraftingTable;

    // Paper extra flags (still may exist in 1.21.1 Paper, but we open via MenuType.create, not builders)
    private final boolean enableSmithingTable;
    private final boolean enableStoneCutter;
    private final boolean enableGrindstone;
    private final boolean enableCartographyTable;
    private final boolean enableLoom;
    private final boolean enableAnvil;
    private final boolean enableEnchantingTable;
    private final boolean enableFurnace;
    private final boolean enableBlastFurnace;
    private final boolean enableSmoker;

    private final boolean usePermissions;

    public InventoryListenerLegacy(LeafInventory plugin, FileConfiguration config, WorkstationManager workstationManager) {
        this.plugin = plugin;
        this.workstationManager = workstationManager;

        enableShulkerbox = config.getBoolean("enableShulkerbox", true);
        enableEnderChest = config.getBoolean("enableEnderChest", true);
        enableCraftingTable = config.getBoolean("enableCraftingTable", true);

        // For legacy jar, we still read these; if server doesn't support the menu type, open will just fail and be ignored.
        enableSmithingTable = config.getBoolean("enableSmithingTable", true);
        enableStoneCutter = config.getBoolean("enableStoneCutter", true);
        enableGrindstone = config.getBoolean("enableGrindstone", true);
        enableCartographyTable = config.getBoolean("enableCartographyTable", true);
        enableLoom = config.getBoolean("enableLoom", true);
        enableAnvil = config.getBoolean("enableAnvil", false);
        enableEnchantingTable = config.getBoolean("enableEnchantingTable", true);

        enableFurnace = config.getBoolean("enableFurnace", true);
        enableBlastFurnace = config.getBoolean("enableBlastFurnace", true);
        enableSmoker = config.getBoolean("enableSmoker", true);

        usePermissions = config.getBoolean("usePermissions", false);
    }

    // -------------------------
    // Utilities
    // -------------------------

    private boolean hasPerm(HumanEntity player, String perm) {
        return !usePermissions || player.hasPermission(perm);
    }

    private boolean isShulkerBox(Material material) {
        return switch (material) {
            case SHULKER_BOX,
                 RED_SHULKER_BOX, MAGENTA_SHULKER_BOX, PINK_SHULKER_BOX, PURPLE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX, ORANGE_SHULKER_BOX, LIME_SHULKER_BOX, GREEN_SHULKER_BOX,
                 CYAN_SHULKER_BOX, BLUE_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                 LIGHT_GRAY_SHULKER_BOX, GRAY_SHULKER_BOX, BROWN_SHULKER_BOX,
                 BLACK_SHULKER_BOX, WHITE_SHULKER_BOX -> true;
            default -> false;
        };
    }

    private boolean isAnvil(Material material) {
        return material == Material.ANVIL || material == Material.CHIPPED_ANVIL || material == Material.DAMAGED_ANVIL;
    }

    /**
     * Open a MenuType using create() (NO builder usage).
     * If already at the same legacy inventory type, do nothing.
     */
    private boolean openMenuByCreateIfNotAlready(HumanEntity player, InventoryType legacyType, MenuType menuType) {
        try {
            if (player.getOpenInventory() != null
                    && player.getOpenInventory().getTopInventory().getType() == legacyType) {
                return true;
            }

            // Prefer create(player) if exists.
            // Some APIs have create(HumanEntity) or create(HumanEntity, String/Component)
            // We'll try reflectively for resilience across minor API differences.
            InventoryView view = tryCreateMenuView(menuType, player);
            if (view == null) return false;

            player.openInventory(view);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private InventoryView tryCreateMenuView(MenuType menuType, HumanEntity player) {
        try {
            // 1) create(HumanEntity)
            Method m = menuType.getClass().getMethod("create", HumanEntity.class);
            Object view = m.invoke(menuType, player);
            return (InventoryView) view;
        } catch (NoSuchMethodException ignored) {
            // try next
        } catch (Throwable t) {
            return null;
        }

        try {
            // 2) create(HumanEntity, String)
            Method m = menuType.getClass().getMethod("create", HumanEntity.class, String.class);
            Object view = m.invoke(menuType, player, "");
            return (InventoryView) view;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Create shulker UI inventory with optional title (Component), with reflection fallback.
     */
    private Inventory createShulkerUI(Component title) {
        try {
            if (title != null) {
                // Bukkit.createInventory(InventoryHolder, InventoryType, Component) may exist in modern servers
                Method m = Bukkit.class.getMethod("createInventory", InventoryHolder.class, InventoryType.class, Component.class);
                return (Inventory) m.invoke(null, null, InventoryType.SHULKER_BOX, title);
            }
        } catch (Throwable ignored) { }
        // fallback without title
        return Bukkit.createInventory(null, InventoryType.SHULKER_BOX);
    }

    // -------------------------
    // Menu actions
    // -------------------------

    private void showEnderChest(HumanEntity player) {
        if (player.getOpenInventory() != null && player.getOpenInventory().getType() == InventoryType.ENDER_CHEST) {
            player.closeInventory();
            Player p = Bukkit.getPlayer(player.getUniqueId());
            if (p != null) p.playSound(player, Sound.BLOCK_ENDER_CHEST_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.2f);
            return;
        }
        player.openInventory(player.getEnderChest());
        Player p = Bukkit.getPlayer(player.getUniqueId());
        if (p != null) p.playSound(player, Sound.BLOCK_ENDER_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);
    }

    private void showCraftingTable(HumanEntity player) {
        boolean ok = openMenuByCreateIfNotAlready(player, InventoryType.WORKBENCH, MenuType.CRAFTING);
        if (!ok) {
            // fallback legacy api (deprecated but exists for compatibility)
            player.openWorkbench(player.getLocation(), false);
        }
    }

    private void showStoneCutter(HumanEntity player) {
        openMenuByCreateIfNotAlready(player, InventoryType.STONECUTTER, MenuType.STONECUTTER);
    }

    private void showCartographyTable(HumanEntity player) {
        openMenuByCreateIfNotAlready(player, InventoryType.CARTOGRAPHY, MenuType.CARTOGRAPHY_TABLE);
    }

    private void showLoom(HumanEntity player) {
        openMenuByCreateIfNotAlready(player, InventoryType.LOOM, MenuType.LOOM);
    }

    private void showSmithingTable(HumanEntity player) {
        openMenuByCreateIfNotAlready(player, InventoryType.SMITHING, MenuType.SMITHING);
    }

    private void showGrindstone(HumanEntity player) {
        openMenuByCreateIfNotAlready(player, InventoryType.GRINDSTONE, MenuType.GRINDSTONE);
    }

    private void showAnvil(HumanEntity player) {
        openMenuByCreateIfNotAlready(player, InventoryType.ANVIL, MenuType.ANVIL);
    }

    private void showEnchantingTable(HumanEntity player) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getType() == InventoryType.ENCHANTING) {
            player.closeInventory();
            return;
        }

        boolean ok = openMenuByCreateIfNotAlready(player, InventoryType.ENCHANTING, MenuType.ENCHANTMENT);
        if (!ok) {
            // fallback legacy api (deprecated but exists for compatibility)
            player.openEnchanting(player.getLocation(), false);
        }
    }

    private void showFurnace(Player player) {
        if (workstationManager != null) workstationManager.openFurnace(player);
    }

    private void showBlastFurnace(Player player) {
        if (workstationManager != null) workstationManager.openBlastFurnace(player);
    }

    private void showSmoker(Player player) {
        if (workstationManager != null) workstationManager.openSmoker(player);
    }

    // -------------------------
    // Shulker open/close
    // -------------------------

    private void openShulkerBox(HumanEntity player, ItemStack shulkerItem) {
        // avoid reopening same box (duplication guard)
        UUID uuid = player.getUniqueId();
        if (openShulkerBoxes.containsKey(uuid) && openShulkerBoxes.get(uuid) != null && openShulkerBoxes.get(uuid).equals(shulkerItem)) {
            return;
        }

        ItemMeta meta = shulkerItem.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) return;
        if (!(bsm.getBlockState() instanceof ShulkerBox shulkerBoxState)) return;

        // lock token in PDC to prevent stacking / help locate correct item on close
        PersistentDataContainer data = meta.getPersistentDataContainer();
        NamespacedKey nbtKey = new NamespacedKey(plugin, "__shulkerbox_plugin");

        if (!data.has(nbtKey, PersistentDataType.STRING)) {
            // Use UUID for stronger uniqueness than currentTimeMillis (still removed on close)
            data.set(nbtKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            shulkerItem.setItemMeta(meta);
        }

        String lockToken = data.get(nbtKey, PersistentDataType.STRING);
        openShulkerTokens.put(uuid, lockToken);

        Inventory snapshotInv = shulkerBoxState.getSnapshotInventory();
        Component title = meta.displayName();
        Inventory ui = createShulkerUI(title);

        ui.setContents(snapshotInv.getContents());
        player.openInventory(ui);

        Player p = Bukkit.getPlayer(uuid);
        if (p != null) p.playSound(player, Sound.BLOCK_SHULKER_BOX_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);

        openShulkerBoxes.put(uuid, shulkerItem);
    }

    private void closeShulkerBox(HumanEntity player) {
        UUID uuid = player.getUniqueId();
        String token = openShulkerTokens.get(uuid);

        ItemStack shulkerItem = null;

        // Prefer locating by token in player's inventory (robust)
        if (token != null && player instanceof Player p) {
            ItemStack[] contents = p.getInventory().getContents();
            for (ItemStack it : contents) {
                if (it == null) continue;
                if (!isShulkerBox(it.getType())) continue;
                ItemMeta im = it.getItemMeta();
                if (im == null) continue;
                PersistentDataContainer dc = im.getPersistentDataContainer();
                NamespacedKey k = new NamespacedKey(plugin, "__shulkerbox_plugin");
                String t = dc.get(k, PersistentDataType.STRING);
                if (token.equals(t)) {
                    shulkerItem = it;
                    break;
                }
            }
        }

        // Fallback to stored reference
        if (shulkerItem == null) shulkerItem = openShulkerBoxes.get(uuid);

        if (shulkerItem == null) {
            openShulkerBoxes.remove(uuid);
            openShulkerTokens.remove(uuid);
            return;
        }

        ItemMeta baseMeta = shulkerItem.getItemMeta();
        if (!(baseMeta instanceof BlockStateMeta meta)) {
            openShulkerBoxes.remove(uuid);
            openShulkerTokens.remove(uuid);
            return;
        }

        if (!(meta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            openShulkerBoxes.remove(uuid);
            openShulkerTokens.remove(uuid);
            return;
        }

        // write back UI contents into shulker state
        Inventory top = player.getOpenInventory().getTopInventory();
        shulkerBox.getInventory().setContents(top.getContents());

        // remove token to allow stacking again
        PersistentDataContainer data = meta.getPersistentDataContainer();
        NamespacedKey nbtKey = new NamespacedKey(plugin, "__shulkerbox_plugin");
        if (data.has(nbtKey, PersistentDataType.STRING)) data.remove(nbtKey);

        meta.setBlockState(shulkerBox);
        shulkerItem.setItemMeta(meta);

        Player p = Bukkit.getPlayer(uuid);
        if (p != null) p.playSound(player, Sound.BLOCK_SHULKER_BOX_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.2f);

        openShulkerBoxes.remove(uuid);
        openShulkerTokens.remove(uuid);
    }

    // -------------------------
    // Event handlers
    // -------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getAction() == InventoryAction.NOTHING) return;

        // If not right click OR is shift-click, we generally ignore;
        // but still block dangerous swaps when shulker UI is open.
        if (!e.isRightClick() || e.isShiftClick()) {
            if (openShulkerBoxes.containsKey(e.getWhoClicked().getUniqueId())) {
                // Block number-key or hotbar swap actions involving shulkers during open view
                if (e.getClick() == ClickType.NUMBER_KEY
                        || e.getAction() == InventoryAction.HOTBAR_SWAP
                        || (e.getCurrentItem() != null && isShulkerBox(e.getCurrentItem().getType()))) {
                    e.setCancelled(true);
                }
            }
            return;
        }

        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        InventoryType clickedType = clicked.getType();
        if (!(clickedType == InventoryType.PLAYER
                || clickedType == InventoryType.ENDER_CHEST
                || clickedType == InventoryType.SHULKER_BOX)) {
            return;
        }

        ItemStack item = e.getCurrentItem();
        if (item == null) return;
        if (item.getAmount() != 1) return;

        HumanEntity player = e.getWhoClicked();
        Material itemType = item.getType();

        // Shulker box from inventory (do not open if already in shulker UI)
        if (clickedType != InventoryType.SHULKER_BOX
                && isShulkerBox(itemType)
                && enableShulkerbox
                && hasPerm(player, "leafinventory.shulkerbox")) {
            Bukkit.getScheduler().runTask(plugin, () -> openShulkerBox(player, item));
            e.setCancelled(true);
            return;
        }

        // Ender chest item
        if (itemType == Material.ENDER_CHEST
                && enableEnderChest
                && hasPerm(player, "leafinventory.enderchest")) {
            Bukkit.getScheduler().runTask(plugin, () -> showEnderChest(player));
            e.setCancelled(true);
            return;
        }

        // Crafting table item
        if (itemType == Material.CRAFTING_TABLE
                && enableCraftingTable
                && hasPerm(player, "leafinventory.craftingtable")) {
            Bukkit.getScheduler().runTask(plugin, () -> showCraftingTable(player));
            e.setCancelled(true);
            return;
        }

        // Extras (work on legacy by MenuType.create; if unsupported it just fails silently)
        if (itemType == Material.ENCHANTING_TABLE
                && enableEnchantingTable
                && hasPerm(player, "leafinventory.enchantingtable")) {
            Bukkit.getScheduler().runTask(plugin, () -> showEnchantingTable(player));
            e.setCancelled(true);
            return;
        }

        if (itemType == Material.STONECUTTER
                && enableStoneCutter
                && hasPerm(player, "leafinventory.stonecutter")) {
            Bukkit.getScheduler().runTask(plugin, () -> showStoneCutter(player));
            e.setCancelled(true);
            return;
        }

        if (itemType == Material.CARTOGRAPHY_TABLE
                && enableCartographyTable
                && hasPerm(player, "leafinventory.cartographytable")) {
            Bukkit.getScheduler().runTask(plugin, () -> showCartographyTable(player));
            e.setCancelled(true);
            return;
        }

        if (itemType == Material.LOOM
                && enableLoom
                && hasPerm(player, "leafinventory.loom")) {
            Bukkit.getScheduler().runTask(plugin, () -> showLoom(player));
            e.setCancelled(true);
            return;
        }

        if (itemType == Material.SMITHING_TABLE
                && enableSmithingTable
                && hasPerm(player, "leafinventory.smithingtable")) {
            Bukkit.getScheduler().runTask(plugin, () -> showSmithingTable(player));
            e.setCancelled(true);
            return;
        }

        if (itemType == Material.GRINDSTONE
                && enableGrindstone
                && hasPerm(player, "leafinventory.grindstone")) {
            Bukkit.getScheduler().runTask(plugin, () -> showGrindstone(player));
            e.setCancelled(true);
            return;
        }

        if (isAnvil(itemType)
                && enableAnvil
                && hasPerm(player, "leafinventory.anvil")) {
            Bukkit.getScheduler().runTask(plugin, () -> showAnvil(player));
            e.setCancelled(true);
            return;
        }

        // Workstation-backed furnaces
        if (player instanceof Player p) {
            if (itemType == Material.FURNACE
                    && enableFurnace
                    && hasPerm(player, "leafinventory.furnace")) {
                Bukkit.getScheduler().runTask(plugin, () -> showFurnace(p));
                e.setCancelled(true);
            } else if (itemType == Material.BLAST_FURNACE
                    && enableBlastFurnace
                    && hasPerm(player, "leafinventory.blastfurnace")) {
                Bukkit.getScheduler().runTask(plugin, () -> showBlastFurnace(p));
                e.setCancelled(true);
            } else if (itemType == Material.SMOKER
                    && enableSmoker
                    && hasPerm(player, "leafinventory.smoker")) {
                Bukkit.getScheduler().runTask(plugin, () -> showSmoker(p));
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRightClickAir(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR) return;

        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getAmount() != 1) return;

        Material type = item.getType();

        if (isShulkerBox(type) && enableShulkerbox && hasPerm(player, "leafinventory.shulkerbox")) {
            Bukkit.getScheduler().runTask(plugin, () -> openShulkerBox(player, item));
            e.setCancelled(true);
            return;
        }

        if (type == Material.ENDER_CHEST && enableEnderChest && hasPerm(player, "leafinventory.enderchest")) {
            Bukkit.getScheduler().runTask(plugin, () -> showEnderChest(player));
            e.setCancelled(true);
            return;
        }

        if (type == Material.CRAFTING_TABLE && enableCraftingTable && hasPerm(player, "leafinventory.craftingtable")) {
            Bukkit.getScheduler().runTask(plugin, () -> showCraftingTable(player));
            e.setCancelled(true);
            return;
        }

        if (type == Material.ENCHANTING_TABLE && enableEnchantingTable && hasPerm(player, "leafinventory.enchantingtable")) {
            Bukkit.getScheduler().runTask(plugin, () -> showEnchantingTable(player));
            e.setCancelled(true);
            return;
        }

        if (type == Material.STONECUTTER && enableStoneCutter && hasPerm(player, "leafinventory.stonecutter")) {
            Bukkit.getScheduler().runTask(plugin, () -> showStoneCutter(player));
            e.setCancelled(true);
            return;
        }

        if (type == Material.CARTOGRAPHY_TABLE && enableCartographyTable && hasPerm(player, "leafinventory.cartographytable")) {
            Bukkit.getScheduler().runTask(plugin, () -> showCartographyTable(player));
            e.setCancelled(true);
            return;
        }

        if (type == Material.LOOM && enableLoom && hasPerm(player, "leafinventory.loom")) {
            Bukkit.getScheduler().runTask(plugin, () -> showLoom(player));
            e.setCancelled(true);
            return;
        }

        if (type == Material.SMITHING_TABLE && enableSmithingTable && hasPerm(player, "leafinventory.smithingtable")) {
            Bukkit.getScheduler().runTask(plugin, () -> showSmithingTable(player));
            e.setCancelled(true);
            return;
        }

        if (type == Material.GRINDSTONE && enableGrindstone && hasPerm(player, "leafinventory.grindstone")) {
            Bukkit.getScheduler().runTask(plugin, () -> showGrindstone(player));
            e.setCancelled(true);
            return;
        }

        if (isAnvil(type) && enableAnvil && hasPerm(player, "leafinventory.anvil")) {
            Bukkit.getScheduler().runTask(plugin, () -> showAnvil(player));
            e.setCancelled(true);
            return;
        }

        if (type == Material.FURNACE && enableFurnace && hasPerm(player, "leafinventory.furnace")) {
            Bukkit.getScheduler().runTask(plugin, () -> showFurnace(player));
            e.setCancelled(true);
            return;
        }

        if (type == Material.BLAST_FURNACE && enableBlastFurnace && hasPerm(player, "leafinventory.blastfurnace")) {
            Bukkit.getScheduler().runTask(plugin, () -> showBlastFurnace(player));
            e.setCancelled(true);
            return;
        }

        if (type == Material.SMOKER && enableSmoker && hasPerm(player, "leafinventory.smoker")) {
            Bukkit.getScheduler().runTask(plugin, () -> showSmoker(player));
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (openShulkerBoxes.containsKey(e.getWhoClicked().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (openShulkerBoxes.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (openShulkerBoxes.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        if (openShulkerBoxes.containsKey(e.getPlayer().getUniqueId())) {
            closeShulkerBox(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKick(PlayerKickEvent e) {
        if (openShulkerBoxes.containsKey(e.getPlayer().getUniqueId())) {
            closeShulkerBox(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (openShulkerBoxes.containsKey(e.getPlayer().getUniqueId())) {
            closeShulkerBox(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        if (openShulkerBoxes.containsKey(player.getUniqueId())) {
            closeShulkerBox(player);
        }
    }
}
