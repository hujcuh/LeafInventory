package me.LeafPixel.LeafInventory.enderchest;

import me.LeafPixel.LeafInventory.util.Scheduler;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles right-clicking placed ender chests.
 *
 * If Large Ender Chest is enabled and the player has permission,
 * opens the 54-slot virtual GUI.
 *
 * Otherwise LargeEnderChestService.openOrFallback(...) opens vanilla 27-slot.
 */
public final class PlacedEnderChestListener implements Listener {

    private final JavaPlugin plugin;
    private final LargeEnderChestService service;

    public PlacedEnderChestListener(JavaPlugin plugin, LargeEnderChestService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClickPlacedEnderChest(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        if (event.getClickedBlock().getType() != Material.ENDER_CHEST) {
            return;
        }

        Player player = event.getPlayer();

        /*
         * Take over the placed ender chest interaction.
         * This prevents vanilla 27-slot GUI from opening first.
         */
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);

        Scheduler.runEntity(plugin, player, () -> {
            service.openOrFallback(player);
        });
    }
}
