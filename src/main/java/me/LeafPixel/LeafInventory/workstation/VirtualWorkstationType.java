package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.event.inventory.InventoryType;

public enum VirtualWorkstationType {
    FURNACE(InventoryType.FURNACE, "Furnace"),
    BLAST_FURNACE(InventoryType.BLAST_FURNACE, "Blast Furnace"),
    SMOKER(InventoryType.SMOKER, "Smoker");

    private final InventoryType inventoryType;
    private final String displayName;

    VirtualWorkstationType(InventoryType inventoryType, String displayName) {
        this.inventoryType = inventoryType;
        this.displayName = displayName;
    }

    public InventoryType inventoryType() {
        return inventoryType;
    }

    public String displayName() {
        return displayName;
    }
}