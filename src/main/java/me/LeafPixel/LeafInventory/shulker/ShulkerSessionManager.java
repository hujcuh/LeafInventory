package me.LeafPixel.LeafInventory.shulker;

import me.LeafPixel.LeafInventory.config.LeafConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages shulker edit sessions.
 *
 * Safety features:
 * - One active session per player.
 * - PDC token verification on commit.
 * - Rollback on any mismatch to prevent item loss.
 *
 * Performance features:
 * - Records the inventory slot, so no scanning is needed on close.
 * - Cooldown (ticks) to reduce spam-click open requests.
 */
public final class ShulkerSessionManager {

    private final JavaPlugin plugin;
    private final LeafConfig cfg;

    /** PDC key used to mark the shulker ItemStack being edited. */
    private final NamespacedKey tokenKey;

    /** Active sessions per player UUID. */
    private final Map<UUID, ShulkerEditSession> sessions = new HashMap<>();

    /** Simple open spam cooldown: player UUID -> last open tick. */
    private final Map<UUID, Long> lastOpenTick = new HashMap<>();

    public ShulkerSessionManager(JavaPlugin plugin, LeafConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.tokenKey = new NamespacedKey(plugin, "__shulker_edit_id");
    }

    public NamespacedKey tokenKey() {
        return tokenKey;
    }

    public ShulkerEditSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean isEditing(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * Attempts to open a shulker edit session from a player's inventory slot.
     *
     * @return true if a session was created and UI opened, false otherwise.
     */
    public boolean tryOpenFromSlot(Player player, int slot) {
        UUID pid = player.getUniqueId();

        // Hard rule: one session at a time per player.
        if (isEditing(pid)) return false;

        // Cooldown: reduce spam-click queueing.
        if (!cooldownPassed(pid)) return false;

        ItemStack real = player.getInventory().getItem(slot);
        if (real == null || real.getType().isAir()) return false;
        if (real.getAmount() != 1) return false; // same as your original constraint
        if (!ShulkerItemIO.isShulkerBox(real.getType())) return false;

        ItemMeta meta0 = real.getItemMeta();
        if (meta0 == null) return false;

        // Read snapshot inventory from real item meta
        Inventory snap = ShulkerItemIO.readSnapshot(meta0);
        if (snap == null) return false;

        // Create a new edit token
        UUID editId = UUID.randomUUID();

        // Clone item, write token into meta, and put back to the REAL slot.
        ItemStack locked = real.clone();
        ItemMeta lockedMeta = locked.getItemMeta();
        ShulkerItemIO.setToken(lockedMeta, tokenKey, editId);
        locked.setItemMeta(lockedMeta);
        player.getInventory().setItem(slot, locked);

        // Create UI inventory with optional title
        Component title = ShulkerItemIO.getDisplayTitle(lockedMeta);
        Inventory ui = (title == null)
                ? Bukkit.createInventory(null, InventoryType.SHULKER_BOX)
                : Bukkit.createInventory(null, InventoryType.SHULKER_BOX, title);

        ui.setContents(snap.getContents());

        ShulkerEditSession session = new ShulkerEditSession(pid, slot, editId, ui);
        session.state = ShulkerEditSession.State.OPEN;
        sessions.put(pid, session);

        player.openInventory(ui);
        player.playSound(player, Sound.BLOCK_SHULKER_BOX_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);

        return true;
    }

    /**
     * Called when an inventory close event happens.
     *
     * IMPORTANT:
     * - Only handles the close if the closed inventory is exactly the session UI instance.
     * - Captures the UI snapshot and commits next tick.
     */
    public void handleClose(Player player, Inventory closedInventory) {
        UUID pid = player.getUniqueId();
        ShulkerEditSession s = sessions.get(pid);
        if (s == null) return;

        // Only process if the closed inventory is the session UI we opened.
        if (closedInventory != s.ui) return;

        if (s.state == ShulkerEditSession.State.CLOSING) return;
        s.state = ShulkerEditSession.State.CLOSING;

        // Capture snapshot immediately from the closed inventory instance.
        s.closeSnapshot = closedInventory.getContents().clone();

        // Commit on next tick to avoid event ordering issues (close caused by another openInventory, etc.)
        Bukkit.getScheduler().runTask(plugin, () -> commitOrRollback(player, s));
    }

    /**
     * Force-closes/commits when player quits/kicks/dies.
     * Safe because commitOrRollback is token-verified and will rollback on mismatch.
     */
    public void forceClose(Player player) {
        ShulkerEditSession s = sessions.get(player.getUniqueId());
        if (s == null) return;

        // If UI is still open, its top inventory may be s.ui. Capture it safely.
        Inventory top = player.getOpenInventory() != null ? player.getOpenInventory().getTopInventory() : null;
        if (top == s.ui) {
            s.closeSnapshot = top.getContents().clone();
        } else if (s.closeSnapshot == null) {
            // If we cannot access UI contents, we do nothing. This is rare, but safe.
            s.closeSnapshot = new ItemStack[0];
        }

        s.state = ShulkerEditSession.State.CLOSING;
        Bukkit.getScheduler().runTask(plugin, () -> commitOrRollback(player, s));
    }

    // ----------------- internal commit/rollback -----------------

    private void commitOrRollback(Player player, ShulkerEditSession s) {
        UUID pid = player.getUniqueId();

        try {
            // Session might have been replaced/removed; ensure current session matches.
            ShulkerEditSession current = sessions.get(pid);
            if (current != s) return;

            ItemStack inSlot = player.getInventory().getItem(s.slot);

            // If the item moved or disappeared -> rollback to avoid losing items.
            if (inSlot == null || inSlot.getType().isAir() || inSlot.getAmount() != 1 || !ShulkerItemIO.isShulkerBox(inSlot.getType())) {
                rollbackToPlayer(player, s.closeSnapshot);
                return;
            }

            ItemMeta meta = inSlot.getItemMeta();
            UUID token = ShulkerItemIO.getToken(meta, tokenKey);

            // Token mismatch means we must NOT write back, otherwise we might dupe/overwrite wrong item.
            if (token == null || !token.equals(s.editId)) {
                rollbackToPlayer(player, s.closeSnapshot);
                return;
            }

            // Safe commit: write contents into blockstate and remove token.
            ItemStack updated = ShulkerItemIO.writeContents(inSlot, s.closeSnapshot, m -> ShulkerItemIO.clearToken(m, tokenKey));
            if (updated == null) {
                // Could not write; rollback rather than risk item loss.
                rollbackToPlayer(player, s.closeSnapshot);
                return;
            }

            player.getInventory().setItem(s.slot, updated);
            player.playSound(player, Sound.BLOCK_SHULKER_BOX_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.2f);

        } finally {
            // Always end session to avoid stuck locks.
            sessions.remove(pid);
        }
    }

    /**
     * Rollback = give UI contents back to the player (or drop if inventory full).
     *
     * This is critical to prevent "items disappear" cases when the target item
     * cannot be safely located or validated.
     */
    private void rollbackToPlayer(Player player, ItemStack[] contents) {
        if (contents == null) return;

        for (ItemStack it : contents) {
            if (it == null || it.getType().isAir()) continue;

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(it);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            }
        }
    }

    // ----------------- cooldown -----------------

    private boolean cooldownPassed(UUID pid) {
        int cd = Math.max(0, cfg.shulkerCooldownTicks);
        if (cd == 0) return true;

        long nowTick = Bukkit.getCurrentTick();
        Long last = lastOpenTick.get(pid);

        if (last != null && (nowTick - last) < cd) {
            return false;
        }
        lastOpenTick.put(pid, nowTick);
        return true;
    }
}
