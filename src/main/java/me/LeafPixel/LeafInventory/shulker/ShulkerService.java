package me.LeafPixel.LeafInventory.shulker;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.block.ShulkerBox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public final class ShulkerService {

    private final JavaPlugin plugin;
    private final ShulkerKeys keys;
    private final Map<UUID, ShulkerSession> sessions = new HashMap<>();

    public ShulkerService(JavaPlugin plugin, ShulkerKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    public JavaPlugin plugin() { return plugin; }

    public boolean isSessionOpen(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public ShulkerSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public void requestOpenFromPlayerInventory(Player player, int slot) {
        if (isSessionOpen(player.getUniqueId())) return;
        ItemStack carrier = player.getInventory().getItem(slot);
        if (!isSingleShulker(carrier)) return;
        openInternal(player, carrier, new ShulkerSession.CarrierBinding(ShulkerSession.CarrierBinding.Type.PLAYER_SLOT, slot));
    }

    public void requestOpenFromMainHand(Player player) {
        if (isSessionOpen(player.getUniqueId())) return;
        ItemStack carrier = player.getInventory().getItemInMainHand();
        if (!isSingleShulker(carrier)) return;
        openInternal(player, carrier, new ShulkerSession.CarrierBinding(ShulkerSession.CarrierBinding.Type.MAIN_HAND, -1));
    }

    public void handleClose(Player player, Inventory closedInventory) {
        ShulkerSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        if (!(closedInventory.getHolder() instanceof ShulkerViewHolder holder)) return;
        if (!holder.sessionId().equals(session.sessionId)) return;

        commit(player, session, closedInventory);
    }

    /**
     * Force abort if a session exists (quit/kick/death).
     * @param refundVirtual if true, will refund virtual inventory contents to player
     */
    public void forceAbortIfOpen(Player player, String reason, boolean refundVirtual) {
        ShulkerSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        Inventory inv = (refundVirtual ? session.virtualInventory : null);
        abort(player, session, reason, inv);
    }

    // ------------------------------------------------------------
    // Internal: open/commit/abort
    // ------------------------------------------------------------
    private void openInternal(Player player, ItemStack carrier, ShulkerSession.CarrierBinding binding) {
        UUID playerId = player.getUniqueId();

        ItemMeta meta = carrier.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) return;
        if (!(bsm.getBlockState() instanceof ShulkerBox box)) return;

        // Lock check (with optional TTL)
        PersistentDataContainer pdc = bsm.getPersistentDataContainer();
        String existing = pdc.get(keys.lockTokenKey(), PersistentDataType.STRING);

        if (existing != null && !existing.isEmpty()) {
            long maxAgeSec = plugin.getConfig().getLong("shulker.lockMaxAgeSeconds", 120);
            Long lockTime = pdc.get(keys.lockTimeKey(), PersistentDataType.LONG);

            boolean expired = false;
            if (maxAgeSec > 0 && lockTime != null) {
                long age = System.currentTimeMillis() - lockTime;
                expired = age > (maxAgeSec * 1000L);
            }

            if (expired) {
                // auto-heal old lock
                pdc.remove(keys.lockTokenKey());
                pdc.remove(keys.lockOwnerKey());
                pdc.remove(keys.lockTimeKey());
                carrier.setItemMeta(bsm);
                applyCarrierToBinding(player, binding, carrier);
            } else {
                player.sendMessage("§c该潜影盒正在被使用，或锁未释放（请稍后再试）。");
                return;
            }
        }

        UUID sessionId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        pdc.set(keys.lockTokenKey(), PersistentDataType.STRING, lockToken.toString());
        pdc.set(keys.lockOwnerKey(), PersistentDataType.STRING, playerId.toString());
        pdc.set(keys.lockTimeKey(), PersistentDataType.LONG, System.currentTimeMillis());
        carrier.setItemMeta(bsm);
        applyCarrierToBinding(player, binding, carrier);

        ShulkerSession session = new ShulkerSession(
                playerId,
                sessionId,
                lockToken,
                binding,
                carrier.clone()
        );

        Component title = bsm.displayName();
        if (title == null) title = Component.text("Shulker Box", NamedTextColor.WHITE);

        Inventory gui = Bukkit.createInventory(
                new ShulkerViewHolder(sessionId),
                InventoryType.SHULKER_BOX,
                title
        );

        gui.setContents(box.getInventory().getContents());

        session.virtualInventory = gui;
        session.state = ShulkerSession.State.OPEN;
        sessions.put(playerId, session);

        player.openInventory(gui);
        player.playSound(player, Sound.BLOCK_SHULKER_BOX_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);
    }

    private void commit(Player player, ShulkerSession session, Inventory guiInventory) {
        session.state = ShulkerSession.State.COMMITTING;

        ItemStack carrier = findCarrierBySession(player, session);
        if (carrier == null) {
            abort(player, session, "carrier_not_found", guiInventory);
            return;
        }

        ItemMeta meta = carrier.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) {
            abort(player, session, "not_block_state_meta", guiInventory);
            return;
        }
        if (!(bsm.getBlockState() instanceof ShulkerBox box)) {
            abort(player, session, "not_shulker_state", guiInventory);
            return;
        }

        box.getInventory().setContents(guiInventory.getContents());
        bsm.setBlockState(box);

        // release locks
        bsm.getPersistentDataContainer().remove(keys.lockTokenKey());
        bsm.getPersistentDataContainer().remove(keys.lockOwnerKey());
        bsm.getPersistentDataContainer().remove(keys.lockTimeKey());

        carrier.setItemMeta(bsm);
        applyCarrierToBinding(player, session.binding, carrier);

        sessions.remove(player.getUniqueId());
        player.playSound(player, Sound.BLOCK_SHULKER_BOX_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.2f);
    }

    private void abort(Player player, ShulkerSession session, String reason, Inventory guiInventoryOrNull) {
        session.state = ShulkerSession.State.ABORTED;

        // try to release lock
        ItemStack carrier = findCarrierBySession(player, session);
        if (carrier != null) {
            ItemMeta meta = carrier.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.remove(keys.lockTokenKey());
                pdc.remove(keys.lockOwnerKey());
                pdc.remove(keys.lockTimeKey());
                carrier.setItemMeta(meta);
                applyCarrierToBinding(player, session.binding, carrier);
            }
        }

        if (guiInventoryOrNull != null) {
            refundInventoryToPlayer(player, guiInventoryOrNull);
        }

        sessions.remove(player.getUniqueId());
        plugin.getLogger().warning("[LeafInventory] Shulker session aborted: player=" + player.getName() + " reason=" + reason);
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    private ItemStack findCarrierBySession(Player player, ShulkerSession session) {
        ItemStack bound = getCarrierFromBinding(player, session.binding);
        if (bound != null && hasLockToken(bound, session.lockToken)) return bound;

        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null) continue;
            if (!isShulkerMaterial(it.getType())) continue;
            if (hasLockToken(it, session.lockToken)) return it;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && hasLockToken(hand, session.lockToken)) return hand;

        return null;
    }

    private ItemStack getCarrierFromBinding(Player player, ShulkerSession.CarrierBinding binding) {
        if (binding.type() == ShulkerSession.CarrierBinding.Type.MAIN_HAND) {
            return player.getInventory().getItemInMainHand();
        }
        if (binding.type() == ShulkerSession.CarrierBinding.Type.PLAYER_SLOT) {
            return player.getInventory().getItem(binding.slot());
        }
        return null;
    }

    private void applyCarrierToBinding(Player player, ShulkerSession.CarrierBinding binding, ItemStack carrier) {
        if (binding.type() == ShulkerSession.CarrierBinding.Type.MAIN_HAND) {
            player.getInventory().setItemInMainHand(carrier);
        } else if (binding.type() == ShulkerSession.CarrierBinding.Type.PLAYER_SLOT) {
            player.getInventory().setItem(binding.slot(), carrier);
        }
    }

    private boolean hasLockToken(ItemStack stack, UUID lockToken) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        String token = meta.getPersistentDataContainer().get(keys.lockTokenKey(), PersistentDataType.STRING);
        return token != null && token.equals(lockToken.toString());
    }

    private static boolean isSingleShulker(ItemStack stack) {
        return stack != null && stack.getAmount() == 1 && isShulkerMaterial(stack.getType());
    }

    private static boolean isShulkerMaterial(Material m) {
        return switch (m) {
            case SHULKER_BOX,
                 WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX, GRAY_SHULKER_BOX,
                 LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }

    private void refundInventoryToPlayer(Player player, Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> left = player.getInventory().addItem(item);
            if (!left.isEmpty()) {
                left.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            }
        }
        inv.clear();
    }
}
