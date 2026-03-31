package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class VirtualWorkstationHolder implements InventoryHolder {
    private final UUID owner;
    private final VirtualWorkstationType type;

    public VirtualWorkstationHolder(UUID owner, VirtualWorkstationType type) {
        this.owner = owner;
        this.type = type;
    }

    public UUID owner() {
        return owner;
    }

    public VirtualWorkstationType type() {
        return type;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
