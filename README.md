# Luckymod

A **Lucky Block** plugin for Paper/Spigot (Bukkit API). Place a Lucky Block, break it, and a
weighted random outcome fires — from treasure and helpful mobs to cages, anvils, explosions
and full-blown monster apocalypses.

Everything is config-driven: no resource pack or client mod required.

- **Minecraft:** 26.2 (Paper / Spigot / Bukkit) — `api-version: 1.21`
- **Java:** 25 (26.2 requires it)
- **Build:** Gradle 9.5.1 (wrapper included)

> Inspired by the classic "Lucky Block" mod. This is an independent implementation — no
> third-party code or assets are bundled.

## Features

- A Lucky Block is a normal vanilla block (default `SPONGE`) tagged via persistent data, so it
  works on a completely vanilla client.
- **9 outcome types:** `ITEM`, `MOB`, `EFFECT`, `TRAP`, `STRUCTURE`, `ACTION`, `FALLING`,
  `APOCALYPSE`, `SPECIAL`.
- **Weighted GOOD / NEUTRAL / BAD** outcomes. Each block carries a **luck value** (-100..100):
  positive luck biases rolls toward GOOD, negative toward BAD.
- **Crafting** (8 gold ingots + dropper by default) and a **`/lucky give`** command.
- **Luck upgrades** in a crafting table: combine a Lucky Block with catalysts (Diamond +10,
  Emerald +8, Rotten Flesh -10, ...) to raise/lower its luck. The result keeps the block's
  current luck plus the catalysts' deltas.
- Item pools, random enchantments, named items, mob equipment/names/size, falling blocks,
  mob-wave apocalypses, structures (cages, boxes, loot chests, slime castle), hero villagers,
  and more.
- Placed blocks persist across restarts (`luckyblocks.yml`).

## Install

1. Drop `Luckymod-<version>.jar` into your server's `plugins/` folder.
2. Start the server. A default `plugins/Luckymod/config.yml` is generated.
3. Get a block with `/lucky give` (or craft one) and break it.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/lucky help` | `luckymod.use` | Show help |
| `/lucky give [player] [amount] [luck]` | `luckymod.give` | Give Lucky Blocks (defaults: self, 1, 0) |
| `/lucky reload` | `luckymod.reload` | Reload `config.yml` and re-register recipes |

Aliases: `/luckymod`, `/lb`.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `luckymod.use` | true | Use/place Lucky Blocks |
| `luckymod.give` | op | `/lucky give` |
| `luckymod.reload` | op | `/lucky reload` |

## Configuration

`plugins/Luckymod/config.yml` controls the block material, crafting recipe, upgrade catalysts,
and the full outcome list. Key fields per outcome:

```yaml
outcomes:
  - type: ITEM            # ITEM | MOB | EFFECT | TRAP | STRUCTURE | ACTION | FALLING | APOCALYPSE | SPECIAL
    category: GOOD        # GOOD | NEUTRAL | BAD
    weight: 8             # relative chance
    message: "&aJackpot!" # shown to the breaker (& colour codes)
    items: ["DIAMOND:16"] # type-specific fields below
```

Type-specific fields:

- **ITEM** — `items`, `pool` + `min`/`max` (random picks), `name`, `lore`, `launch` (throw into air),
  `enchants: {SHARPNESS: 5}`, `randomEnchantments`, `enchantCount`, `enchantMaxLevel`
- **MOB** — `mob`, `min`/`max`, `name`, `size`, `charged`, `tamed`, `baby`, `collar`,
  `rabbitType`, `equipment: {helmet,chest,legs,boots,hand,offhand}`
- **EFFECT** — `effect`, `duration` (seconds), `amplifier`
- **TRAP** — `trap: EXPLOSION|LIGHTNING|FIRE|ANVIL|TNT|ARROW_RAIN|LAVA|WEB|WATER|CAGE`,
  `target: BLOCK|PLAYER`, `value`
- **STRUCTURE** — `structure: CAGE|OBSIDIAN_BOX|GLASS_BOX|PLATFORM|TREE|LOOT_CHEST`,
  `target`, `material`, `value`, `items` (loot table)
- **ACTION** — `action: HEAL|FEED|XP|LAUNCH|TELEPORT|IGNITE|WEATHER_CLEAR|WEATHER_RAIN|THUNDER|TIME_DAY|TIME_NIGHT`, `value`
- **FALLING** — `fallingMaterial`, `topMaterial`, `height`, `count`, `spread`, `lightning`
- **APOCALYPSE** — `groups: [{mob, min, max}]`, `difficultyHard`, `midnight`, `giveWoodenSword`, `slowness`, `blindness`
- **SPECIAL** — `special: SLIME_CASTLE|HERO_VILLAGER|ARMOR_STAND_BABY|RAINBOW_SHEEP|TNT_AIR_LAUNCH|WITCH_BATS|MOB_STACK|TNT_TOWER|TWO_BLOCKS|BEDROCK_SIGN`, `preset`, `value`

### Luck upgrades

```yaml
upgrades:
  enabled: true
  catalysts:
    DIAMOND: 10
    EMERALD: 8
    ROTTEN_FLESH: -10
```

Put 1 Lucky Block plus any number of catalysts in a crafting grid; the result is the same
block with `current luck + sum(catalyst deltas)`, clamped to -100..100.

## Building

Requires JDK 25 (the 26.2 Paper API is compiled for it).

```bash
./gradlew clean build
# output: target/Luckymod-<version>.jar
```

## Known gaps

Some meta events from the original mod are not implemented yet, notably:
wishing well interaction, the giant lucky block, and randomized-effect "lucky/hero/evil" potions.

## License

Licensed under the **GNU Affero General Public License v3.0** — see [LICENSE](LICENSE).
