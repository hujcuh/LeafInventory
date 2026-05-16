package me.LeafPixel.LeafInventory.largeshulker;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Holder used to identify LeafInventory's 54-slot large shulker GUI.
 */
public final class LargeShulkerHolder implements InventoryHolder {

    private final UUID shulkerId;
    private Inventory inventory;

    public LargeShulkerHolder(UUID shulkerId) {
        this.shulkerId = shulkerId;
    }

    public UUID shulkerId() {
        return shulkerId;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}