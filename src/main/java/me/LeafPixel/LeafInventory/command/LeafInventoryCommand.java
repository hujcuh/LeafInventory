package me.LeafPixel.LeafInventory.command;

import me.LeafPixel.LeafInventory.LeafInventory;
import me.LeafPixel.LeafInventory.enderchest.LargeEnderChestService;
import me.LeafPixel.LeafInventory.largeshulker.LargeShulkerService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LeafInventoryCommand implements CommandExecutor, TabCompleter {

    private final LeafInventory plugin;
    private final LargeEnderChestService largeEnderChestService;
    private final LargeShulkerService largeShulkerService;

    public LeafInventoryCommand(
            LeafInventory plugin,
            LargeEnderChestService largeEnderChestService,
            LargeShulkerService largeShulkerService
    ) {
        this.plugin = plugin;
        this.largeEnderChestService = largeEnderChestService;
        this.largeShulkerService = largeShulkerService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("leafinventory.admin")) {
            sender.sendMessage("§c你没有权限使用 LeafInventory 管理命令。");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "status" -> handleStatus(sender);
            case "save" -> handleSave(sender);
            case "largeshulker" -> handleLargeShulker(sender, label, args);
            case "largeender" -> handleLargeEnder(sender, label, args);
            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§aLeafInventory 管理命令:");
        sender.sendMessage("§7/" + label + " status §f- 查看插件状态");
        sender.sendMessage("§7/" + label + " save §f- 立即保存大容量数据");
        sender.sendMessage("§7/" + label + " largeshulker info §f- 查看手上或准星方块的大容量潜影盒");
        sender.sendMessage("§7/" + label + " largeshulker listplaced §f- 查看已放置大容量潜影盒索引");
        sender.sendMessage("§7/" + label + " largeshulker unlock <shulkerId> §f- 强制解锁大容量潜影盒");
        sender.sendMessage("§7/" + label + " largeender info [player] §f- 查看大容量末影箱状态");
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage("§aLeafInventory Status:");
        sender.sendMessage("§7Version: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Large Ender Chest: §f" + enabledText(largeEnderChestService != null && largeEnderChestService.isEnabled()));
        sender.sendMessage("§7Large Shulker: §f" + enabledText(largeShulkerService != null && largeShulkerService.isEnabled()));

        if (largeShulkerService != null) {
            sender.sendMessage("§7Active Large Shulker Sessions: §f" + largeShulkerService.activeSessionCount());
            sender.sendMessage("§7Placed Large Shulkers: §f" + largeShulkerService.placedCount());
        }
    }

    private void handleSave(CommandSender sender) {
        if (largeEnderChestService != null) {
            largeEnderChestService.flushNow();
        }

        if (largeShulkerService != null) {
            largeShulkerService.flushNow();
            largeShulkerService.savePlacedNow();
        }

        sender.sendMessage("§aLeafInventory 数据已同步保存。");
    }

    private void handleLargeShulker(CommandSender sender, String label, String[] args) {
        if (largeShulkerService == null) {
            sender.sendMessage("§cLargeShulkerService 未初始化。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /" + label + " largeshulker <info|unlock|listplaced>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "info" -> handleLargeShulkerInfo(sender);
            case "unlock" -> handleLargeShulkerUnlock(sender, label, args);
            case "listplaced" -> handleLargeShulkerListPlaced(sender);
            default -> sender.sendMessage("§c用法: /" + label + " largeshulker <info|unlock|listplaced>");
        }
    }

    private void handleLargeShulkerInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行。");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        UUID itemId = largeShulkerService.getShulkerId(hand);

        if (itemId != null) {
            sender.sendMessage("§aLarge Shulker Info:");
            sender.sendMessage("§7Source: §fMAIN_HAND");
            sender.sendMessage("§7ShulkerId: §f" + itemId);
            sender.sendMessage("§7Owner: §f" + nullText(largeShulkerService.getOwner(hand)));
            sender.sendMessage("§7Active: §f" + largeShulkerService.isShulkerActive(itemId));
            return;
        }

        Block target = player.getTargetBlockExact(6);

        if (target == null) {
            sender.sendMessage("§c主手和准星方块都不是大容量潜影盒。");
            return;
        }

        UUID blockId = largeShulkerService.getShulkerId(target);

        if (blockId == null) {
            sender.sendMessage("§c主手和准星方块都不是大容量潜影盒。");
            return;
        }

        sender.sendMessage("§aLarge Shulker Info:");
        sender.sendMessage("§7Source: §fBLOCK");
        sender.sendMessage("§7ShulkerId: §f" + blockId);
        sender.sendMessage("§7Location: §f" + LargeShulkerService.locationKey(target.getLocation()));
        sender.sendMessage("§7PlacedIndexValid: §f" + largeShulkerService.isValidPlacedLocation(target.getLocation(), blockId));
        sender.sendMessage("§7Active: §f" + largeShulkerService.isShulkerActive(blockId));
    }

    private void handleLargeShulkerUnlock(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c用法: /" + label + " largeshulker unlock <shulkerId>");
            return;
        }

        UUID shulkerId;

        try {
            shulkerId = UUID.fromString(args[2]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c无效的 UUID: " + args[2]);
            return;
        }

        boolean unlocked = largeShulkerService.forceUnlock(shulkerId);

        if (unlocked) {
            sender.sendMessage("§a已强制解锁大容量潜影盒: §f" + shulkerId);
        } else {
            sender.sendMessage("§e该大容量潜影盒当前没有 active lock: §f" + shulkerId);
        }
    }

    private void handleLargeShulkerListPlaced(CommandSender sender) {
        Map<String, UUID> placed = largeShulkerService.placedSnapshot();

        sender.sendMessage("§aPlaced Large Shulkers: §f" + placed.size());

        int shown = 0;

        for (var entry : placed.entrySet()) {
            sender.sendMessage("§7- §f" + entry.getKey() + " §7-> §f" + entry.getValue());
            shown++;

            if (shown >= 10) {
                int remaining = placed.size() - shown;

                if (remaining > 0) {
                    sender.sendMessage("§7... 还有 §f" + remaining + " §7条未显示");
                }

                break;
            }
        }
    }

    private void handleLargeEnder(CommandSender sender, String label, String[] args) {
        if (largeEnderChestService == null) {
            sender.sendMessage("§cLargeEnderChestService 未初始化。");
            return;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("info")) {
            sender.sendMessage("§c用法: /" + label + " largeender info [player]");
            return;
        }

        Player target;

        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);

            if (target == null) {
                sender.sendMessage("§c玩家不在线: " + args[2]);
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c控制台使用时需要指定玩家: /" + label + " largeender info <player>");
                return;
            }

            target = player;
        }

        UUID uuid = target.getUniqueId();

        sender.sendMessage("§aLarge Ender Chest Info:");
        sender.sendMessage("§7Player: §f" + target.getName());
        sender.sendMessage("§7Enabled: §f" + largeEnderChestService.isEnabled());
        sender.sendMessage("§7CanUseLarge: §f" + largeEnderChestService.canUse(target));
        sender.sendMessage("§7SessionOpen: §f" + largeEnderChestService.isSessionOpen(uuid));
        sender.sendMessage("§7ExtraSlotsUsed: §f" + largeEnderChestService.countExtraUsed(uuid) + " / 27");
    }

    private static String enabledText(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private static String nullText(Object value) {
        return value == null ? "null" : value.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("leafinventory.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("status", "save", "largeshulker", "largeender"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("largeshulker")) {
            return filter(List.of("info", "unlock", "listplaced"), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("largeender")) {
            return filter(List.of("info"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("largeender") && args[1].equalsIgnoreCase("info")) {
            List<String> names = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }

            return filter(names, args[2]);
        }

        return List.of();
    }

    private static List<String> filter(List<String> source, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();

        for (String value : source) {
            if (value.toLowerCase().startsWith(lower)) {
                result.add(value);
            }
        }

        return result;
    }
}
