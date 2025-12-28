package me.LeafPixel.LeafInventory;

import java.util.*;

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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

public class InventoryListener implements Listener
{
    LeafInventory plugin;
    FileConfiguration config;
    boolean isPaper;
    Map<UUID, ItemStack> openShulkerBoxes = new HashMap<>();
    // Tracks the unique lock token for the shulker item currently opened by each player
    Map<UUID, String> openShulkerTokens = new HashMap<>();

    boolean enableShulkerbox;
    boolean enableEnderChest;
    boolean enableCraftingTable;
    boolean enableSmithingTable;
    boolean enableStoneCutter;
    boolean enableGrindstone;
    boolean enableCartographyTable;
    boolean enableLoom;
    boolean enableAnvil;

    boolean usePermissions;

    public InventoryListener(LeafInventory plugin, FileConfiguration config, boolean isPaper) {
        this.config = config;
        this.plugin = plugin;
        this.isPaper = isPaper;

        enableShulkerbox = config.getBoolean("enableShulkerbox", true);
        enableEnderChest = config.getBoolean("enableEnderChest", true);
        enableCraftingTable = config.getBoolean("enableCraftingTable", true);
        if (isPaper)
        {
            enableSmithingTable = config.getBoolean("enableSmithingTable", true);
            enableStoneCutter = config.getBoolean("enableStoneCutter", true);
            enableGrindstone = config.getBoolean("enableGrindstone", true);
            enableCartographyTable = config.getBoolean("enableCartographyTable", true);
            enableLoom = config.getBoolean("enableLoom", true);
            enableAnvil = config.getBoolean("enableAnvil", false);
        }
        usePermissions = config.getBoolean("usePermissions", false);
    }

    private boolean IsShulkerBox(Material material)
    {
        switch (material)
        {
            case SHULKER_BOX:
            case RED_SHULKER_BOX:
            case MAGENTA_SHULKER_BOX:
            case PINK_SHULKER_BOX:
            case PURPLE_SHULKER_BOX:
            case YELLOW_SHULKER_BOX:
            case ORANGE_SHULKER_BOX:
            case LIME_SHULKER_BOX:
            case GREEN_SHULKER_BOX:
            case CYAN_SHULKER_BOX:
            case BLUE_SHULKER_BOX:
            case LIGHT_BLUE_SHULKER_BOX:
            case LIGHT_GRAY_SHULKER_BOX:
            case GRAY_SHULKER_BOX:
            case BROWN_SHULKER_BOX:
            case BLACK_SHULKER_BOX:
            case WHITE_SHULKER_BOX:
                return true;
            default:
                return false;
        }
    }

    private boolean IsAnvil(Material material)
    {
        switch (material)
        {
            case ANVIL:
            case CHIPPED_ANVIL:
            case DAMAGED_ANVIL:
                return true;
            default:
                return false;
        }
    }

    // --- MenuType API helper (recommended in Paper 1.21.4+ over the deprecated openXxx methods) ---
    private void openMenuIfNotAlready(HumanEntity player, InventoryType legacyType, MenuType.Typed<? extends InventoryView, ?> menuType) {
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().getType() == legacyType) {
            return;
        }
        InventoryView view = menuType.create(player);
        player.openInventory(view);
    }


    private void ShowEnderchest(HumanEntity player)
    {
        if (player.getOpenInventory().getType() == InventoryType.ENDER_CHEST)
        {
            player.closeInventory();
            Bukkit.getServer().getPlayer(player.getUniqueId()).playSound(player, Sound.BLOCK_ENDER_CHEST_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.2f);
        }
        else
        {
            player.openInventory(player.getEnderChest());
            Bukkit.getServer().getPlayer(player.getUniqueId()).playSound(player, Sound.BLOCK_ENDER_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);
        }
    }

        private void ShowCraftingTable(HumanEntity player)
    {
        openMenuIfNotAlready(player, InventoryType.WORKBENCH, MenuType.CRAFTING);
    }
    private void ShowStoneCutter(HumanEntity player)
    {
        openMenuIfNotAlready(player, InventoryType.STONECUTTER, MenuType.STONECUTTER);
    }
    private void ShowCartographyTable(HumanEntity player)
    {
        openMenuIfNotAlready(player, InventoryType.CARTOGRAPHY, MenuType.CARTOGRAPHY_TABLE);
    }
    private void ShowLoom(HumanEntity player)
    {
        openMenuIfNotAlready(player, InventoryType.LOOM, MenuType.LOOM);
    }
    private void ShowSmithingTable(HumanEntity player)
    {
        openMenuIfNotAlready(player, InventoryType.SMITHING, MenuType.SMITHING);
    }
    private void ShowGrindstone(HumanEntity player)
    {
        openMenuIfNotAlready(player, InventoryType.GRINDSTONE, MenuType.GRINDSTONE);
    }
    private void ShowAnvil(HumanEntity player)
    {
        openMenuIfNotAlready(player, InventoryType.ANVIL, MenuType.ANVIL);
    }
private void OpenShulkerbox(HumanEntity player, ItemStack shulkerItem)
    {
        // Don't open the box if already open (avoids a duplication bug)
        if (openShulkerBoxes.containsKey(player.getUniqueId()) && openShulkerBoxes.get(player.getUniqueId()).equals(shulkerItem))
        {
            return;
        }

        // Added NBT for "locking" to prevent stacking shulker boxes
        ItemMeta meta = shulkerItem.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        NamespacedKey nbtKey = new NamespacedKey(plugin, "__shulkerbox_plugin");
        if(!data.has(nbtKey, PersistentDataType.STRING)) {
            data.set(nbtKey, PersistentDataType.STRING, String.valueOf(System.currentTimeMillis()));
            shulkerItem.setItemMeta(meta);
        }
        // Store the lock token so we can find the real item later even if the ItemStack reference changes
        String lockToken = data.get(nbtKey, PersistentDataType.STRING);
        openShulkerTokens.put(player.getUniqueId(), lockToken);

        Inventory shulker_inventory = ((ShulkerBox)((BlockStateMeta)meta).getBlockState()).getSnapshotInventory();

        Inventory inventory;
        Component title = meta.displayName(); 

        if (title == null) {
            inventory = Bukkit.createInventory(null, InventoryType.SHULKER_BOX);
        } else {
            inventory = Bukkit.createInventory(null, InventoryType.SHULKER_BOX, title); 
        }

        inventory.setContents(shulker_inventory.getContents());


        player.openInventory(inventory);
        Bukkit.getServer().getPlayer(player.getUniqueId()).playSound(player, Sound.BLOCK_SHULKER_BOX_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);

        openShulkerBoxes.put(player.getUniqueId(), shulkerItem);
    }

    private void CloseShulkerbox(HumanEntity player)
    {
        String token = openShulkerTokens.get(player.getUniqueId());
        ItemStack shulkerItem = null;

        // Prefer locating the real item in the player's inventory by lock token (more robust than keeping an ItemStack reference)
        if (token != null && player instanceof Player p) {
            ItemStack[] contents = p.getInventory().getContents();
            for (ItemStack it : contents) {
                if (it == null) continue;
                if (!IsShulkerBox(it.getType())) continue;
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

        // Fallback to the originally stored reference
        if (shulkerItem == null) {
            shulkerItem = openShulkerBoxes.get(player.getUniqueId());
        }

        if (shulkerItem == null) {
            openShulkerBoxes.remove(player.getUniqueId());
            openShulkerTokens.remove(player.getUniqueId());
            return;
        }

        ItemMeta baseMeta = shulkerItem.getItemMeta();
        if (!(baseMeta instanceof BlockStateMeta meta)) {
            openShulkerBoxes.remove(player.getUniqueId());
            openShulkerTokens.remove(player.getUniqueId());
            return;
        }

        ShulkerBox shulkerbox = (ShulkerBox) meta.getBlockState();
        shulkerbox.getInventory().setContents(player.getOpenInventory().getTopInventory().getContents());

        // Remove NBT lock token to allow stacking again
        PersistentDataContainer data = meta.getPersistentDataContainer();
        NamespacedKey nbtKey = new NamespacedKey(plugin, "__shulkerbox_plugin");
        if (data.has(nbtKey, PersistentDataType.STRING)) {
            data.remove(nbtKey);
        }

        meta.setBlockState(shulkerbox);
        shulkerItem.setItemMeta(meta);

        Bukkit.getServer().getPlayer(player.getUniqueId()).playSound(player, Sound.BLOCK_SHULKER_BOX_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.2f);
        openShulkerBoxes.remove(player.getUniqueId());
        openShulkerTokens.remove(player.getUniqueId());
    
    }

    @EventHandler(priority = EventPriority.LOW)
    public void InventoryClick(InventoryClickEvent e)
    {
        if (e.getAction() == InventoryAction.NOTHING)
        {
            return;
        }



        if (!e.isRightClick() || e.isShiftClick()) {
            if (openShulkerBoxes.containsKey(e.getWhoClicked().getUniqueId()) &&
                (
                    e.getClick() == ClickType.NUMBER_KEY
                    || e.getAction() == InventoryAction.HOTBAR_SWAP
                    || (e.getCurrentItem() != null && IsShulkerBox(e.getCurrentItem().getType()))
                )
            ) {
                e.setCancelled(true);
            }
            return;
        }



        InventoryType clickedInventory = e.getClickedInventory().getType();

        if (!(clickedInventory == InventoryType.PLAYER || clickedInventory == InventoryType.ENDER_CHEST || clickedInventory == InventoryType.SHULKER_BOX))
        {
            return;
        }

        ItemStack item = e.getCurrentItem();
        if (item == null)
        {
            return;
        }

        if (item.getAmount() != 1)
        {
            return;
        }

        Material itemType = item.getType();
        HumanEntity player = e.getWhoClicked();

        if (clickedInventory != InventoryType.SHULKER_BOX
                && IsShulkerBox(itemType)
                && enableShulkerbox
                && (!usePermissions || player.hasPermission("leafinventory.shulkerbox")))
        {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                    plugin,
                    () -> OpenShulkerbox(player, item)
            );
            e.setCancelled(true);
        }

        if (itemType == Material.ENDER_CHEST
                && enableEnderChest
                && (!usePermissions || player.hasPermission("leafinventory.enderchest")))
        {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                    plugin,
                    () -> ShowEnderchest(player)
            );
            e.setCancelled(true);
        }

        if (itemType == Material.CRAFTING_TABLE
                && enableCraftingTable
                && (!usePermissions || player.hasPermission("leafinventory.craftingtable")))
        {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                    plugin,
                    () -> ShowCraftingTable(player)
            );
            e.setCancelled(true);
        }

        if (isPaper)
        {
            if (itemType == Material.STONECUTTER
                    && enableStoneCutter
                    && (!usePermissions || player.hasPermission("leafinventory.stonecutter")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowStoneCutter(player)
                );
                e.setCancelled(true);
            }

            if (itemType == Material.CARTOGRAPHY_TABLE
                    && enableCartographyTable
                    && (!usePermissions || player.hasPermission("leafinventory.cartographytable")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowCartographyTable(player)
                );
                e.setCancelled(true);
            }

            if (itemType == Material.LOOM
                    && enableLoom
                    && (!usePermissions || player.hasPermission("leafinventory.loom")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowLoom(player)
                );
                e.setCancelled(true);
            }

            if (itemType == Material.SMITHING_TABLE
                    && enableSmithingTable
                    && (!usePermissions || player.hasPermission("leafinventory.smithingtable")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowSmithingTable(player)
                );
                e.setCancelled(true);
            }

            if (itemType == Material.GRINDSTONE
                    && enableGrindstone
                    && (!usePermissions || player.hasPermission("leafinventory.grindstone")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowGrindstone(player)
                );
                e.setCancelled(true);
            }

            if (IsAnvil(itemType)
                    && enableAnvil
                    && (!usePermissions || player.hasPermission("leafinventory.anvil")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowAnvil(player)
                );
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void RightClick(PlayerInteractEvent e)
    {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_AIR)
        {
            return;
        }

        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        Material itemType = item.getType();

        if (IsShulkerBox(itemType)
                && item.getAmount() == 1
                && enableShulkerbox
                && (!usePermissions || player.hasPermission("LeafInventory.shulkerbox")))
        {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                    plugin,
                    () -> OpenShulkerbox(player, item)
            );
            e.setCancelled(true);
        }

        if (itemType == Material.ENDER_CHEST
                && enableEnderChest
                && (!usePermissions || player.hasPermission("LeafInventory.enderchest")))
        {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                    plugin,
                    () -> ShowEnderchest(player)
            );
            e.setCancelled(true);
        }

        if (itemType == Material.CRAFTING_TABLE
                && enableCraftingTable
                && (!usePermissions || player.hasPermission("LeafInventory.craftingtable")))
        {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                    plugin,
                    () -> ShowCraftingTable(player)
            );
            e.setCancelled(true);
        }

        if (isPaper)
        {
            if (itemType == Material.STONECUTTER
                    && enableStoneCutter
                    && (!usePermissions || player.hasPermission("LeafInventory.stonecutter")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowStoneCutter(player)
                );
                e.setCancelled(true);
            }

            if (itemType == Material.CARTOGRAPHY_TABLE
                    && enableCartographyTable
                    && (!usePermissions || player.hasPermission("LeafInventory.cartographytable")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowCartographyTable(player)
                );
                e.setCancelled(true);
            }

            if (itemType == Material.LOOM
                    && enableLoom
                    && (!usePermissions || player.hasPermission("LeafInventory.loom")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowLoom(player)
                );
                e.setCancelled(true);
            }

            if (itemType == Material.SMITHING_TABLE
                    && enableSmithingTable
                    && (!usePermissions || player.hasPermission("LeafInventory.smithingtable")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowSmithingTable(player)
                );
                e.setCancelled(true);
            }

            if (itemType == Material.GRINDSTONE
                    && enableGrindstone
                    && (!usePermissions || player.hasPermission("LeafInventory.grindstone")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowGrindstone(player)
                );
                e.setCancelled(true);
            }

            if (IsAnvil(itemType)
                    && enableAnvil
                    && (!usePermissions || player.hasPermission("LeafInventory.anvil")))
            {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ShowAnvil(player)
                );
                e.setCancelled(true);
            }
        }
    }

    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void InventoryDrag(InventoryDragEvent e)
    {
        if (openShulkerBoxes.containsKey(e.getWhoClicked().getUniqueId()))
        {
            // Simplest safe option: disallow dragging while a shulker view is open to avoid moving the backing item.
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void DropItem(PlayerDropItemEvent e)
    {
        if (openShulkerBoxes.containsKey(e.getPlayer().getUniqueId()))
        {
            // Prevent dropping items while a shulker view is open (avoids desync / wrong write-back)
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void SwapHand(PlayerSwapHandItemsEvent e)
    {
        if (openShulkerBoxes.containsKey(e.getPlayer().getUniqueId()))
        {
            // Prevent swapping hands while a shulker view is open
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void Quit(PlayerQuitEvent e)
    {
        if (openShulkerBoxes.containsKey(e.getPlayer().getUniqueId()))
        {
            CloseShulkerbox(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void Kick(PlayerKickEvent e)
    {
        if (openShulkerBoxes.containsKey(e.getPlayer().getUniqueId()))
        {
            CloseShulkerbox(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
        public void InventoryClose(InventoryCloseEvent e)
        {
            if (openShulkerBoxes.containsKey(e.getPlayer().getUniqueId()))
            {
                CloseShulkerbox(e.getPlayer());
            }
        }

    // Needs to close shulker box before items drop on death to avoid a duplication bug
    @EventHandler(priority = EventPriority.HIGHEST)
    public void Death(PlayerDeathEvent e)
    {
        Player player = e.getEntity();
        if (openShulkerBoxes.containsKey(player.getUniqueId()))
        {
            CloseShulkerbox(player);
        }
    }
}
