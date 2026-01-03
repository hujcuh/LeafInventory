package me.LeafPixel.LeafInventory.shulker;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;         
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;


/**
 * Low-level helper for reading/writing shulker contents from/to an ItemStack.
 *
 * This class contains ONLY item IO logic and does not manage sessions.
 * Keeping it separate makes the system easier to test and maintain.
 */
public final class ShulkerItemIO {

    private ShulkerItemIO() { }

    /**
     * True if material is any color of shulker box.
     * (Kept explicit for performance and clarity.)
     */
    public static boolean isShulkerBox(Material m) {
        return switch (m) {
            case SHULKER_BOX,
                 WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX, GRAY_SHULKER_BOX,
                 LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }

    /**
     * Reads the shulker snapshot inventory from an ItemStack.
     * Returns null if the item is not a valid shulker ItemStack.
     */
    public static Inventory readSnapshot(ItemMeta meta) {
        if (!(meta instanceof BlockStateMeta bsm)) return null;
        if (!(bsm.getBlockState() instanceof ShulkerBox sb)) return null;
        return sb.getSnapshotInventory();
    }

    /**
     * Writes UI contents into the shulker box BlockStateMeta and returns an updated ItemStack.
     *
     * NOTE:
     * - We do not mutate the input item directly; we clone and return a new item.
     * - Caller is responsible for placing the returned item back into the player's slot.
     */
    public static org.bukkit.inventory.ItemStack writeContents(org.bukkit.inventory.ItemStack base,
                                                               ItemStack[] contents,
                                                               java.util.function.Consumer<ItemMeta> metaMutator) {
        if (base == null) return null;

        org.bukkit.inventory.ItemStack updated = base.clone();
        ItemMeta im = updated.getItemMeta();
        if (!(im instanceof BlockStateMeta bsm)) return null;
        if (!(bsm.getBlockState() instanceof ShulkerBox sb)) return null;

        // Apply contents
        sb.getInventory().setContents(contents);

        // Persist the block state back into meta
        bsm.setBlockState(sb);

        // Apply extra meta changes (token remove etc.)
        if (metaMutator != null) metaMutator.accept(bsm);

        updated.setItemMeta(bsm);
        return updated;
    }

    /**
     * Gets the adventure title (custom item name) if present, else null.
     */
    public static Component getDisplayTitle(ItemMeta meta) {
        if (meta == null) return null;
        return meta.displayName();
    }

    /**
     * Writes a session token into item meta PDC.
     */
    public static void setToken(ItemMeta meta, org.bukkit.NamespacedKey key, UUID editId) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key, PersistentDataType.STRING, editId.toString());
    }

    /**
     * Reads a session token from item meta PDC.
     */
    public static UUID getToken(ItemMeta meta, org.bukkit.NamespacedKey key) {
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Removes a session token from item meta PDC.
     */
    public static void clearToken(ItemMeta meta, org.bukkit.NamespacedKey key) {
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(key);
    }
}
