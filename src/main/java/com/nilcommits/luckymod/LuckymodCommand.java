package com.nilcommits.luckymod;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LuckymodCommand implements CommandExecutor, TabCompleter {

    private final LuckymodPlugin plugin;

    public LuckymodCommand(LuckymodPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give":
                return give(sender, args);
            case "reload":
                return reload(sender);
            default:
                help(sender);
                return true;
        }
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("luckymod.give")) {
            plugin.msg(sender, "&cYou don't have permission to do that.");
            return true;
        }

        Player target;
        int index;
        if (args.length >= 2 && Bukkit.getPlayerExact(args[1]) != null) {
            target = Bukkit.getPlayerExact(args[1]);
            index = 2;
        } else if (sender instanceof Player) {
            target = (Player) sender;
            index = 1;
        } else {
            plugin.msg(sender, "&cUsage: /luckymod give <player> [amount] [luck]");
            return true;
        }

        int amount = 1;
        int luck = 0;
        try {
            if (args.length > index) {
                amount = Integer.parseInt(args[index]);
            }
            if (args.length > index + 1) {
                luck = Integer.parseInt(args[index + 1]);
            }
        } catch (NumberFormatException ex) {
            plugin.msg(sender, "&cAmount and luck must be whole numbers.");
            return true;
        }

        amount = Math.max(1, Math.min(amount, 2304));
        luck = Math.max(-100, Math.min(100, luck));

        ItemStack item = plugin.manager().createItem(luck, amount);
        target.getInventory().addItem(item).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));

        plugin.msg(sender, "&aGave &f" + amount + "&a lucky block(s) with luck &f" + luck + "&a to &f" + target.getName() + "&a.");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("luckymod.reload")) {
            plugin.msg(sender, "&cYou don't have permission to do that.");
            return true;
        }
        plugin.manager().reloadSettings();
        plugin.manager().registerRecipe();
        plugin.msg(sender, "&aConfiguration reloaded.");
        return true;
    }

    private void help(CommandSender sender) {
        plugin.msg(sender, "&6Luckymod &7v" + plugin.getDescription().getVersion());
        plugin.msg(sender, "&f/luckymod give [player] [amount] [luck] &7- give lucky blocks");
        plugin.msg(sender, "&f/luckymod reload &7- reload config.yml");
        plugin.msg(sender, "&7Place a lucky block and break it for a random outcome.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 0) {
            return out;
        }
        if (args.length == 1) {
            out.add("give");
            out.add("reload");
            out.add("help");
        } else if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    out.add(player.getName());
                }
            } else if (args.length == 3) {
                out.add("1");
                out.add("8");
                out.add("16");
            } else if (args.length == 4) {
                out.add("0");
                out.add("80");
                out.add("-80");
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(entry -> !entry.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
