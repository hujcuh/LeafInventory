package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 *   implementation is intentionally disabled or not ready yet.
 *   Future Folia backend can replace this class.
 */
public final class NoopWorkstationBackend implements PortableWorkstationBackend {

    @Override
    public void initFromConfig() {
        // No-op
    }

    @Override
    public void openFurnace(Player player) {
        // No-op
    }

    @Override
    public void openBlastFurnace(Player player) {
        // No-op
    }

    @Override
    public void openSmoker(Player player) {
        // No-op
    }

    @Override
    public void clearAll(UUID uuid) {
        // No-op
    }
}