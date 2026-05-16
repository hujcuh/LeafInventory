package me.LeafPixel.LeafInventory.largeshulker;

import me.LeafPixel.LeafInventory.util.Scheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Handles placed large shulker block lifecycle.
 *
 * Design:
 * - The placed block is only an entry point / shell.
 * - Real 54-slot contents are stored by LargeShulkerStore using shulkerId.
 * - Vanilla shulker inventory must not be used as the source of truth.
 */
public final class LargeShulkerBlockListener implements Listener {

    private final JavaPlugin plugin;
    private final LargeShulkerService service;

    public LargeShulkerBlockListener(JavaPlugin plugin, LargeShulkerService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /**
     * ItemStack -> placed block.
     *
     * Copies large shulker PDC from item to TileState and clears vanilla contents.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        ItemStack item = event.getItemInHand();
        UUID shulkerId = service.getShulkerId(item);

        if (shulkerId == null) {
            return;
        }

        if (!isPlacementEnabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c大容量潜影盒当前不允许放置。");
            return;
        }

        Block block = event.getBlockPlaced();

        if (!LargeShulkerService.isShulkerMaterial(block.getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c大容量潜影盒放置失败：方块类型异常。");
            return;
        }

        String currentLocationKey = LargeShulkerService.locationKey(block.getLocation());
        String existingLocationKey = service.findPlacedLocationByShulkerId(shulkerId);

        /*
         * Duplicate placed block detection:
         * If this shulkerId is already registered at another location, treat this
         * placed block as a duplicate copy.
         */
        if (existingLocationKey != null && !existingLocationKey.equals(currentLocationKey)) {
            if (convertDuplicatePlacedBlocksToVanilla()) {
                service.convertBlockToVanilla(block);
                event.getPlayer().sendMessage("§c检测到重复的大容量潜影盒方块，已转为普通潜影盒。");
                return;
            }

            event.setCancelled(true);
            event.getPlayer().sendMessage("§c检测到重复的大容量潜影盒方块，已阻止放置。");
            return;
        }

        if (!(block.getState() instanceof TileState tile)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c大容量潜影盒放置失败：无法写入方块数据。");
            return;
        }

        service.copyItemPdcToBlock(item, tile);

        /*
         * The vanilla 27-slot inventory is not the source of truth.
         * Keep it empty to avoid hopper/vanilla desync.
         */
        if (tile instanceof ShulkerBox box) {
            box.getInventory().clear();
        }

        tile.update(true, false);

        service.recordPlaced(block.getLocation(), shulkerId);
    }

    /**
     * Open placed large shulker block as 54-slot virtual GUI.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClickBlock(PlayerInteractEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();

        if (block == null) {
            return;
        }

        if (!LargeShulkerService.isShulkerMaterial(block.getType())) {
            return;
        }

        UUID shulkerId = service.getShulkerId(block);

        if (shulkerId == null) {
            /*
             * Normal vanilla shulker box.
             */
            return;
        }

        Player player = event.getPlayer();

        /*
         * Duplicate placed block detection:
         * TileState says it is a large shulker, but placed index does not
         * recognize this location as the valid one.
         */
        if (!service.isValidPlacedLocation(block.getLocation(), shulkerId)) {
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);

            if (convertDuplicatePlacedBlocksToVanilla()) {
                service.convertBlockToVanilla(block);
                player.sendMessage("§c检测到异常或重复的大容量潜影盒方块，已转为普通潜影盒。");
            } else {
                player.sendMessage("§c这个大容量潜影盒方块索引异常，已阻止打开。");
            }

            return;
        }

        /*
         * Take over vanilla shulker opening.
         */
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);

        Location location = block.getLocation();

        Scheduler.runEntity(plugin, player, () -> {
            service.openExistingFromBlock(player, shulkerId, location);
        });
    }

    /**
     * Break placed large shulker.
     *
     * Drops a new ItemStack carrying the same shulkerId.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        Block block = event.getBlock();

        if (!LargeShulkerService.isShulkerMaterial(block.getType())) {
            return;
        }

        UUID shulkerId = service.getShulkerId(block);

        if (shulkerId == null) {
            return;
        }

        event.setCancelled(true);
        event.setExpToDrop(0);

        dropLargeShulkerBlock(block);
    }

    /**
     * Prevent piston moving large shulker blocks.
     *
     * If pistonDropInsteadOfCancel is enabled, simulate vanilla-like behavior:
     * the large shulker is dropped as an item with the same shulkerId.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        if (!preventPistonMove()) {
            return;
        }

        boolean found = false;

        for (Block block : event.getBlocks()) {
            if (service.isLargeShulkerBlock(block)) {
                found = true;
                break;
            }
        }

        if (!found) {
            return;
        }

        event.setCancelled(true);

        if (!pistonDropInsteadOfCancel()) {
            return;
        }

        for (Block block : event.getBlocks()) {
            if (service.isLargeShulkerBlock(block)) {
                dropLargeShulkerBlock(block);
            }
        }
    }

    /**
     * Prevent piston pulling large shulker blocks.
     *
     * If pistonDropInsteadOfCancel is enabled, simulate vanilla-like behavior:
     * the large shulker is dropped as an item with the same shulkerId.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        if (!preventPistonMove()) {
            return;
        }

        boolean found = false;

        for (Block block : event.getBlocks()) {
            if (service.isLargeShulkerBlock(block)) {
                found = true;
                break;
            }
        }

        if (!found) {
            return;
        }

        event.setCancelled(true);

        if (!pistonDropInsteadOfCancel()) {
            return;
        }

        for (Block block : event.getBlocks()) {
            if (service.isLargeShulkerBlock(block)) {
                dropLargeShulkerBlock(block);
            }
        }
    }

    /**
     * Explosion handling.
     *
     * Remove large shulker blocks from vanilla explosion list and drop custom item.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        if (!handleExplosionDrop()) {
            return;
        }

        handleExplosionBlocks(event.blockList());
    }

    /**
     * Explosion handling for block explosions.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        if (!handleExplosionDrop()) {
            return;
        }

        handleExplosionBlocks(event.blockList());
    }

    /**
     * Prevent hopper interaction with large shulker blocks.
     *
     * Custom hopper logic is intentionally not implemented yet.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        if (!blockHopperInteraction()) {
            return;
        }

        if (isLargeShulkerInventory(event.getSource().getHolder())
                || isLargeShulkerInventory(event.getDestination().getHolder())) {
            event.setCancelled(true);
        }
    }

    /**
     * Conservative fire/burn protection.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (service.isLargeShulkerBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Conservative fade protection.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (service.isLargeShulkerBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private void handleExplosionBlocks(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();

        while (iterator.hasNext()) {
            Block block = iterator.next();

            if (!LargeShulkerService.isShulkerMaterial(block.getType())) {
                continue;
            }

            UUID shulkerId = service.getShulkerId(block);

            if (shulkerId == null) {
                continue;
            }

            iterator.remove();

            dropLargeShulkerBlock(block);
        }
    }

    private void dropLargeShulkerBlock(Block block) {
        UUID shulkerId = service.getShulkerId(block);

        if (shulkerId == null) {
            return;
        }

        Material type = block.getType();
        ItemStack drop = service.createItemFromId(shulkerId, type);

        Location dropLocation = block.getLocation().clone().add(0.5, 0.5, 0.5);
        World world = block.getWorld();

        world.dropItemNaturally(dropLocation, drop);

        service.removePlaced(block.getLocation());

        block.setType(Material.AIR);
    }

    private boolean isLargeShulkerInventory(InventoryHolder holder) {
        if (!(holder instanceof ShulkerBox box)) {
            return false;
        }

        if (!(box instanceof TileState tile)) {
            return false;
        }

        return service.getShulkerId(tile) != null;
    }

    private boolean isPlacementEnabled() {
        return plugin.getConfig().getBoolean("largeShulker.placement.enabled", true);
    }

    private boolean preventPistonMove() {
        return plugin.getConfig().getBoolean("largeShulker.placement.preventPistonMove", true);
    }

    private boolean pistonDropInsteadOfCancel() {
        return plugin.getConfig().getBoolean("largeShulker.placement.pistonDropInsteadOfCancel", true);
    }

    private boolean handleExplosionDrop() {
        return plugin.getConfig().getBoolean("largeShulker.placement.handleExplosionDrop", true);
    }

    private boolean blockHopperInteraction() {
        return plugin.getConfig().getBoolean("largeShulker.placement.blockHopperInteraction", true);
    }

    private boolean convertDuplicatePlacedBlocksToVanilla() {
        return plugin.getConfig().getBoolean(
                "largeShulker.placement.convertDuplicatePlacedBlocksToVanilla",
                true
        );
    }
}
