package me.LeafPixel.LeafInventory.workstation;

import me.LeafPixel.LeafInventory.util.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.view.FurnaceView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FoliaVirtualWorkstationBackend implements PortableWorkstationBackend, Listener {
    private final JavaPlugin plugin;
    private final VirtualWorkstationStore store;

    private final ConcurrentMap<UUID, EnumMap<VirtualWorkstationType, VirtualWorkstationData>> playerData = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, VirtualWorkstationSession> sessions = new ConcurrentHashMap<>();

    public FoliaVirtualWorkstationBackend(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new VirtualWorkstationStore(plugin);
    }

    @Override
    public void initFromConfig() {
        store.load();
        playerData.clear();
        playerData.putAll(store.readAll());

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Tick all virtual workstations globally.
        Scheduler.runGlobalTimer(plugin, 1L, 1L, this::tickAll);

        // Periodic async save (every 30 seconds).
        Scheduler.runGlobalTimer(plugin, 20L * 30L, 20L * 30L, () -> {
            Map<UUID, EnumMap<VirtualWorkstationType, VirtualWorkstationData>> snapshot = snapshotAll();
            Scheduler.runAsync(plugin, () -> store.writeAll(snapshot));
        });
    }

    @Override
    public void openFurnace(Player player) {
        open(player, VirtualWorkstationType.FURNACE);
    }

    @Override
    public void openBlastFurnace(Player player) {
        open(player, VirtualWorkstationType.BLAST_FURNACE);
    }

    @Override
    public void openSmoker(Player player) {
        open(player, VirtualWorkstationType.SMOKER);
    }

    @Override
    public void clearAll(UUID uuid) {
        sessions.remove(uuid);
        playerData.remove(uuid);
    }

    @Override
    public void shutdown() {
        store.writeAll(snapshotAll());
    }

    private void open(Player player, VirtualWorkstationType type) {
        UUID uuid = player.getUniqueId();
        VirtualWorkstationData data = getOrCreateData(uuid, type);
        data.touch();

        FurnaceView view = createView(player, type);
        FurnaceInventory inv = view.getTopInventory();

        // Use typed FurnaceInventory accessors.
        inv.setSmelting(cloneOrNull(data.getInput()));
        inv.setFuel(cloneOrNull(data.getFuel()));
        inv.setResult(cloneOrNull(data.getOutput()));

        applyViewProgress(view, data);

        sessions.put(uuid, new VirtualWorkstationSession(uuid, type, view));
        player.openInventory(view);
    }

    private FurnaceView createView(Player player, VirtualWorkstationType type) {
        Component title = Component.text(type.displayName());

        return switch (type) {
            case FURNACE -> MenuType.FURNACE.create(player, title);
            case BLAST_FURNACE -> MenuType.BLAST_FURNACE.create(player, title);
            case SMOKER -> MenuType.SMOKER.create(player, title);
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        VirtualWorkstationSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!(e.getView() instanceof FurnaceView closingView)) return;
        if (!matchesViewType(session.type(), closingView)) return;

        // Sync after Bukkit/Paper has applied the click result.
        Scheduler.runEntityLater(plugin, player, 1L, () -> syncViewToData(player.getUniqueId(), session.type(), closingView));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        VirtualWorkstationSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!(e.getView() instanceof FurnaceView closingView)) return;
        if (!matchesViewType(session.type(), closingView)) return;

        Scheduler.runEntityLater(plugin, player, 1L, () -> syncViewToData(player.getUniqueId(), session.type(), closingView));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        VirtualWorkstationSession session = sessions.get(uuid);
        if (session == null) return;

        if (!(e.getView() instanceof FurnaceView closingView)) return;
        if (!matchesViewType(session.type(), closingView)) return;

        syncViewToData(uuid, session.type(), closingView);
        sessions.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        saveAndRemoveSession(e.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent e) {
        saveAndRemoveSession(e.getPlayer());
    }

    private void saveAndRemoveSession(Player player) {
        UUID uuid = player.getUniqueId();
        VirtualWorkstationSession session = sessions.remove(uuid);
        if (session == null) return;

        syncViewToData(uuid, session.type(), session.view());
    }

    private boolean matchesViewType(VirtualWorkstationType type, FurnaceView view) {
        return switch (type) {
            case FURNACE -> view.getType() == org.bukkit.event.inventory.InventoryType.FURNACE;
            case BLAST_FURNACE -> view.getType() == org.bukkit.event.inventory.InventoryType.BLAST_FURNACE;
            case SMOKER -> view.getType() == org.bukkit.event.inventory.InventoryType.SMOKER;
        };
    }

    private void syncViewToData(UUID uuid, VirtualWorkstationType type, FurnaceView view) {
        VirtualWorkstationData data = getOrCreateData(uuid, type);
        FurnaceInventory inv = view.getTopInventory();

        data.setInput(inv.getSmelting());
        data.setFuel(inv.getFuel());
        data.setOutput(inv.getResult());
        data.touch();
    }

    private VirtualWorkstationData getOrCreateData(UUID uuid, VirtualWorkstationType type) {
        EnumMap<VirtualWorkstationType, VirtualWorkstationData> typed =
                playerData.computeIfAbsent(uuid, k -> new EnumMap<>(VirtualWorkstationType.class));
        return typed.computeIfAbsent(type, k -> new VirtualWorkstationData());
    }

    private void tickAll() {
        // Tick open sessions using the live FurnaceView as the source of truth.
        for (VirtualWorkstationSession session : sessions.values()) {
            tickView(session.type(), session.view(), session.playerId());
        }

        // Tick closed workstation data directly.
        for (var playerEntry : playerData.entrySet()) {
            UUID uuid = playerEntry.getKey();
            if (sessions.containsKey(uuid)) continue;

            for (var typedEntry : playerEntry.getValue().entrySet()) {
                tickData(typedEntry.getKey(), typedEntry.getValue());
            }
        }
    }

    private void tickView(VirtualWorkstationType type, FurnaceView view, UUID owner) {
        FurnaceInventory inv = view.getTopInventory();

        // Read the current live view state first.
        VirtualWorkstationData temp = new VirtualWorkstationData();
        temp.setInput(inv.getSmelting());
        temp.setFuel(inv.getFuel());
        temp.setOutput(inv.getResult());

        // Carry over timing state from persistent backend data.
        VirtualWorkstationData base = getOrCreateData(owner, type);
        temp.setBurnTimeRemaining(base.getBurnTimeRemaining());
        temp.setBurnTimeTotal(base.getBurnTimeTotal());
        temp.setCookTime(base.getCookTime());
        temp.setCookTimeTotal(base.getCookTimeTotal());

        // Tick the temporary live state.
        tickData(type, temp);

        // Write the live result back into the view.
        inv.setSmelting(cloneOrNull(temp.getInput()));
        inv.setFuel(cloneOrNull(temp.getFuel()));
        inv.setResult(cloneOrNull(temp.getOutput()));
        applyViewProgress(view, temp);

        // Persist it back to backend data.
        base.setInput(temp.getInput());
        base.setFuel(temp.getFuel());
        base.setOutput(temp.getOutput());
        base.setBurnTimeRemaining(temp.getBurnTimeRemaining());
        base.setBurnTimeTotal(temp.getBurnTimeTotal());
        base.setCookTime(temp.getCookTime());
        base.setCookTimeTotal(temp.getCookTimeTotal());
        base.touch();
    }

    private void applyViewProgress(FurnaceView view, VirtualWorkstationData data) {
        int burnProgress = Math.max(0, data.getBurnTimeRemaining());
        int burnDuration = Math.max(1, data.getBurnTimeTotal());

        int cookProgress = Math.max(0, data.getCookTime());
        int cookDuration = Math.max(1, data.getCookTimeTotal());

        // FurnaceView is the modern Paper API for furnace progress bars.
        view.setBurnTime(burnProgress, burnDuration);
        view.setCookTime(cookProgress, cookDuration);
    }

    private int burnRateFor(VirtualWorkstationType type) {
        return switch (type) {
            case FURNACE -> 1;
            case BLAST_FURNACE, SMOKER -> 2;
        };
    }

    private void tickData(VirtualWorkstationType type, VirtualWorkstationData data) {
        ItemStack input = data.getInput();
        if (input == null || input.getType() == Material.AIR) {
            data.setCookTime(0);
            return;
        }

        ResolvedCookingRecipe recipe = resolveRecipe(type, input);
        if (recipe == null) {
            data.setCookTime(0);
            return;
        }

        if (!canOutput(recipe.result(), data.getOutput())) {
            data.setCookTime(0);
            return;
        }

        // Blast furnace and smoker: 2x speed, 2x fuel consumption rate in vanilla.
        int burnRate = burnRateFor(type);

        if (data.getBurnTimeRemaining() <= 0) {
            int fuelTime = getFuelTime(data.getFuel());
            if (fuelTime > 0) {
                consumeOneFuel(data);
                data.setBurnTimeRemaining(fuelTime);
                data.setBurnTimeTotal(fuelTime);
            }
        }

        if (data.getBurnTimeRemaining() > 0) {
            data.setBurnTimeRemaining(Math.max(0, data.getBurnTimeRemaining() - burnRate));

            data.setCookTimeTotal(recipe.cookTime());
            data.setCookTime(data.getCookTime() + 1);

            if (data.getCookTime() >= data.getCookTimeTotal()) {
                craftOne(data, recipe.result());
                data.setCookTime(0);
            }
        } else {
            data.setCookTime(0);
        }
    }

    private ResolvedCookingRecipe resolveRecipe(VirtualWorkstationType type, ItemStack input) {
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();

            if (type == VirtualWorkstationType.FURNACE && !(recipe instanceof FurnaceRecipe)) continue;
            if (type == VirtualWorkstationType.BLAST_FURNACE && !(recipe instanceof BlastingRecipe)) continue;
            if (type == VirtualWorkstationType.SMOKER && !(recipe instanceof SmokingRecipe)) continue;
            if (!(recipe instanceof CookingRecipe<?> cooking)) continue;

            RecipeChoice choice = cooking.getInputChoice();
            if (choice == null) continue;
            if (!choice.test(input)) continue;

            return new ResolvedCookingRecipe(cooking.getResult().clone(), cooking.getCookingTime());
        }
        return null;
    }

    private boolean canOutput(ItemStack result, ItemStack output) {
        if (output == null || output.getType() == Material.AIR) return true;
        if (!output.isSimilar(result)) return false;
        return output.getAmount() + result.getAmount() <= output.getMaxStackSize();
    }

    private void craftOne(VirtualWorkstationData data, ItemStack result) {
        ItemStack input = data.getInput();
        if (input == null || input.getType() == Material.AIR) return;

        input = input.clone();
        input.setAmount(input.getAmount() - 1);
        data.setInput(input.getAmount() <= 0 ? null : input);

        ItemStack output = data.getOutput();
        if (output == null || output.getType() == Material.AIR) {
            data.setOutput(result.clone());
        } else {
            output = output.clone();
            output.setAmount(output.getAmount() + result.getAmount());
            data.setOutput(output);
        }
    }

    private void consumeOneFuel(VirtualWorkstationData data) {
        ItemStack fuel = data.getFuel();
        if (fuel == null || fuel.getType() == Material.AIR) return;

        Material originalType = fuel.getType();

        fuel = fuel.clone();
        fuel.setAmount(fuel.getAmount() - 1);

        // Lava bucket -> empty bucket
        if (originalType == Material.LAVA_BUCKET) {
            data.setFuel(new ItemStack(Material.BUCKET));
            return;
        }

        data.setFuel(fuel.getAmount() <= 0 ? null : fuel);
    }

    private int getFuelTime(ItemStack fuel) {
        if (fuel == null || fuel.getType() == Material.AIR) return 0;

        return switch (fuel.getType()) {
            case COAL, CHARCOAL -> 1600;
            case COAL_BLOCK -> 16000;
            case BLAZE_ROD -> 2400;
            case LAVA_BUCKET -> 20000;

            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG,
                 MANGROVE_LOG, CHERRY_LOG, PALE_OAK_LOG,
                 CRIMSON_STEM, WARPED_STEM,
                 BAMBOO_BLOCK -> 300;

            case STICK -> 100;
            default -> 0;
        };
    }

    private Map<UUID, EnumMap<VirtualWorkstationType, VirtualWorkstationData>> snapshotAll() {
        Map<UUID, EnumMap<VirtualWorkstationType, VirtualWorkstationData>> copy = new ConcurrentHashMap<>();
        for (var playerEntry : playerData.entrySet()) {
            EnumMap<VirtualWorkstationType, VirtualWorkstationData> typed = new EnumMap<>(VirtualWorkstationType.class);
            for (var typeEntry : playerEntry.getValue().entrySet()) {
                typed.put(typeEntry.getKey(), typeEntry.getValue().copy());
            }
            copy.put(playerEntry.getKey(), typed);
        }
        return copy;
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    private record ResolvedCookingRecipe(ItemStack result, int cookTime) {
    }
}
