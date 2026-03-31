package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.inventory.view.FurnaceView;

import java.util.UUID;

public final class VirtualWorkstationSession {
    private final UUID playerId;
    private final VirtualWorkstationType type;
    private final FurnaceView view;

    public VirtualWorkstationSession(UUID playerId, VirtualWorkstationType type, FurnaceView view) {
        this.playerId = playerId;
        this.type = type;
        this.view = view;
    }

    public UUID playerId() {
        return playerId;
    }

    public VirtualWorkstationType type() {
        return type;
    }

    public FurnaceView view() {
        return view;
    }
}
