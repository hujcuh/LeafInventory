package me.LeafPixel.LeafInventory.largeshulker;

import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Runtime session for an opened large shulker box.
 */
public final class LargeShulkerSession {

    private final UUID playerId;
    private final UUID shulkerId;
    private final Inventory inventory;
    private final SourceBinding sourceBinding;
    private final long openedAt;

    public LargeShulkerSession(
            UUID playerId,
            UUID shulkerId,
            Inventory inventory,
            SourceBinding sourceBinding
    ) {
        this.playerId = playerId;
        this.shulkerId = shulkerId;
        this.inventory = inventory;
        this.sourceBinding = sourceBinding;
        this.openedAt = System.currentTimeMillis();
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID shulkerId() {
        return shulkerId;
    }

    public Inventory inventory() {
        return inventory;
    }

    public SourceBinding sourceBinding() {
        return sourceBinding;
    }

    public long openedAt() {
        return openedAt;
    }

    public record SourceBinding(Type type, int slot, String locationKey) {

        /*
         * Compatibility constructor:
         * existing code using new SourceBinding(Type.MAIN_HAND, -1)
         * or new SourceBinding(Type.PLAYER_SLOT, slot) will still compile.
         */
        public SourceBinding(Type type, int slot) {
            this(type, slot, null);
        }

        public static SourceBinding mainHand() {
            return new SourceBinding(Type.MAIN_HAND, -1, null);
        }

        public static SourceBinding playerSlot(int slot) {
            return new SourceBinding(Type.PLAYER_SLOT, slot, null);
        }

        public static SourceBinding block(String locationKey) {
            return new SourceBinding(Type.BLOCK, -1, locationKey);
        }

        public enum Type {
            MAIN_HAND,
            PLAYER_SLOT,
            BLOCK
        }
    }
}