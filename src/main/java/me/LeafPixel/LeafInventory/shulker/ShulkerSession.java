package me.LeafPixel.LeafInventory.shulker;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Represents one active shulker session per player.
 */
public final class ShulkerSession {

    public enum State { OPENING, OPEN, COMMITTING, ABORTED }

    public final UUID playerId;
    public final UUID sessionId;

    // A stable UUID stored in PDC to locate the real carrier item.
    public final UUID lockToken;

    // Where the carrier item is expected to be.
    public final CarrierBinding binding;

    // Snapshot for fallback or debugging (do not trust as live reference).
    public final ItemStack originalCarrierSnapshot;

    // The inventory we opened for this session.
    public Inventory virtualInventory;

    public State state = State.OPENING;

    public ShulkerSession(UUID playerId,
                          UUID sessionId,
                          UUID lockToken,
                          CarrierBinding binding,
                          ItemStack originalCarrierSnapshot) {
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.lockToken = lockToken;
        this.binding = binding;
        this.originalCarrierSnapshot = originalCarrierSnapshot;
    }

    /**
     * Carrier binding describes how to locate the backing shulker item.
     */
    public record CarrierBinding(Type type, int slot) {
        public enum Type { MAIN_HAND, PLAYER_SLOT }
    }
}
