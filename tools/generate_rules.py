#!/usr/bin/env python3
"""Generate DS4C's contextual facts and trigger-effect rules for Controlify.

Writes, inside the built-in "effects" resource pack:
  assets/controlify/contextual/facts/in_game.json
  assets/controlify/contextual/trigger_effect/in_game.json

Run from the repository root:  python3 tools/generate_rules.py
Commit the generated JSON alongside changes to this script.

How Controlify evaluates these (see Controlify's docs/content/resource-packs/):
  * DS4C's pack ranks above Controlify's own resources, so these rules are checked first.
  * For each binding, the FIRST matching rule wins. Order in RULES below is priority order.
  * A rule only applies while its binding ("for") is bound to a controller trigger.
  * Facts that are never contributed read as false.

We do not set "replace": Controlify's default rules (items being used, off-hand fallbacks,
attack-with-weapon) still apply underneath ours. Every DS4C Use rule therefore requires
"not using an item", otherwise it would hide Controlify's draw/charge effects.
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "common/src/main/resources/resourcepacks/effects/assets/controlify/contextual"

USE = "controlify:use"
ATTACK = "controlify:attack"
BOTH = (USE, ATTACK)


# --- effect helpers (field names match Controlify's TriggerEffectCodecs) -------------------

def feedback(position, strength):
    return {"type": "feedback", "position": position, "strength": strength}


def vibration(position, amplitude, frequency):
    return {"type": "vibration", "position": position, "amplitude": amplitude, "frequency": frequency}


def weapon(start, end, strength):
    return {"type": "weapon", "start_position": start, "end_position": end, "strength": strength}


def feedback_slope(start, end, start_strength, end_strength):
    return {"type": "feedback_slope", "start_position": start, "end_position": end,
            "start_strength": start_strength, "end_strength": end_strength}


def bow(start, end, strength, snap_force):
    # Unofficial firmware effect; needs Controlify 3.5.4+ (unsigned, corrected encoding).
    return {"type": "bow", "start_position": start, "end_position": end,
            "strength": strength, "snap_force": snap_force}


OFF = {"type": "off"}


# --- predicate helpers ----------------------------------------------------------------------

def main_hand(item):
    return {"slot": "controlify:main_hand", "item": item}


def block_at(slot, blocks):
    return {"slot": slot, "block": {"blocks": blocks}}


def bucket(name, value):
    return f"ds4c:{name}/{value}"


def event(name):
    return f"ds4c:event/{name}"


def use_anim(name):
    return f"ds4c:use_anim/{name}"


# --- derived facts (data-driven; tweakable by resource packs) -------------------------------

FACTS = {
    "ds4c:in_portal": block_at("ds4c:block_at_feet", ["minecraft:nether_portal", "minecraft:end_portal"]),
    "ds4c:on_ice": {"all_of": ["controlify:on_ground", block_at("ds4c:block_below", "#minecraft:ice")]},
    "ds4c:on_magma": {"all_of": ["controlify:on_ground", block_at("ds4c:block_below", "minecraft:magma_block")]},
    "ds4c:hungry_sprint": {"all_of": ["controlify:sprinting", "ds4c:low_food"]},
    "ds4c:holding_mace": main_hand({"items": "minecraft:mace"}),
    "ds4c:holding_weapon": main_hand({"predicates": {"minecraft:weapon": {}}}),
    "ds4c:place_ready": {"all_of": ["controlify:main_hand_is_block_item", "controlify:looking_at_block"]},
}


# --- rules, in priority order per binding ---------------------------------------------------

RULES = []


def rule(binding, category, condition, effect, idle_only=False):
    """Add a DS4C rule gated on the master switch and its category.

    idle_only: for Use rules that must not hide an item-in-use effect (passive and held-item rules).
    """
    clauses = ["ds4c:enabled", f"ds4c:category/{category}"]
    clauses += condition if isinstance(condition, list) else [condition]
    predicate = {"all_of": clauses}
    if idle_only and binding == USE:
        predicate["none_of"] = ["ds4c:using_item"]
    RULES.append({"for": binding, "if": predicate, "then": effect})


def both(category, condition, effect, idle_only=False):
    for binding in BOTH:
        rule(binding, category, condition, effect, idle_only)


def draw_curve(level, snap_force=None):
    """Bow tension for draw bucket 0..7: the tension zone widens and stiffens as the draw builds,
    then snaps back on release. snap_force defaults to full (8)."""
    power = level / 7
    start = max(2, round(7 - 5 * power))
    strength = min(8, round(4 + 4 * power ** 0.5))
    return bow(start, 8, strength, 8 if snap_force is None else snap_force)


def horse_jump_curve(level):
    """Mount jump charge 0..7: same shape as the bow, with resistance climbing from 2 to 8."""
    charge = level / 7
    return bow(max(2, round(7 - 5 * charge)), 8, round(2 + 6 * charge), 8)


# Block-break pulse frequency by main-hand tool class (ds4c:tool/*).
TOOL_FREQUENCIES = {"pickaxe": 60, "shovel": 40, "axe": 24, "hoe": 18, "shears": 75, "mace": 22, "other": 30}


def bursts():
    """Short event windows. Highest priority: they should cut through whatever else is happening."""
    for name, effect in [("hit_crit", vibration(0, 8, 70)), ("hit_knockback", vibration(0, 8, 18)),
                         ("hit_strong", vibration(0, 8, 25)), ("hit_sweep", vibration(0, 6, 50)),
                         ("hit_weak", vibration(0, 4, 35)), ("hit_no_damage", vibration(0, 2, 40))]:
        rule(ATTACK, "combat", event(name), effect)
    rule(ATTACK, "combat", event("crit"), vibration(0, 8, 50))
    both("combat", event("projectile_hit"), vibration(0, 7, 50))
    for strength in range(8, 3, -1):
        both("combat", bucket("event/mace_land", strength), vibration(0, strength, 22))

    rule(USE, "combat", event("shield_block"), vibration(6, 8, 100))
    rule(USE, "combat", event("crossbow_fire"), vibration(2, 8, 60))
    rule(USE, "combat", event("crossbow_loaded"), weapon(7, 8, 8))
    rule(USE, "combat", event("bow_release"), vibration(2, 7, 40))
    rule(USE, "combat", event("trident_release"), weapon(2, 8, 8))

    rule(ATTACK, "combat", event("tool_warning_attack"), vibration(4, 3, 6))
    rule(USE, "environment", event("tool_warning_use"), vibration(4, 3, 6))
    rule(ATTACK, "environment", event("break_signature"), vibration(4, 6, 80))
    rule(USE, "environment", event("bucket_lava"), vibration(4, 4, 25))

    rule(USE, "items", event("drink_fizz"), vibration(0, 4, 80))
    rule(USE, "items", "ds4c:chew", vibration(4, 3, 8))
    rule(USE, "items", "ds4c:fishing_biting", vibration(2, 6, 25))


def actions():
    """The action in progress."""
    # Attack swing: heavy start, light middle, hard stop.
    rule(ATTACK, "combat", event("swing_1"), vibration(0, 8, 35))
    rule(ATTACK, "combat", event("swing_2"), vibration(0, 2, 50))
    rule(ATTACK, "combat", event("swing_3"), vibration(0, 8, 22))

    # Block breaking: the block giving way, then per-crack-stage pulses tuned to the tool.
    rule(ATTACK, "environment", event("block_broken"), vibration(0, 8, 35))
    for amplitude in range(8, 0, -1):
        for tool, frequency in TOOL_FREQUENCIES.items():
            rule(ATTACK, "environment", [bucket("event/break_pulse", amplitude), f"ds4c:tool/{tool}"],
                 vibration(0, amplitude, frequency))

    # Using items on blocks and entities.
    rule(USE, "environment", event("bucket"), feedback(4, 5))
    rule(USE, "environment", event("hoe_till"), vibration(3, 5, 18))
    rule(USE, "environment", event("spawn_egg"), weapon(3, 4, 2))
    rule(USE, "environment", event("block_place"), weapon(3, 4, 3))
    rule(USE, "environment", event("shears_clip"), weapon(4, 6, 7))

    rule(USE, "items", event("fishing_cast"), weapon(3, 5, 4))
    rule(ATTACK, "items", "ds4c:fishing_biting", feedback(3, 7))

    rule(USE, "movement", event("firework"), vibration(0, 8, 60))
    both("movement", event("jump"), vibration(0, 3, 35))

    # Items in use. These replace Controlify's defaults for the same animations.
    rule(USE, "combat", "ds4c:crossbow_loaded", OFF)
    for level in range(7, -1, -1):
        rule(USE, "combat", [use_anim("bow"), bucket("draw", level)], draw_curve(level))
        trident_snap = min(8, round(4 + 4 * (level / 7) ** 0.5))
        rule(USE, "combat", [use_anim("trident"), bucket("draw", level)], draw_curve(level, trident_snap))
        loading = min(7, 4 + round(3 * (level / 7) ** 0.5))
        rule(USE, "combat", [use_anim("crossbow"), bucket("draw", level)], feedback_slope(2, 9, loading, 8))
    rule(USE, "items", {"any_of": [use_anim("eat"), use_anim("drink")]}, feedback_slope(0, 9, 2, 6))
    rule(USE, "combat", use_anim("block"), feedback(6, 8))
    rule(USE, "items", use_anim("toot_horn"), vibration(2, 5, 18))

    for level in range(7, -1, -1):
        rule(USE, "movement", bucket("horse_jump", level), horse_jump_curve(level))


def held_item_baselines():
    """Main-hand baselines so the trigger already feels like the item before it's used."""
    # Crossbow: uncharged first (empty charged_projectiles), then any crossbow = charged.
    rule(USE, "combat", main_hand({"items": "#c:tools/crossbow", "components": {"minecraft:charged_projectiles": []}}),
         feedback(4, 2), idle_only=True)
    rule(USE, "combat", main_hand({"items": "#c:tools/crossbow"}), feedback(4, 8), idle_only=True)
    # Bow: the start of a draw, so there's no jump in feel when the draw begins.
    rule(USE, "combat", main_hand({"items": "#c:tools/bow"}), bow(7, 8, 2, 8), idle_only=True)
    rule(USE, "combat", main_hand({"items": "#c:tools/trident"}), feedback(3, 4), idle_only=True)
    rule(USE, "items", main_hand({"items": "minecraft:spyglass"}), feedback(5, 5), idle_only=True)
    rule(USE, "environment", "ds4c:place_ready", feedback(4, 3), idle_only=True)


def attack_baselines():
    # Mace wind-up: resistance across the whole travel, growing with fall distance.
    for strength in range(8, 2, -1):
        rule(ATTACK, "combat", ["ds4c:holding_mace", bucket("fall_charge", strength)], feedback(0, strength))
    # Looking at a block the held item can harvest: harder blocks resist more.
    for strength in range(8, 0, -1):
        rule(ATTACK, "environment", bucket("target_resistance", strength), feedback(2, strength))
    rule(ATTACK, "combat", "ds4c:holding_weapon", feedback(3, 4))


def environment():
    """Passive states felt on both triggers. Lowest priority."""
    both("ambient", "ds4c:on_magma", vibration(0, 6, 10), idle_only=True)
    for level in range(4, 0, -1):
        both("ambient", bucket("sticky", level), feedback(0, level), idle_only=True)
    both("ambient", "ds4c:underwater", feedback(1, 2), idle_only=True)
    both("ambient", "ds4c:on_ice", vibration(0, 2, 30), idle_only=True)
    for amplitude in range(6, 1, -1):
        both("movement", ["ds4c:riding_minecart", bucket("vehicle_speed", amplitude)], vibration(0, amplitude, 22),
             idle_only=True)
        both("movement", ["ds4c:riding_boat", bucket("vehicle_speed", amplitude)], vibration(0, amplitude, 14),
             idle_only=True)
    both("ambient", "ds4c:hungry_sprint", vibration(2, 2, 10), idle_only=True)


def build():
    bursts()
    actions()
    # Portal swirl outranks everything passive: it's the "something big is about to happen" cue.
    both("ambient", "ds4c:in_portal", vibration(0, 7, 12), idle_only=True)
    held_item_baselines()
    attack_baselines()
    environment()


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")
    print(f"wrote {path.relative_to(ROOT)}")


if __name__ == "__main__":
    build()
    write(PACK / "facts/in_game.json", FACTS)
    write(PACK / "trigger_effect/in_game.json", {"rules": RULES})
    print(f"{len(FACTS)} facts, {len(RULES)} rules")
