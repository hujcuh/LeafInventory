package me.LeafPixel.LeafInventory.enderchest;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder used to identify LeafInventory's large ender chest GUI.
 */
public final class LargeEnderChestHolder implements InventoryHolder {

    private final UUID owner;
    private Inventory inventory;

    public LargeEnderChestHolder(UUID owner) {
        this.owner = owner;
    }

    public UUID owner() {
        return owner;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
