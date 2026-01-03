
package me.LeafPixel.LeafInventory.listener;

import me.LeafPixel.LeafInventory.config.LeafConfig;
import me.LeafPixel.LeafInventory.shulker.ShulkerItemIO;
import me.LeafPixel.LeafInventory.workstation.WorkstationManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType; // ✅ correct package: org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MenuItemListener (route2):
 * Handles opening non-shulker "menu items" from:
 *  - Right-clicking in an inventory (player/enderchest/shulker UI)
 *  - Right-clicking air while holding the item (main hand)
 *
 * This class does NOT handle shulker box editing; that is handled by ShulkerListener + session manager.
 *
 * Notes:
 * - Uses constructor-injected plugin for scheduling (no hard-coded plugin name).
 * - Keeps behavior similar to your original implementation: toggle close if the same menu is already open.
 */
public final class MenuItemListener implements Listener {

    private final JavaPlugin plugin;
    private final LeafConfig cfg;
    private final WorkstationManager workstationManager; // may be null when workstation is disabled

    public MenuItemListener(JavaPlugin plugin, LeafConfig cfg, WorkstationManager workstationManager) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.workstationManager = workstationManager; // ✅ IMPORTANT: initialize the final field
    }

    // -------------------------------------------------------------------------
    // Inventory right-click (works in PLAYER / ENDER_CHEST / SHULKER_BOX inventories)
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryRightClick(InventoryClickEvent e) {
        if (!e.isRightClick() || e.isShiftClick()) return;

        if (!(e.getWhoClicked() instanceof Player player)) return;

        if (e.getClickedInventory() == null) return;

        // Preserve your old behavior: allow clicks in player inventory, ender chest, and shulker UI.
        // (If you want to restrict to only PLAYER, change this condition.)
        InventoryType clickedType = e.getClickedInventory().getType();
        if (!(clickedType == InventoryType.PLAYER
                || clickedType == InventoryType.ENDER_CHEST
                || clickedType == InventoryType.SHULKER_BOX)) {
            return;
        }

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType().isAir()) return;

        // Keep your original constraint: do not open menu items if stack size != 1.
        if (item.getAmount() != 1) return;

        Material type = item.getType();

        // Shulker boxes are handled by ShulkerListener (session model).
        // We just return here to avoid double-cancelling or interfering.
        if (ShulkerItemIO.isShulkerBox(type)) return;

        // Dispatch based on item type
        if (type == Material.ENDER_CHEST && cfg.enableEnderChest && hasPerm(player, "leafinventory.enderchest")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> showEnderChest(player));
            return;
        }

        if (type == Material.CRAFTING_TABLE && cfg.enableCraftingTable && hasPerm(player, "leafinventory.craftingtable")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> showCraftingTable(player));
            return;
        }

        if (type == Material.ENCHANTING_TABLE && cfg.enableEnchantingTable && hasPerm(player, "leafinventory.enchantingtable")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> showEnchantingTable(player));
            return;
        }

        if (type == Material.STONECUTTER && cfg.enableStoneCutter && hasPerm(player, "leafinventory.stonecutter")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.STONECUTTER, MenuType.STONECUTTER));
            return;
        }

        if (type == Material.CARTOGRAPHY_TABLE && cfg.enableCartographyTable && hasPerm(player, "leafinventory.cartographytable")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.CARTOGRAPHY, MenuType.CARTOGRAPHY_TABLE));
            return;
        }

        if (type == Material.LOOM && cfg.enableLoom && hasPerm(player, "leafinventory.loom")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.LOOM, MenuType.LOOM));
            return;
        }

        if (type == Material.SMITHING_TABLE && cfg.enableSmithingTable && hasPerm(player, "leafinventory.smithingtable")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.SMITHING, MenuType.SMITHING));
            return;
        }

        if (type == Material.GRINDSTONE && cfg.enableGrindstone && hasPerm(player, "leafinventory.grindstone")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.GRINDSTONE, MenuType.GRINDSTONE));
            return;
        }

        if (isAnvil(type) && cfg.enableAnvil && hasPerm(player, "leafinventory.anvil")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.ANVIL, MenuType.ANVIL));
            return;
        }

        // Workstation-backed furnaces (optional). Only if workstationManager is available.
        if (type == Material.FURNACE && cfg.enableFurnace && hasPerm(player, "leafinventory.furnace")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (workstationManager != null) workstationManager.openFurnace(player);
            });
            return;
        }

        if (type == Material.BLAST_FURNACE && cfg.enableBlastFurnace && hasPerm(player, "leafinventory.blastfurnace")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (workstationManager != null) workstationManager.openBlastFurnace(player);
            });
            return;
        }

        if (type == Material.SMOKER && cfg.enableSmoker && hasPerm(player, "leafinventory.smoker")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (workstationManager != null) workstationManager.openSmoker(player);
            });
        }
    }

    // -------------------------------------------------------------------------
    // Right-click air (main hand)
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.NORMAL)
    public void onRightClickAir(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR) return;

        Player player = e.getPlayer();

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;
        if (item.getAmount() != 1) return;

        Material type = item.getType();

        // Shulker boxes handled by ShulkerListener
        if (ShulkerItemIO.isShulkerBox(type)) return;

        if (type == Material.ENDER_CHEST && cfg.enableEnderChest && hasPerm(player, "leafinventory.enderchest")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> showEnderChest(player));
            return;
        }

        if (type == Material.CRAFTING_TABLE && cfg.enableCraftingTable && hasPerm(player, "leafinventory.craftingtable")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> showCraftingTable(player));
            return;
        }

        if (type == Material.ENCHANTING_TABLE && cfg.enableEnchantingTable && hasPerm(player, "leafinventory.enchantingtable")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> showEnchantingTable(player));
            return;
        }

        if (type == Material.STONECUTTER && cfg.enableStoneCutter && hasPerm(player, "leafinventory.stonecutter")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.STONECUTTER, MenuType.STONECUTTER));
            return;
        }

        if (type == Material.CARTOGRAPHY_TABLE && cfg.enableCartographyTable && hasPerm(player, "leafinventory.cartographytable")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.CARTOGRAPHY, MenuType.CARTOGRAPHY_TABLE));
            return;
        }

        if (type == Material.LOOM && cfg.enableLoom && hasPerm(player, "leafinventory.loom")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.LOOM, MenuType.LOOM));
            return;
        }

        if (type == Material.SMITHING_TABLE && cfg.enableSmithingTable && hasPerm(player, "leafinventory.smithingtable")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.SMITHING, MenuType.SMITHING));
            return;
        }

        if (type == Material.GRINDSTONE && cfg.enableGrindstone && hasPerm(player, "leafinventory.grindstone")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.GRINDSTONE, MenuType.GRINDSTONE));
            return;
        }

        if (isAnvil(type) && cfg.enableAnvil && hasPerm(player, "leafinventory.anvil")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openMenuByCreateIfNotAlready(player, InventoryType.ANVIL, MenuType.ANVIL));
            return;
        }

        if (type == Material.FURNACE && cfg.enableFurnace && hasPerm(player, "leafinventory.furnace")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (workstationManager != null) workstationManager.openFurnace(player);
            });
            return;
        }

        if (type == Material.BLAST_FURNACE && cfg.enableBlastFurnace && hasPerm(player, "leafinventory.blastfurnace")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (workstationManager != null) workstationManager.openBlastFurnace(player);
            });
            return;
        }

        if (type == Material.SMOKER && cfg.enableSmoker && hasPerm(player, "leafinventory.smoker")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (workstationManager != null) workstationManager.openSmoker(player);
            });
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean hasPerm(Player p, String perm) {
        return !cfg.usePermissions || p.hasPermission(perm);
    }

    private boolean isAnvil(Material m) {
        return m == Material.ANVIL || m == Material.CHIPPED_ANVIL || m == Material.DAMAGED_ANVIL;
    }

    /**
     * Open a MenuType via MenuType#create(player, Component) if not already open.
     * If the same menu is already open, close it (toggle behavior).
     */
    private void openMenuByCreateIfNotAlready(HumanEntity player, InventoryType legacyType, MenuType menuType) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getType() == legacyType) {
            player.closeInventory();
            return;
        }
        try {
            InventoryView view = menuType.create(player, Component.empty());
            player.openInventory(view);
        } catch (Throwable ignored) {
            // If create fails (unlikely on Paper 1.21.1), we simply do nothing here.
        }
    }

    private void showCraftingTable(HumanEntity player) {
        try {
            InventoryView view = MenuType.CRAFTING.create(player, Component.empty());
            player.openInventory(view);
        } catch (Throwable t) {
            // Fallback for compatibility
            player.openWorkbench(player.getLocation(), false);
        }
    }

    private void showEnchantingTable(HumanEntity player) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getType() == InventoryType.ENCHANTING) {
            player.closeInventory();
            return;
        }
        try {
            InventoryView view = MenuType.ENCHANTMENT.create(player, Component.empty());
            player.openInventory(view);
        } catch (Throwable t) {
            player.openEnchanting(player.getLocation(), false);
        }
    }

    private void showEnderChest(HumanEntity player) {
        if (player.getOpenInventory() != null && player.getOpenInventory().getType() == InventoryType.ENDER_CHEST) {
            player.closeInventory();
            Player p = Bukkit.getPlayer(player.getUniqueId());
            if (p != null) p.playSound(player, Sound.BLOCK_ENDER_CHEST_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.2f);
        } else {
            player.openInventory(player.getEnderChest());
            Player p = Bukkit.getPlayer(player.getUniqueId());
            if (p != null) p.playSound(player, Sound.BLOCK_ENDER_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);
        }
    }
}
