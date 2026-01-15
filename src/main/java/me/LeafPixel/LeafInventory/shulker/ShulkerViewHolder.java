package me.LeafPixel.LeafInventory.shulker;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder for LeafInventory virtual shulker GUI.
 * This allows us to reliably identify our GUI in close/click events.
 */
public final class ShulkerViewHolder implements InventoryHolder {

    private final UUID sessionId;

    public ShulkerViewHolder(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    @Override
    public Inventory getInventory() {
        return null; // Not used; inventory is created by Bukkit.createInventory(...)
    }
}
