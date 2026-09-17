package com.nilcommits.luckymod;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public final class LuckymodListener implements Listener {

    private final LuckymodPlugin plugin;

    public LuckymodListener(LuckymodPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().hasPermission("luckymod.use")) {
            return;
        }
        ItemStack item = event.getItemInHand();
        if (!plugin.manager().isLuckyItem(item)) {
            return;
        }
        plugin.manager().track(event.getBlockPlaced().getLocation(), plugin.manager().luckOfItem(item));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!plugin.manager().isTracked(loc)) {
            return;
        }
        int luck = plugin.manager().luckAt(loc);
        plugin.manager().untrack(loc);
        event.setDropItems(false);

        Player player = event.getPlayer();
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.manager().trigger(player, center, luck));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        untrackAll(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        untrackAll(event.blockList());
    }

    private void untrackAll(Iterable<Block> blocks) {
        for (Block block : blocks) {
            Location loc = block.getLocation();
            if (plugin.manager().isTracked(loc)) {
                plugin.manager().untrack(loc);
            }
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!plugin.manager().upgradesEnabled()) {
            return;
        }
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        if (matrix == null) {
            return;
        }

        ItemStack lucky = null;
        int delta = 0;
        boolean sawBlockMaterial = false;
        boolean invalid = false;

        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.getType() == plugin.manager().blockMaterial()) {
                sawBlockMaterial = true;
                if (plugin.manager().isLuckyItem(item)) {
                    if (lucky != null) {
                        invalid = true;
                    } else {
                        lucky = item;
                    }
                } else {
                    invalid = true; // a plain block used where a lucky block is required
                }
            } else if (plugin.manager().isCatalyst(item.getType())) {
                delta += plugin.manager().upgradeDelta(item.getType()) * item.getAmount();
            } else {
                invalid = true;
            }
        }

        if (!sawBlockMaterial) {
            return; // not an upgrade craft - leave vanilla/other recipes alone
        }
        if (invalid || lucky == null) {
            inventory.setResult(null);
            return;
        }

        int newLuck = Math.max(-100, Math.min(100, plugin.manager().luckOfItem(lucky) + delta));
        inventory.setResult(plugin.manager().createItem(newLuck, 1));
    }
}
