
package me.LeafPixel.LeafInventory.shulker;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Represents a single "edit transaction" for a player's shulker box.
 *
 * The session is created when opening a shulker UI and is removed after
 * commit or rollback.
 *
 * IMPORTANT:
 * - We store the slot index of the *real* item in the player's inventory.
 *   This is the key to avoiding scanning and avoiding wrong write-backs.
 * - We store a unique editId token (UUID) that is also written into the
 *   item PDC. On close, we verify token before committing.
 */
public final class ShulkerEditSession {

    public enum State {
        OPENING,
        OPEN,
        CLOSING
    }

    public final UUID playerId;

    /** The slot in the player's inventory that holds the shulker item being edited. */
    public final int slot;

    /** Unique token written into the shulker ItemStack PDC for this session. */
    public final UUID editId;

    /** The top inventory (SHULKER_BOX UI) we opened for editing. */
    public final Inventory ui;

    /** Snapshot of UI contents captured on close (used for commit or rollback). */
    public ItemStack[] closeSnapshot;

    /** Session lifecycle state to prevent double-close/commit. */
    public State state = State.OPENING;

    public ShulkerEditSession(UUID playerId, int slot, UUID editId, Inventory ui) {
        this.playerId = playerId;
        this.slot = slot;
        this.editId = editId;
        this.ui = ui;
    }
}
