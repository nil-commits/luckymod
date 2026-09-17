package com.nilcommits.luckymod;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * One configurable lucky-block outcome, parsed from an entry in the {@code outcomes}
 * list of config.yml. Supports simple types (ITEM/MOB/EFFECT/TRAP/STRUCTURE/ACTION)
 * plus FALLING, APOCALYPSE and SPECIAL (hard-coded complex events).
 */
public final class Outcome {

    public final OutcomeType type;
    public final LuckCategory category;
    public final double weight;
    public final String message;

    // ITEM
    public final List<ItemStack> items = new ArrayList<>();   // always dropped
    public final List<ItemStack> pool = new ArrayList<>();    // randomly picked from
    public int minPick = 0;
    public int maxPick = 0;
    public final Map<String, Integer> enchants = new LinkedHashMap<>();
    public boolean randomEnchantments = false;
    public int enchantCount = 3;
    public int enchantMaxLevel = 3;
    public String name = null;                                // display name
    public final List<String> lore = new ArrayList<>();
    public boolean launch = false;

    // MOB
    public EntityType mob;
    public int mobMin = 1;
    public int mobMax = 1;
    public int size = 0;
    public boolean charged = false;
    public boolean tamed = false;
    public boolean baby = false;
    public String collar = null;
    public String rabbitType = null;
    public final Map<String, String> equipment = new LinkedHashMap<>();

    // EFFECT
    public PotionEffectType effect;
    public int duration = 20;
    public int amplifier = 0;

    // TRAP / STRUCTURE / ACTION
    public String trap = "EXPLOSION";
    public String structure = "CAGE";
    public String action = "HEAL";
    public String material = null;
    public String target = "BLOCK";
    public double value = 0;
    public double power = 3.0;
    public boolean fire = false;
    public boolean breakBlocks = false;

    // FALLING
    public String fallingMaterial = null;
    public String topMaterial = null;
    public int height = 15;
    public int fallingCount = 1;
    public int spread = 0;
    public boolean lightning = false;

    // APOCALYPSE
    public final List<MobGroup> groups = new ArrayList<>();
    public boolean apocHard = true;
    public boolean apocMidnight = true;
    public boolean apocSword = true;
    public boolean apocSlowness = true;
    public boolean apocBlindness = true;

    // SPECIAL
    public String special = null;
    public String preset = null;

    public static final class MobGroup {
        public final EntityType mob;
        public final int min;
        public final int max;

        MobGroup(EntityType mob, int min, int max) {
            this.mob = mob;
            this.min = min;
            this.max = max;
        }
    }

    private Outcome(OutcomeType type, LuckCategory category, double weight, String message) {
        this.type = type;
        this.category = category;
        this.weight = Math.max(0.0, weight);
        this.message = message == null ? "" : message;
    }

    @SuppressWarnings("unchecked")
    public static Outcome parse(Map<?, ?> map, Logger log) {
        OutcomeType type;
        try {
            type = OutcomeType.valueOf(str(map.get("type"), "ITEM").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning("Luckymod: unknown outcome type '" + map.get("type") + "', skipping.");
            return null;
        }
        LuckCategory category;
        try {
            category = LuckCategory.valueOf(str(map.get("category"), "NEUTRAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            category = LuckCategory.NEUTRAL;
        }

        Outcome o = new Outcome(type, category, num(map.get("weight"), 1.0), str(map.get("message"), ""));
        o.name = map.get("name") == null ? null : String.valueOf(map.get("name"));
        o.material = map.get("material") == null ? null : String.valueOf(map.get("material"));
        o.target = str(map.get("target"), type == OutcomeType.ACTION ? "PLAYER" : "BLOCK").toUpperCase(Locale.ROOT);
        o.value = num(map.get("value"), 0);
        o.launch = bool(map.get("launch"), false);

        Object lore = map.get("lore");
        if (lore instanceof List) {
            for (Object line : (List<?>) lore) o.lore.add(String.valueOf(line));
        }

        switch (type) {
            case ITEM: {
                parseItems(map.get("items"), o.items);
                parseItems(map.get("pool"), o.pool);
                o.minPick = (int) Math.max(0, num(map.get("min"), o.pool.isEmpty() ? 0 : 1));
                o.maxPick = (int) Math.max(o.minPick, num(map.get("max"), o.minPick));
                parseEnchants(map.get("enchants"), o.enchants);
                o.randomEnchantments = bool(map.get("randomEnchantments"), false);
                o.enchantCount = (int) Math.max(0, num(map.get("enchantCount"), 3));
                o.enchantMaxLevel = (int) Math.max(1, num(map.get("enchantMaxLevel"), 3));
                if (o.items.isEmpty() && o.pool.isEmpty()) {
                    log.warning("Luckymod: ITEM outcome has no items/pool, skipping.");
                    return null;
                }
                break;
            }
            case MOB: {
                o.mob = parseEntity(str(map.get("mob"), "ZOMBIE"));
                if (o.mob == null) {
                    log.warning("Luckymod: MOB outcome has invalid mob '" + map.get("mob") + "', skipping.");
                    return null;
                }
                o.mobMin = (int) Math.max(1, num(map.get("min"), num(map.get("count"), 1)));
                o.mobMax = (int) Math.max(o.mobMin, num(map.get("max"), o.mobMin));
                o.size = (int) Math.max(0, num(map.get("size"), 0));
                o.charged = bool(map.get("charged"), false);
                o.tamed = bool(map.get("tamed"), false);
                o.baby = bool(map.get("baby"), false);
                o.collar = map.get("collar") == null ? null : String.valueOf(map.get("collar"));
                o.rabbitType = map.get("rabbitType") == null ? null : String.valueOf(map.get("rabbitType"));
                Object equip = map.get("equipment");
                if (equip instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) equip).entrySet()) {
                        o.equipment.put(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT), String.valueOf(e.getValue()));
                    }
                }
                break;
            }
            case EFFECT: {
                o.effect = parseEffect(str(map.get("effect"), "speed"));
                if (o.effect == null) {
                    log.warning("Luckymod: EFFECT outcome has invalid effect '" + map.get("effect") + "', skipping.");
                    return null;
                }
                o.duration = (int) Math.max(1, num(map.get("duration"), 20.0));
                o.amplifier = (int) Math.max(0, num(map.get("amplifier"), 0.0));
                break;
            }
            case TRAP: {
                o.trap = str(map.get("trap"), "EXPLOSION").toUpperCase(Locale.ROOT);
                o.power = num(map.get("power"), 3.0);
                o.fire = bool(map.get("fire"), false);
                o.breakBlocks = bool(map.get("breakBlocks"), false);
                break;
            }
            case STRUCTURE: {
                o.structure = str(map.get("structure"), "CAGE").toUpperCase(Locale.ROOT);
                parseItems(map.get("items"), o.items);
                break;
            }
            case ACTION: {
                o.action = str(map.get("action"), "HEAL").toUpperCase(Locale.ROOT);
                break;
            }
            case FALLING: {
                o.fallingMaterial = str(map.get("fallingMaterial"), "IRON_BLOCK");
                o.topMaterial = map.get("topMaterial") == null ? null : String.valueOf(map.get("topMaterial"));
                o.height = (int) Math.max(1, num(map.get("height"), 15));
                o.fallingCount = (int) Math.max(1, num(map.get("count"), 1));
                o.spread = (int) Math.max(0, num(map.get("spread"), 0));
                o.lightning = bool(map.get("lightning"), false);
                break;
            }
            case APOCALYPSE: {
                Object groups = map.get("groups");
                if (groups instanceof List) {
                    for (Object entry : (List<?>) groups) {
                        if (!(entry instanceof Map)) continue;
                        Map<?, ?> g = (Map<?, ?>) entry;
                        EntityType mob = parseEntity(str(g.get("mob"), "ZOMBIE"));
                        if (mob == null) continue;
                        int min = (int) Math.max(1, num(g.get("min"), 10));
                        int max = (int) Math.max(min, num(g.get("max"), min));
                        o.groups.add(new MobGroup(mob, min, max));
                    }
                }
                if (o.groups.isEmpty()) {
                    log.warning("Luckymod: APOCALYPSE outcome has no groups, skipping.");
                    return null;
                }
                o.apocHard = bool(map.get("difficultyHard"), true);
                o.apocMidnight = bool(map.get("midnight"), true);
                o.apocSword = bool(map.get("giveWoodenSword"), true);
                o.apocSlowness = bool(map.get("slowness"), true);
                o.apocBlindness = bool(map.get("blindness"), true);
                break;
            }
            case SPECIAL: {
                o.special = str(map.get("special"), "").toUpperCase(Locale.ROOT);
                o.preset = map.get("preset") == null ? null : String.valueOf(map.get("preset")).toUpperCase(Locale.ROOT);
                parseItems(map.get("items"), o.items);
                if (o.special.isEmpty()) {
                    log.warning("Luckymod: SPECIAL outcome has no 'special' name, skipping.");
                    return null;
                }
                break;
            }
        }
        return o;
    }

    private static void parseItems(Object raw, List<ItemStack> into) {
        if (!(raw instanceof List)) return;
        for (Object entry : (List<?>) raw) {
            ItemStack stack = parseItem(String.valueOf(entry));
            if (stack != null) into.add(stack);
        }
    }

    private static void parseEnchants(Object raw, Map<String, Integer> into) {
        if (!(raw instanceof Map)) return;
        for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
            into.put(String.valueOf(e.getKey()), (int) num(e.getValue(), 1));
        }
    }

    private static ItemStack parseItem(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(":");
        Material material = Material.matchMaterial(parts[0].trim());
        if (material == null || !material.isItem()) return null;
        int amount = parts.length > 1 ? (int) parseInt(parts[1].trim(), 1) : 1;
        amount = Math.max(1, Math.min(amount, material.getMaxStackSize()));
        return new ItemStack(material, amount);
    }

    private static EntityType parseEntity(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static PotionEffectType parseEffect(String name) {
        String key = name.toLowerCase(Locale.ROOT).trim();
        try {
            PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.minecraft(key));
            if (t != null) return t;
        } catch (Throwable ignored) {
        }
        try {
            PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(key));
            if (t != null) return t;
        } catch (Throwable ignored) {
        }
        try {
            return PotionEffectType.getByName(name.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String str(Object value, String def) {
        return value == null ? def : String.valueOf(value);
    }

    private static double num(Object value, double def) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        return parseInt(String.valueOf(value), (long) def);
    }

    private static long parseInt(String value, long def) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static boolean bool(Object value, boolean def) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value == null) return def;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
