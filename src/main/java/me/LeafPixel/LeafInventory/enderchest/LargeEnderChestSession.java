package me.LeafPixel.LeafInventory.enderchest;

import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Runtime session for an opened large ender chest.
 */
public final class LargeEnderChestSession {

    private final UUID playerId;
    private final Inventory inventory;
    private final long openedAt;

    public LargeEnderChestSession(UUID playerId, Inventory inventory) {
        this.playerId = playerId;
        this.inventory = inventory;
        this.openedAt = System.currentTimeMillis();
    }

    public UUID playerId() {
        return playerId;
    }

    public Inventory inventory() {
        return inventory;
    }

    public long openedAt() {
        return openedAt;
    }
}
