package me.LeafPixel.LeafInventory.largeshulker;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PersistentDataContainer keys used by 54-slot large shulker boxes.
 */
public final class LargeShulkerKeys {

    private final NamespacedKey idKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey rowsKey;
    private final NamespacedKey versionKey;
    private final NamespacedKey createdAtKey;

    public LargeShulkerKeys(JavaPlugin plugin) {
        this.idKey = new NamespacedKey(plugin, "li_large_shulker_id");
        this.ownerKey = new NamespacedKey(plugin, "li_large_shulker_owner");
        this.rowsKey = new NamespacedKey(plugin, "li_large_shulker_rows");
        this.versionKey = new NamespacedKey(plugin, "li_large_shulker_version");
        this.createdAtKey = new NamespacedKey(plugin, "li_large_shulker_created_at");
    }

    public NamespacedKey idKey() {
        return idKey;
    }

    public NamespacedKey ownerKey() {
        return ownerKey;
    }

    public NamespacedKey rowsKey() {
        return rowsKey;
    }

    public NamespacedKey versionKey() {
        return versionKey;
    }

    public NamespacedKey createdAtKey() {
        return createdAtKey;
    }
}