package com.nilcommits.luckymod;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckymodPlugin extends JavaPlugin {

    private NamespacedKey keyId;
    private NamespacedKey keyLuck;
    private LuckyBlockManager manager;

    @Override
    public void onEnable() {
        keyId = new NamespacedKey(this, "lucky_block");
        keyLuck = new NamespacedKey(this, "lucky_luck");

        manager = new LuckyBlockManager(this);
        manager.load();
        manager.registerRecipe();

        getServer().getPluginManager().registerEvents(new LuckymodListener(this), this);

        LuckymodCommand command = new LuckymodCommand(this);
        if (getCommand("luckymod") != null) {
            getCommand("luckymod").setExecutor(command);
            getCommand("luckymod").setTabCompleter(command);
        }

        getLogger().info("Luckymod v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.save();
        }
    }

    public LuckyBlockManager manager() {
        return manager;
    }

    public NamespacedKey keyId() {
        return keyId;
    }

    public NamespacedKey keyLuck() {
        return keyLuck;
    }

    public String prefix() {
        return getConfig().getString("prefix", "");
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public void msg(CommandSender sender, String message) {
        sender.sendMessage(color(prefix() + message));
    }
}
