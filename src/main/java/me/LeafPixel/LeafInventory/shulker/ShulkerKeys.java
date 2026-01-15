package me.LeafPixel.LeafInventory.shulker;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Holds NamespacedKeys used by the shulker feature.
 */
public final class ShulkerKeys {

    private final NamespacedKey lockTokenKey;
    private final NamespacedKey lockOwnerKey;
    private final NamespacedKey lockTimeKey;

    public ShulkerKeys(JavaPlugin plugin) {
        this.lockTokenKey = new NamespacedKey(plugin, "li_shulker_lock_token");
        this.lockOwnerKey = new NamespacedKey(plugin, "li_shulker_lock_owner");
        this.lockTimeKey  = new NamespacedKey(plugin, "li_shulker_lock_time");
    }

    public NamespacedKey lockTokenKey() { return lockTokenKey; }
    public NamespacedKey lockOwnerKey() { return lockOwnerKey; }
    public NamespacedKey lockTimeKey()  { return lockTimeKey; }
}
