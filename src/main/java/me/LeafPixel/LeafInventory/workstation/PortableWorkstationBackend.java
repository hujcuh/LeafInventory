package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Abstract backend for portable workstation features.
 * - Paper backend can use hidden real blocks/world.
 * - Future Folia backend can use fully virtual workstation data.
 */
public interface PortableWorkstationBackend {

    /**
     * Initialize backend from plugin config.
     */
    void initFromConfig();

    /**
     * Open furnace-like workstation for the player.
     */
    void openFurnace(Player player);

    /**
     * Open blast furnace-like workstation for the player.
     */
    void openBlastFurnace(Player player);

    /**
     * Open smoker-like workstation for the player.
     */
    void openSmoker(Player player);

    /**
     * Clear all workstation data for a player.
     */
    void clearAll(UUID uuid);

    /**
     * Return guard world if this backend uses a hidden world.
     * Future virtual/Folia backend can return null.
     */
    default World getGuardWorld() {
        return null;
    }

    /**
     * Called on plugin disable for final flush / cleanup.
     */
    default void shutdown() {
        // no-op
    }
}
