package me.LeafPixel.LeafInventory.largeshulker;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Persistent data for a 54-slot large shulker box.
 *
 * Data is bound to shulkerId, not to a player.
 */
public final class LargeShulkerData {

    public static final int DEFAULT_ROWS = 6;
    public static final int DEFAULT_SIZE = DEFAULT_ROWS * 9;

    private final UUID shulkerId;
    private UUID owner;
    private int rows;
    private ItemStack[] items;
    private long createdAt;
    private long lastAccessTime;

    public LargeShulkerData(UUID shulkerId, UUID owner, int rows) {
        this.shulkerId = shulkerId;
        this.owner = owner;
        this.rows = Math.max(1, Math.min(6, rows));
        this.items = new ItemStack[this.rows * 9];
        this.createdAt = System.currentTimeMillis();
        this.lastAccessTime = this.createdAt;
    }

    public UUID shulkerId() {
        return shulkerId;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public int rows() {
        return rows;
    }

    public void setRows(int rows) {
        int normalized = Math.max(1, Math.min(6, rows));

        if (this.rows == normalized) {
            return;
        }

        ItemStack[] old = this.items;
        this.rows = normalized;
        this.items = new ItemStack[normalized * 9];

        if (old != null) {
            int len = Math.min(old.length, this.items.length);
            for (int i = 0; i < len; i++) {
                this.items[i] = cloneOrNull(old[i]);
            }
        }
    }

    public int size() {
        return rows * 9;
    }

    public ItemStack[] items() {
        return cloneArray(items, size());
    }

    public void setItems(ItemStack[] source) {
        this.items = cloneArray(source, size());
    }

    public ItemStack itemAt(int slot) {
        if (slot < 0 || slot >= size()) {
            return null;
        }

        return cloneOrNull(items[slot]);
    }

    public void setItemAt(int slot, ItemStack item) {
        if (slot < 0 || slot >= size()) {
            return;
        }

        items[slot] = cloneOrNull(item);
    }

    public long createdAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long lastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public void touch() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    public LargeShulkerData copy() {
        LargeShulkerData copy = new LargeShulkerData(shulkerId, owner, rows);
        copy.setItems(items);
        copy.setCreatedAt(createdAt);
        copy.setLastAccessTime(lastAccessTime);
        return copy;
    }

    private static ItemStack[] cloneArray(ItemStack[] input, int size) {
        ItemStack[] out = new ItemStack[size];

        if (input == null) {
            return out;
        }

        int len = Math.min(input.length, size);

        for (int i = 0; i < len; i++) {
            out[i] = cloneOrNull(input[i]);
        }

        return out;
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}