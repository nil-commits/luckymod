package com.nilcommits.luckymod;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.TreeType;
import org.bukkit.Difficulty;
import org.bukkit.DyeColor;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Tracks placed lucky blocks (by location) and rolls/applies outcomes.
 */
public final class LuckyBlockManager {

    private final LuckymodPlugin plugin;
    private final Map<String, Integer> tracked = new HashMap<>(); // location key -> luck
    private final List<Outcome> outcomes = new ArrayList<>();

    private File dataFile;
    private Material blockMaterial = Material.SPONGE;
    private boolean upgradesEnabled = true;
    private final Map<Material, Integer> upgradeDeltas = new LinkedHashMap<>();
    private static final int MAX_UPGRADE_CATALYSTS = 8;

    public LuckyBlockManager(LuckymodPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        dataFile = new File(plugin.getDataFolder(), "luckyblocks.yml");
        loadData();
        reloadSettings();
    }

    public void reloadSettings() {
        plugin.reloadConfig();
        outcomes.clear();

        String matName = plugin.getConfig().getString("block.material", "SPONGE");
        Material mat = Material.matchMaterial(matName == null ? "SPONGE" : matName);
        if (mat == null || !mat.isBlock() || !mat.isItem()) {
            plugin.getLogger().warning("Luckymod: invalid block.material '" + matName + "', using SPONGE.");
            mat = Material.SPONGE;
        }
        blockMaterial = mat;

        List<Map<?, ?>> list = plugin.getConfig().getMapList("outcomes");
        for (Map<?, ?> entry : list) {
            Outcome outcome = Outcome.parse(entry, plugin.getLogger());
            if (outcome != null) {
                outcomes.add(outcome);
            }
        }
        upgradeDeltas.clear();
        upgradesEnabled = plugin.getConfig().getBoolean("upgrades.enabled", true);
        ConfigurationSection catalysts = plugin.getConfig().getConfigurationSection("upgrades.catalysts");
        if (catalysts != null) {
            for (String key : catalysts.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null || !material.isItem()) {
                    plugin.getLogger().warning("Luckymod: invalid upgrade catalyst '" + key + "', skipping.");
                    continue;
                }
                if (material == blockMaterial) {
                    plugin.getLogger().warning("Luckymod: catalyst '" + key + "' is the lucky block material, skipping.");
                    continue;
                }
                Object raw = catalysts.get(key);
                if (raw instanceof Number) {
                    upgradeDeltas.put(material, ((Number) raw).intValue());
                }
            }
        }
        plugin.getLogger().info("Luckymod: loaded " + outcomes.size() + " outcome(s) and "
                + upgradeDeltas.size() + " upgrade catalyst(s).");
    }

    public void save() {
        if (dataFile == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tracked.entrySet()) {
            lines.add(entry.getKey() + ";" + entry.getValue());
        }
        yaml.set("blocks", lines);
        try {
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Luckymod: could not save luckyblocks.yml", ex);
        }
    }

    private void loadData() {
        tracked.clear();
        if (dataFile == null || !dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String line : yaml.getStringList("blocks")) {
            String[] p = line.split(";");
            if (p.length < 4) continue;
            try {
                int x = Integer.parseInt(p[1]);
                int y = Integer.parseInt(p[2]);
                int z = Integer.parseInt(p[3]);
                int luck = p.length > 4 ? Integer.parseInt(p[4]) : 0;
                tracked.put(key(p[0], x, y, z), luck);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    //  Tracking
    // ------------------------------------------------------------------

    private static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    private static String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public boolean isTracked(Location loc) {
        return tracked.containsKey(key(loc));
    }

    public int luckAt(Location loc) {
        return tracked.getOrDefault(key(loc), 0);
    }

    public void track(Location loc, int luck) {
        tracked.put(key(loc), luck);
        save();
    }

    public void untrack(Location loc) {
        if (tracked.remove(key(loc)) != null) {
            save();
        }
    }

    // ------------------------------------------------------------------
    //  Items
    // ------------------------------------------------------------------

    public boolean isLuckyItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        if (!stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(plugin.keyId(), PersistentDataType.BYTE);
    }

    public int luckOfItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return 0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0;
        Integer luck = meta.getPersistentDataContainer().get(plugin.keyLuck(), PersistentDataType.INTEGER);
        return luck == null ? 0 : luck;
    }

    public ItemStack createItem(int luck, int amount) {
        int clamped = Math.max(-100, Math.min(100, luck));
        ItemStack item = new ItemStack(blockMaterial, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = plugin.getConfig().getString("block.name", "&6Lucky Block");
            meta.setDisplayName(plugin.color(name));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.color("&7Luck: &f" + (clamped > 0 ? "+" : "") + clamped));
            lore.add(plugin.color("&7Place and break for a random outcome!"));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(plugin.keyId(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(plugin.keyLuck(), PersistentDataType.INTEGER, clamped);
            item.setItemMeta(meta);
        }
        return item;
    }

    public Material blockMaterial() {
        return blockMaterial;
    }

    // ------------------------------------------------------------------
    //  Recipe
    // ------------------------------------------------------------------

    public void registerRecipe() {
        registerUpgradeRecipes();

        NamespacedKey recipeKey = new NamespacedKey(plugin, "lucky_block");
        Bukkit.removeRecipe(recipeKey);

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("recipe");
        if (section == null || !section.getBoolean("enabled", true)) return;

        List<String> shape = section.getStringList("shape");
        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (shape.size() != 3 || ingredients == null) {
            plugin.getLogger().warning("Luckymod: recipe shape must have 3 rows and ingredients must be set; skipping recipe.");
            return;
        }

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createItem(0, 1));
        recipe.shape(shape.get(0), shape.get(1), shape.get(2));
        for (String symbol : ingredients.getKeys(false)) {
            Material material = Material.matchMaterial(String.valueOf(ingredients.get(symbol)));
            if (material == null || symbol.isEmpty()) {
                plugin.getLogger().warning("Luckymod: invalid recipe ingredient '" + symbol + "'; skipping recipe.");
                return;
            }
            recipe.setIngredient(symbol.charAt(0), material);
        }
        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalStateException ex) {
            plugin.getLogger().warning("Luckymod: could not register recipe: " + ex.getMessage());
        }
    }

    /**
     * Registers shapeless recipes (1 lucky block + 1..N catalysts). Recipe matching is by
     * Material only, so the actual validation (that the block really is a lucky block) and the
     * resulting luck are applied in PrepareItemCraftEvent - see LuckymodListener.
     */
    public void registerUpgradeRecipes() {
        for (int n = 1; n <= MAX_UPGRADE_CATALYSTS; n++) {
            Bukkit.removeRecipe(new NamespacedKey(plugin, "lucky_upgrade_" + n));
        }
        if (!upgradesEnabled || upgradeDeltas.isEmpty()) return;

        List<Material> choices = new ArrayList<>(upgradeDeltas.keySet());
        RecipeChoice catalystChoice = new RecipeChoice.MaterialChoice(choices);
        for (int n = 1; n <= MAX_UPGRADE_CATALYSTS; n++) {
            ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, "lucky_upgrade_" + n), createItem(0, 1));
            recipe.addIngredient(1, blockMaterial);
            for (int i = 0; i < n; i++) {
                recipe.addIngredient(catalystChoice);
            }
            try {
                Bukkit.addRecipe(recipe);
            } catch (IllegalStateException ex) {
                plugin.getLogger().warning("Luckymod: could not register upgrade recipe: " + ex.getMessage());
            }
        }
    }

    public boolean upgradesEnabled() {
        return upgradesEnabled;
    }

    public boolean isCatalyst(Material material) {
        return upgradeDeltas.containsKey(material);
    }

    public int upgradeDelta(Material material) {
        return upgradeDeltas.getOrDefault(material, 0);
    }

    // ------------------------------------------------------------------
    //  Triggering
    // ------------------------------------------------------------------

    public void trigger(Player player, Location loc, int luck) {
        if (outcomes.isEmpty()) return;
        Outcome outcome = pick(luck);
        if (outcome == null) return;

        if (!outcome.message.isEmpty()) {
            String message = outcome.message;
            if (player != null) {
                plugin.msg(player, message);
            } else {
                plugin.getServer().getConsoleSender().sendMessage(plugin.color(plugin.prefix() + message));
            }
        }
        apply(outcome, player, loc);
        playFeedback(outcome.category, loc);
    }

    private Outcome pick(int luck) {
        double[] effective = new double[outcomes.size()];
        double total = 0.0;
        for (int i = 0; i < outcomes.size(); i++) {
            Outcome outcome = outcomes.get(i);
            double factor = 1.0;
            if (outcome.category == LuckCategory.GOOD) {
                factor = Math.max(0.0, 1.0 + (luck / 100.0));
            } else if (outcome.category == LuckCategory.BAD) {
                factor = Math.max(0.0, 1.0 - (luck / 100.0));
            }
            effective[i] = outcome.weight * factor;
            total += effective[i];
        }
        if (total <= 0.0) {
            return outcomes.get(ThreadLocalRandom.current().nextInt(outcomes.size()));
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        double cumulative = 0.0;
        for (int i = 0; i < effective.length; i++) {
            cumulative += effective[i];
            if (roll < cumulative) return outcomes.get(i);
        }
        return outcomes.get(outcomes.size() - 1);
    }

    private void apply(Outcome outcome, Player player, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0.5, 0.5, 0.5);

        switch (outcome.type) {
            case ITEM: {
                for (ItemStack stack : buildItems(outcome)) {
                    if (outcome.launch) {
                        Item item = world.dropItem(center, stack);
                        if (item != null) {
                            item.setVelocity(new Vector(
                                    ThreadLocalRandom.current().nextDouble(-0.35, 0.35), 1.1,
                                    ThreadLocalRandom.current().nextDouble(-0.35, 0.35)));
                        }
                    } else {
                        world.dropItemNaturally(center, stack);
                    }
                }
                break;
            }
            case MOB: {
                spawnMobs(outcome, world, center, player);
                break;
            }
            case EFFECT: {
                if (player != null && outcome.effect != null) {
                    player.addPotionEffect(new PotionEffect(outcome.effect, outcome.duration * 20, outcome.amplifier));
                }
                break;
            }
            case TRAP: {
                applyTrap(outcome, player, world, loc, center);
                break;
            }
            case STRUCTURE: {
                applyStructure(outcome, player, world, loc);
                break;
            }
            case ACTION: {
                applyAction(outcome, player, world, loc);
                break;
            }
            case FALLING: {
                applyFalling(outcome, world, loc, center);
                break;
            }
            case APOCALYPSE: {
                applyApocalypse(outcome, player, world, center);
                break;
            }
            case SPECIAL: {
                applySpecial(outcome, player, world, loc, center);
                break;
            }
        }
    }

    // ------------------------------------------------------------------
    //  Items (pools, enchantments, custom names)
    // ------------------------------------------------------------------

    private List<ItemStack> buildItems(Outcome outcome) {
        List<ItemStack> results = new ArrayList<>();
        for (ItemStack fixed : outcome.items) {
            results.add(decorate(outcome, fixed.clone()));
        }
        if (!outcome.pool.isEmpty()) {
            int picks = outcome.minPick;
            if (outcome.maxPick > outcome.minPick) {
                picks = ThreadLocalRandom.current().nextInt(outcome.minPick, outcome.maxPick + 1);
            }
            for (int i = 0; i < picks; i++) {
                ItemStack chosen = outcome.pool.get(ThreadLocalRandom.current().nextInt(outcome.pool.size()));
                results.add(decorate(outcome, chosen.clone()));
            }
        }
        return results;
    }

    private ItemStack decorate(Outcome outcome, ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        if (outcome.name != null) {
            meta.setDisplayName(plugin.color(outcome.name));
        }
        if (!outcome.lore.isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : outcome.lore) lore.add(plugin.color(line));
            meta.setLore(lore);
        }
        applyEnchants(meta, stack, outcome);
        stack.setItemMeta(meta);
        return stack;
    }

    private void applyEnchants(ItemMeta meta, ItemStack stack, Outcome outcome) {
        for (Map.Entry<String, Integer> entry : outcome.enchants.entrySet()) {
            Enchantment enchantment = resolveEnchant(entry.getKey());
            if (enchantment != null) {
                meta.addEnchant(enchantment, entry.getValue(), true);
            }
        }
        if (outcome.randomEnchantments && outcome.enchantCount > 0) {
            List<Enchantment> applicable = new ArrayList<>();
            for (Enchantment enchantment : Enchantment.values()) {
                try {
                    if (enchantment.canEnchantItem(stack)) applicable.add(enchantment);
                } catch (Throwable ignored) {
                }
            }
            Collections.shuffle(applicable);
            int count = Math.min(outcome.enchantCount, applicable.size());
            for (int i = 0; i < count; i++) {
                int level = 1 + ThreadLocalRandom.current().nextInt(Math.max(1, outcome.enchantMaxLevel));
                meta.addEnchant(applicable.get(i), level, true);
            }
            meta.setEnchantmentGlintOverride(true);
        }
    }

    private Enchantment resolveEnchant(String name) {
        try {
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
            if (enchantment != null) return enchantment;
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Mobs
    // ------------------------------------------------------------------

    private void spawnMobs(Outcome outcome, World world, Location center, Player player) {
        int count = outcome.mobMin;
        if (outcome.mobMax > outcome.mobMin) {
            count = ThreadLocalRandom.current().nextInt(outcome.mobMin, outcome.mobMax + 1);
        }
        for (int i = 0; i < count; i++) {
            Location spawnAt = center.clone().add(
                    ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 0,
                    ThreadLocalRandom.current().nextDouble(-1.5, 1.5));
            try {
                Entity entity = world.spawnEntity(spawnAt, outcome.mob);
                configureMob(outcome, entity, player);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Luckymod: cannot spawn " + outcome.mob);
                break;
            }
        }
    }

    private void configureMob(Outcome outcome, Entity entity, Player player) {
        if (outcome.name != null) {
            entity.setCustomName(plugin.color(outcome.name));
            entity.setCustomNameVisible(true);
        }
        if (entity instanceof Slime slime && outcome.size > 0) {
            slime.setSize(outcome.size);
        }
        if (entity instanceof Creeper creeper && outcome.charged) {
            creeper.setPowered(true);
        }
        if (entity instanceof Ageable ageable && outcome.baby) {
            ageable.setBaby();
        }
        if (entity instanceof Rabbit rabbit && outcome.rabbitType != null) {
            try {
                rabbit.setRabbitType(Rabbit.Type.valueOf(outcome.rabbitType.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (entity instanceof Tameable tameable && outcome.tamed) {
            tameable.setTamed(true);
            if (player != null) {
                tameable.setOwner(player);
            }
        }
        if (entity instanceof Wolf wolf && outcome.collar != null) {
            try {
                wolf.setCollarColor(DyeColor.valueOf(outcome.collar.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (entity instanceof LivingEntity living && !outcome.equipment.isEmpty()) {
            for (Map.Entry<String, String> entry : outcome.equipment.entrySet()) {
                Material material = Material.matchMaterial(entry.getValue());
                if (material == null || !material.isItem()) continue;
                ItemStack stack = new ItemStack(material);
                switch (entry.getKey()) {
                    case "helmet": living.getEquipment().setHelmet(stack); break;
                    case "chest": living.getEquipment().setChestplate(stack); break;
                    case "legs": living.getEquipment().setLeggings(stack); break;
                    case "boots": living.getEquipment().setBoots(stack); break;
                    case "hand": living.getEquipment().setItemInMainHand(stack); break;
                    case "offhand": living.getEquipment().setItemInOffHand(stack); break;
                    default: break;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Falling blocks
    // ------------------------------------------------------------------

    private void applyFalling(Outcome outcome, World world, Location blockLoc, Location center) {
        Material material = Material.matchMaterial(outcome.fallingMaterial);
        if (material == null || !material.isBlock()) {
            plugin.getLogger().warning("Luckymod: invalid fallingMaterial '" + outcome.fallingMaterial + "'");
            return;
        }
        Location top = blockLoc.clone().add(0, outcome.height, 0);
        Material topMaterial = outcome.topMaterial == null ? null : Material.matchMaterial(outcome.topMaterial);

        for (int i = 0; i < outcome.fallingCount; i++) {
            double ox = outcome.spread > 0 ? ThreadLocalRandom.current().nextDouble(-outcome.spread, outcome.spread) : 0;
            double oz = outcome.spread > 0 ? ThreadLocalRandom.current().nextDouble(-outcome.spread, outcome.spread) : 0;
            try {
                FallingBlock falling = world.spawnFallingBlock(
                        top.clone().add(ox, -i, oz), material.createBlockData());
                falling.setDropItem(true);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (topMaterial != null && topMaterial.isBlock()) {
            try {
                world.spawnFallingBlock(top.clone().add(0, 1, 0), topMaterial.createBlockData());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (outcome.lightning) {
            world.strikeLightningEffect(center);
        }
    }

    // ------------------------------------------------------------------
    //  Apocalypse
    // ------------------------------------------------------------------

    private void applyApocalypse(Outcome outcome, Player player, World world, Location center) {
        if (outcome.apocHard) {
            world.setDifficulty(Difficulty.HARD);
        }
        if (outcome.apocMidnight) {
            world.setTime(18000L);
        }
        if (outcome.apocSword && player != null) {
            player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
        }
        if (outcome.apocSlowness && player != null && outcome.effect == null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30 * 20, 3));
        }
        if (outcome.apocBlindness && player != null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30 * 20, 0));
        }
        for (Outcome.MobGroup group : outcome.groups) {
            int count = ThreadLocalRandom.current().nextInt(group.min, group.max + 1);
            for (int i = 0; i < count; i++) {
                Location spawnAt = center.clone().add(
                        ThreadLocalRandom.current().nextDouble(-4, 4), 0,
                        ThreadLocalRandom.current().nextDouble(-4, 4));
                try {
                    world.spawnEntity(spawnAt, group.mob);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Special (hard-coded) events
    // ------------------------------------------------------------------

    private void applySpecial(Outcome outcome, Player player, World world, Location blockLoc, Location center) {
        switch (outcome.special) {
            case "SLIME_CASTLE":
                buildSlimeCastle(world, blockLoc);
                break;
            case "HERO_VILLAGER":
                spawnHeroVillager(world, center, outcome.preset);
                break;
            case "ARMOR_STAND_BABY":
                spawnBabyArmorStand(world, blockLoc, player);
                break;
            case "RAINBOW_SHEEP":
                spawnRainbowSheep(world, center, (int) Math.max(1, outcome.value > 0 ? outcome.value : 16));
                break;
            case "TNT_AIR_LAUNCH":
                launchTnt(world, center, (int) Math.max(1, outcome.value > 0 ? outcome.value : 15));
                break;
            case "WITCH_BATS":
                spawnWitchBats(world, center);
                break;
            case "MOB_STACK":
                spawnPigVillagerStack(world, blockLoc);
                break;
            case "TNT_TOWER":
                buildTntTower(world, blockLoc);
                break;
            case "TWO_BLOCKS": {
                world.dropItemNaturally(center, createItem(80, 1));
                world.dropItemNaturally(center, createItem(-80, 1));
                break;
            }
            case "BEDROCK_SIGN":
                buildBedrockSign(world, blockLoc);
                break;
            default:
                plugin.getLogger().warning("Luckymod: unknown special '" + outcome.special + "'");
                break;
        }
    }

    private void buildSlimeCastle(World world, Location origin) {
        Block base = origin.getBlock();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 6; dy++) {
                    boolean wall = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                    boolean floor = dy == 0;
                    if ((wall && dy <= 4) || floor) {
                        setIfReplaceable(world, base.getX() + dx, base.getY() + dy, base.getZ() + dz, Material.SLIME_BLOCK);
                    }
                }
            }
        }
    }

    private void spawnHeroVillager(World world, Location center, String preset) {
        Entity entity = world.spawnEntity(center, EntityType.VILLAGER);
        if (!(entity instanceof Villager villager)) return;
        villager.setProfession(Villager.Profession.WEAPONSMITH);
        List<MerchantRecipe> recipes = new ArrayList<>();
        String key = preset == null ? "WEAPONS" : preset;
        switch (key) {
            case "ARMOR":
                recipes.add(heroRecipe(new ItemStack(Material.DIAMOND_CHESTPLATE), Material.EMERALD, 8));
                recipes.add(heroRecipe(new ItemStack(Material.DIAMOND_LEGGINGS), Material.EMERALD, 7));
                recipes.add(heroRecipe(new ItemStack(Material.DIAMOND_BOOTS), Material.EMERALD, 5));
                recipes.add(heroRecipe(new ItemStack(Material.SHIELD), Material.EMERALD, 3));
                break;
            case "TOOLS":
                recipes.add(heroRecipe(new ItemStack(Material.DIAMOND_PICKAXE), Material.EMERALD, 6));
                recipes.add(heroRecipe(new ItemStack(Material.DIAMOND_AXE), Material.EMERALD, 6));
                recipes.add(heroRecipe(new ItemStack(Material.DIAMOND_SHOVEL), Material.EMERALD, 4));
                recipes.add(heroRecipe(new ItemStack(Material.DIAMOND_HOE), Material.EMERALD, 4));
                break;
            default:
                recipes.add(heroRecipe(new ItemStack(Material.DIAMOND_SWORD), Material.EMERALD, 7));
                recipes.add(heroRecipe(new ItemStack(Material.BOW), Material.EMERALD, 5));
                recipes.add(heroRecipe(new ItemStack(Material.ARROW, 16), Material.EMERALD, 2));
                recipes.add(heroRecipe(new ItemStack(Material.GOLDEN_APPLE), Material.EMERALD, 4));
                break;
        }
        villager.setRecipes(recipes);
    }

    private MerchantRecipe heroRecipe(ItemStack result, Material currency, int cost) {
        MerchantRecipe recipe = new MerchantRecipe(result, 9999);
        recipe.addIngredient(new ItemStack(currency, Math.max(1, cost)));
        recipe.setMaxUses(9999);
        return recipe;
    }

    private void spawnBabyArmorStand(World world, Location blockLoc, Player player) {
        Location at = blockLoc.clone().add(0.5, 0, 0.5);
        try {
            org.bukkit.entity.ArmorStand stand = world.spawn(at, org.bukkit.entity.ArmorStand.class);
            String who = player != null ? player.getName() : "Baby";
            stand.setCustomName(plugin.color("&eBaby " + who));
            stand.setCustomNameVisible(true);
            stand.setSmall(true);
            stand.setArms(true);
            if (player != null) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                org.bukkit.inventory.meta.SkullMeta skull = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
                if (skull != null) {
                    skull.setOwningPlayer(player);
                    head.setItemMeta(skull);
                }
                stand.getEquipment().setHelmet(head);
            }
            stand.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            stand.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            stand.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
            stand.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_PICKAXE));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void spawnRainbowSheep(World world, Location center, int count) {
        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < count; i++) {
            try {
                Entity entity = world.spawnEntity(center.clone().add(
                        ThreadLocalRandom.current().nextDouble(-3, 3), 0,
                        ThreadLocalRandom.current().nextDouble(-3, 3)), EntityType.SHEEP);
                if (entity instanceof org.bukkit.entity.Sheep sheep) {
                    sheep.setColor(colors[ThreadLocalRandom.current().nextInt(colors.length)]);
                }
                if (entity != null) {
                    entity.setCustomName(plugin.color("&dMr. Rainbow"));
                    entity.setCustomNameVisible(true);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void launchTnt(World world, Location center, int count) {
        for (int i = 0; i < count; i++) {
            try {
                TNTPrimed tnt = world.spawn(center.clone().add(
                        ThreadLocalRandom.current().nextDouble(-2, 2), 1,
                        ThreadLocalRandom.current().nextDouble(-2, 2)), TNTPrimed.class);
                tnt.setFuseTicks(80);
                tnt.setVelocity(new Vector(
                        ThreadLocalRandom.current().nextDouble(-0.3, 0.3), 1.2,
                        ThreadLocalRandom.current().nextDouble(-0.3, 0.3)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void spawnWitchBats(World world, Location center) {
        try {
            world.spawnEntity(center, EntityType.WITCH);
        } catch (IllegalArgumentException ignored) {
        }
        for (int i = 0; i < 64; i++) {
            try {
                world.spawnEntity(center.clone().add(
                        ThreadLocalRandom.current().nextDouble(-3, 3), 1,
                        ThreadLocalRandom.current().nextDouble(-3, 3)), EntityType.BAT);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void spawnPigVillagerStack(World world, Location blockLoc) {
        Location at = blockLoc.clone().add(0.5, 0, 0.5);
        Entity below = null;
        for (int i = 0; i < 7; i++) {
            try {
                Entity pig = world.spawnEntity(at.clone().add(0, i * 1.0, 0), EntityType.PIG);
                if (below != null) {
                    below.addPassenger(pig);
                }
                below = pig;
            } catch (IllegalArgumentException ignored) {
            }
        }
        try {
            Entity villager = world.spawnEntity(at, EntityType.VILLAGER);
            if (below != null) {
                below.addPassenger(villager);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void buildTntTower(World world, Location blockLoc) {
        Block base = blockLoc.getBlock();
        setIfReplaceable(world, base.getX(), base.getY(), base.getZ(), Material.REDSTONE_BLOCK);
        for (int i = 1; i <= 10; i++) {
            try {
                TNTPrimed tnt = world.spawn(base.getLocation().add(0.5, i, 0.5), TNTPrimed.class);
                tnt.setFuseTicks(100);
                tnt.setGravity(false);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void buildBedrockSign(World world, Location blockLoc) {
        Block base = blockLoc.getBlock();
        base.setType(Material.BEDROCK);
        Block above = world.getBlockAt(base.getX(), base.getY() + 1, base.getZ());
        above.setType(Material.OAK_SIGN);
        BlockState state = above.getState();
        if (state instanceof org.bukkit.block.Sign sign) {
            org.bukkit.block.sign.SignSide side =
                    sign.getSide(org.bukkit.block.sign.Side.FRONT);
            side.setLine(0, "Well, there's");
            side.setLine(1, "your problem.");
            sign.update(true);
        }
    }

    private Location target(Outcome outcome, Player player, Location blockLoc) {
        if ("PLAYER".equals(outcome.target) && player != null) {
            return player.getLocation();
        }
        return blockLoc;
    }

    // ------------------------------------------------------------------
    //  Traps
    // ------------------------------------------------------------------

    private void applyTrap(Outcome outcome, Player player, World world, Location blockLoc, Location center) {
        Location target = target(outcome, player, center);
        switch (outcome.trap) {
            case "EXPLOSION":
                world.createExplosion(center, (float) outcome.power, outcome.fire, outcome.breakBlocks);
                break;
            case "LIGHTNING":
                world.strikeLightning(target);
                break;
            case "FIRE": {
                Block block = world.getBlockAt(target);
                if (block.getType().isAir()) {
                    block.setType(Material.FIRE);
                }
                break;
            }
            case "ANVIL": {
                world.spawnFallingBlock(target.clone().add(0, 12, 0), Material.ANVIL.createBlockData());
                break;
            }
            case "TNT": {
                TNTPrimed tnt = world.spawn(center, TNTPrimed.class);
                tnt.setFuseTicks((int) Math.max(10, outcome.value > 0 ? outcome.value : 60));
                break;
            }
            case "ARROW_RAIN": {
                int arrows = (int) Math.max(1, outcome.value > 0 ? outcome.value : 24);
                for (int i = 0; i < arrows; i++) {
                    double ox = ThreadLocalRandom.current().nextDouble(-3, 3);
                    double oz = ThreadLocalRandom.current().nextDouble(-3, 3);
                    world.spawnArrow(target.clone().add(ox, 12, oz), new Vector(0, -1, 0), 2.0f, 6.0f);
                }
                break;
            }
            case "LAVA": {
                Block block = world.getBlockAt(target);
                if (block.getType().isAir() || block.getType() == Material.WATER) {
                    block.setType(Material.LAVA);
                }
                break;
            }
            case "WEB": {
                Block feet = target.getBlock();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        setIfReplaceable(world, feet.getX() + dx, feet.getY(), feet.getZ() + dz, Material.COBWEB);
                    }
                }
                break;
            }
            case "WATER": {
                Block block = world.getBlockAt(target);
                if (block.getType().isAir() || block.getType() == Material.LAVA) {
                    block.setType(Material.WATER);
                }
                break;
            }
            case "CAGE":
                buildBox(world, target, matOrDefault(outcome, Material.IRON_BARS), false);
                break;
            default:
                world.createExplosion(center, (float) outcome.power, outcome.fire, outcome.breakBlocks);
                break;
        }
    }

    // ------------------------------------------------------------------
    //  Structures
    // ------------------------------------------------------------------

    private void applyStructure(Outcome outcome, Player player, World world, Location blockLoc) {
        Location target = target(outcome, player, blockLoc);
        switch (outcome.structure) {
            case "CAGE":
                buildBox(world, target, matOrDefault(outcome, Material.IRON_BARS), false);
                break;
            case "OBSIDIAN_BOX":
                buildBox(world, target, matOrDefault(outcome, Material.OBSIDIAN), true);
                break;
            case "GLASS_BOX":
                buildBox(world, target, matOrDefault(outcome, Material.GLASS), true);
                break;
            case "PLATFORM":
                buildPlatform(world, target, matOrDefault(outcome, Material.OBSIDIAN),
                        (int) Math.max(1, outcome.value > 0 ? outcome.value : 2));
                break;
            case "TREE":
                world.generateTree(blockLoc.clone().add(0, 1, 0), TreeType.TREE);
                break;
            case "LOOT_CHEST":
                placeLootChest(world, blockLoc, outcome.items);
                break;
            default:
                buildBox(world, target, Material.IRON_BARS, false);
                break;
        }
    }

    /**
     * Builds a hollow 3x3x3 shell (walls + roof) around the origin block, leaving the
     * two-block-tall interior clear. Only replaces air/non-solid blocks, so it never
     * guts existing terrain.
     */
    private void buildBox(World world, Location origin, Material material, boolean includeFloor) {
        Block base = origin.getBlock();
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean interior = dx == 0 && dz == 0 && dy <= 1;
                    if (interior) continue;
                    setIfReplaceable(world, base.getX() + dx, base.getY() + dy, base.getZ() + dz, material);
                }
            }
        }
        if (includeFloor) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    setIfReplaceable(world, base.getX() + dx, base.getY() - 1, base.getZ() + dz, material);
                }
            }
        }
    }

    private void buildPlatform(World world, Location origin, Material material, int radius) {
        Block base = origin.getBlock();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                setIfReplaceable(world, base.getX() + dx, base.getY() - 1, base.getZ() + dz, material);
            }
        }
    }

    private void setIfReplaceable(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        Material current = block.getType();
        if (current.isAir() || !current.isSolid()) {
            block.setType(material);
        }
    }

    private Material matOrDefault(Outcome outcome, Material fallback) {
        if (outcome.material != null) {
            Material material = Material.matchMaterial(outcome.material);
            if (material != null && material.isBlock()) {
                return material;
            }
        }
        return fallback;
    }

    private void placeLootChest(World world, Location blockLoc, List<ItemStack> items) {
        Block block = blockLoc.getBlock();
        block.setType(Material.CHEST);
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) {
            return;
        }
        Inventory inventory = chest.getBlockInventory();
        List<ItemStack> pool = new ArrayList<>(items.isEmpty() ? defaultLoot() : items);
        Collections.shuffle(pool);
        int stacks = Math.max(3, Math.min(pool.size(), 6));
        for (int i = 0; i < stacks; i++) {
            ItemStack stack = pool.get(ThreadLocalRandom.current().nextInt(pool.size())).clone();
            inventory.setItem(ThreadLocalRandom.current().nextInt(inventory.getSize()), stack);
        }
        chest.update(true);
    }

    private List<ItemStack> defaultLoot() {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Material.DIAMOND, 3));
        loot.add(new ItemStack(Material.IRON_INGOT, 8));
        loot.add(new ItemStack(Material.GOLD_INGOT, 6));
        loot.add(new ItemStack(Material.EMERALD, 4));
        loot.add(new ItemStack(Material.GOLDEN_APPLE, 2));
        loot.add(new ItemStack(Material.ARROW, 16));
        loot.add(new ItemStack(Material.BREAD, 6));
        loot.add(new ItemStack(Material.COAL, 10));
        loot.add(new ItemStack(Material.REDSTONE, 12));
        loot.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 4));
        return loot;
    }

    // ------------------------------------------------------------------
    //  Player actions
    // ------------------------------------------------------------------

    private void applyAction(Outcome outcome, Player player, World world, Location loc) {
        switch (outcome.action) {
            case "HEAL": {
                if (player != null) {
                    AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
                    player.setHealth(max != null ? max.getValue() : player.getHealth());
                }
                break;
            }
            case "FEED": {
                if (player != null) {
                    player.setFoodLevel(20);
                    player.setSaturation(20.0f);
                }
                break;
            }
            case "XP": {
                if (player != null) {
                    player.giveExpLevels((int) Math.max(1, outcome.value > 0 ? outcome.value : 30));
                }
                break;
            }
            case "LAUNCH": {
                if (player != null) {
                    player.setVelocity(new Vector(0, outcome.value > 0 ? outcome.value : 1.5, 0));
                }
                break;
            }
            case "TELEPORT": {
                if (player != null) {
                    teleportRandom(player, (int) Math.max(16, outcome.value > 0 ? outcome.value : 200));
                }
                break;
            }
            case "IGNITE": {
                if (player != null) {
                    player.setFireTicks((int) Math.max(1, outcome.value > 0 ? outcome.value : 5) * 20);
                }
                break;
            }
            case "WEATHER_CLEAR":
                world.setStorm(false);
                world.setThundering(false);
                break;
            case "WEATHER_RAIN":
                world.setStorm(true);
                world.setThundering(false);
                break;
            case "THUNDER":
                world.setStorm(true);
                world.setThundering(true);
                break;
            case "TIME_DAY":
                world.setTime(1000L);
                break;
            case "TIME_NIGHT":
                world.setTime(13000L);
                break;
            default:
                break;
        }
    }

    private void teleportRandom(Player player, int radius) {
        World world = player.getWorld();
        Location from = player.getLocation();
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = from.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int z = from.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            Block highest = world.getHighestBlockAt(x, z);
            Location dest = highest.getLocation().add(0.5, 1.0, 0.5);
            if (dest.getBlock().getType().isAir() && dest.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                player.teleport(dest);
                return;
            }
        }
    }

    private void playFeedback(LuckCategory category, Location loc) {
        if (!plugin.getConfig().getBoolean("effects.sounds", true)) return;
        World world = loc.getWorld();
        if (world == null) return;
        Sound sound;
        switch (category) {
            case GOOD:
                sound = Sound.ENTITY_PLAYER_LEVELUP;
                break;
            case BAD:
                sound = Sound.ENTITY_GENERIC_EXPLODE;
                break;
            default:
                sound = Sound.BLOCK_NOTE_BLOCK_PLING;
                break;
        }
        world.playSound(loc, sound, 1.0f, 1.0f);
    }
}
