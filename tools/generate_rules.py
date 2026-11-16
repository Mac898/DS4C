#!/usr/bin/env python3
"""Generate DS4C's contextual facts and trigger-effect rules for Controlify.

Writes, inside the built-in "effects" resource pack:
  assets/controlify/contextual/facts/in_game.json
  assets/controlify/contextual/trigger_effect/in_game.json

and the same into "effects_no_bow", with every bow effect swapped for the weapon effect. Controlify
fails a whole rule file on one effect type it can't decode, and 3.5.3 has no bow, so DS4C picks
the pack at runtime.

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
PACKS = ROOT / "common/src/main/resources/resourcepacks"
PACK = PACKS / "effects"
NO_BOW_PACK = PACKS / "effects_no_bow"
CONTEXTUAL = "assets/controlify/contextual"
# Index of effect-gallery entries, read by the /ds4c gallery command.
GALLERY_INDEX = ROOT / "common/src/main/resources/assets/ds4c/gallery_triggers.json"

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
    # Unofficial firmware effect; not in Controlify 3.5.3 (see without_bow).
    return {"type": "bow", "start_position": start, "end_position": end,
            "strength": strength, "snap_force": snap_force}


def without_bow(effect):
    """The closest effect Controlify 3.5.3 has: weapon gives way at the end of its zone like bow,
    just without the snap back. Weapon zones start at 2 at the earliest."""
    if effect["type"] != "bow":
        return effect
    start = max(2, effect["start_position"])
    return weapon(start, max(start + 1, effect["end_position"]), effect["strength"])


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
    "ds4c:holding_mace": main_hand({"items": "minecraft:mace"}),
    "ds4c:holding_weapon": main_hand({"predicates": {"minecraft:weapon": {}}}),
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


# Design rules: trigger vibration only for short events and at
# or below 35 Hz; light resistance (1-3) for frequent actions, walls for rare ones; adjacent levels
# at least ~25% apart, so ramps use few levels.
MAX_VIBRATION_HZ = 35

# Levels of the ds4c:draw/* and ds4c:horse_jump/* buckets (0..3).
DRAW_LEVELS = 4


def draw_strength(level):
    """2, 4, 6, 8: each step is at least 25% stronger than the last."""
    return 2 + 2 * level


def draw_curve(level):
    """Draw tension for level 0..3 when shooting on release: resistance that starts earlier and
    ramps harder as the draw builds, all the way to the bottom of the pull, so there is no point
    to push through. Level 0 matches the held-bow baseline."""
    start = 5 - level
    return feedback_slope(start, 9, 1 + level, draw_strength(level))


def pull_through_curve(level):
    """Draw tension for level 0..3 with "pull through to shoot" on: the firmware Bow effect, whose
    resistance zone widens and stiffens as the draw builds and then gives way at its end, where
    pressing on to the bottom looses the shot (input/PullThrough). Level 0 matches the held-bow
    baseline.

    The zone never starts above PULL_THROUGH_MIN_START. The resistance pushes a lightly resting finger
    back to the start of the zone; a zone starting near the top (it reached zone 2 at full draw)
    pushed it out past the let-out point (PullThrough.RESET, about a quarter), which looses the shot,
    and the next touch drew again, firing rapidly under a light press."""
    start = max(PULL_THROUGH_MIN_START, 7 - level)
    return bow(start, 8, draw_strength(level), 8)


# Highest start zone (0..9) for the pull-through draw resistance: 0.4 of the travel, clear of the
# 0.25 let-out point in input/PullThrough.
PULL_THROUGH_MIN_START = 4


# Mining knock strength on R2 by main-hand tool class (ds4c:tool/*): a resistance push three times per block.
TOOL_KNOCK = {"pickaxe": 6, "shovel": 5, "axe": 7, "hoe": 4, "shears": 4, "mace": 6, "other": 5}


def bursts():
    """Short event windows (at most 150 ms). Highest priority: they cut through everything else."""
    # Hits: one short burst, strength by hit type.
    # (Standard hits were lost under the held-weapon weight in testing, so the floor is higher.)
    for name, effect in [("hit_crit", vibration(0, 8, 30)), ("hit_knockback", vibration(0, 8, 18)),
                         ("hit_strong", vibration(0, 7, 22)), ("hit_sweep", vibration(0, 6, 25)),
                         ("hit_weak", vibration(0, 4, 30)), ("hit_no_damage", vibration(0, 3, 30))]:
        rule(ATTACK, "combat", event(name), effect)
    # Mace slam: a short kick on the trigger that swung it; the grips carry the impact.
    for level, amplitude in [(3, 8), (2, 6), (1, 4)]:
        rule(ATTACK, "combat", bucket("event/mace_land", level), vibration(0, amplitude, 22))

    # Explosions close enough to feel: a kick on both triggers, by strength.
    for level, amplitude in [(3, 8), (2, 6), (1, 4)]:
        both("combat", bucket("event/explosion", level), vibration(0, amplitude, 25))

    rule(USE, "combat", event("shield_break"), vibration(0, 8, 20))
    rule(USE, "combat", event("shield_block"), vibration(5, 8, 25))
    rule(USE, "combat", event("crossbow_fire"), vibration(2, 8, 30))
    rule(USE, "combat", event("crossbow_loaded"), weapon(7, 8, 8))
    rule(USE, "combat", event("bow_release"), vibration(2, 7, 30))
    rule(USE, "combat", event("trident_release"), weapon(2, 8, 8))

    rule(ATTACK, "combat", event("tool_warning_attack"), vibration(4, 3, 6))
    rule(USE, "environment", event("tool_warning_use"), vibration(4, 3, 6))
    rule(USE, "environment", event("bucket_lava"), vibration(4, 3, 25))

    rule(USE, "items", "ds4c:chew", vibration(4, 3, 8))
    rule(USE, "items", "ds4c:fishing_biting", vibration(2, 4, 25))


def actions():
    """The action in progress."""
    # Attack: one click; the swing itself belongs to the grips.
    # Swing: a full-charge swing is solid and low; one before the attack has recharged is a weak flick.
    rule(ATTACK, "combat", event("swing_full"), vibration(0, 7, 18))
    rule(ATTACK, "combat", event("swing_weak"), vibration(0, 2, 30))

    # Block breaking: three knocks per block by tool, then a low thunk as it gives way. (Vibration
    # pulses at every crack stage were felt as buzzing.)
    rule(ATTACK, "environment", event("block_broken"), vibration(0, 4, 20))
    for tool, strength in TOOL_KNOCK.items():
        rule(ATTACK, "environment", [event("break_pulse"), f"ds4c:tool/{tool}"], feedback(0, strength))

    # Using items on blocks and entities.
    rule(USE, "environment", event("bucket"), feedback(4, 3))
    rule(USE, "environment", event("hoe_till"), vibration(3, 3, 18))
    rule(USE, "environment", event("spawn_egg"), weapon(3, 5, 4))
    rule(USE, "environment", event("shears_clip"), weapon(4, 6, 7))
    # Placing blocks has no trigger effect: it's very frequent and testing found any resistance unwelcome.

    rule(USE, "items", event("fishing_cast"), weapon(3, 5, 4))

    # Rockets have no trigger effect (testing: the kicks felt like the trigger spasming); the grips carry them.

    # Items in use. These replace Controlify's defaults for the same animations.
    rule(USE, "combat", "ds4c:crossbow_loaded", OFF)
    for level in range(DRAW_LEVELS - 1, -1, -1):
        for anim in ("bow", "trident"):
            rule(USE, "combat", ["ds4c:pull_through", use_anim(anim), bucket("draw", level)], pull_through_curve(level))
            rule(USE, "combat", [use_anim(anim), bucket("draw", level)], draw_curve(level))
        rule(USE, "combat", [use_anim("crossbow"), bucket("draw", level)],
             feedback_slope(2, 9, draw_strength(level), 8))
    rule(USE, "items", {"any_of": [use_anim("eat"), use_anim("drink")]}, feedback_slope(0, 9, 2, 6))
    # Shield raised: a firm stop, but not a wall, since it's often held for a long time.
    rule(USE, "combat", use_anim("block"), feedback(6, 6))

    # No horse-jump effect: the jump is charged with the jump button, not a trigger.


def held_item_baselines():
    """Main-hand baselines so the trigger already feels like the item before it's used."""
    # Crossbow: loaded is a wall (rare, deliberate), empty is light.
    rule(USE, "combat", "ds4c:holding_loaded_crossbow", feedback(4, 8), idle_only=True)
    rule(USE, "combat", main_hand({"items": "#c:tools/crossbow"}), feedback(4, 2), idle_only=True)
    # Bow: the start of a draw, so there's no jump in feel when the draw begins.
    rule(USE, "combat", ["ds4c:pull_through", main_hand({"items": "#c:tools/bow"})], pull_through_curve(0), idle_only=True)
    rule(USE, "combat", main_hand({"items": "#c:tools/bow"}), draw_curve(0), idle_only=True)
    rule(USE, "combat", main_hand({"items": "#c:tools/trident"}), feedback(3, 3), idle_only=True)
    rule(USE, "items", main_hand({"items": "minecraft:spyglass"}), feedback(5, 3), idle_only=True)


def attack_baselines():
    # Mace wind-up: resistance across the whole travel, growing with fall distance (rare, so up to a wall).
    for level, strength in [(3, 8), (2, 5), (1, 3)]:
        rule(ATTACK, "combat", ["ds4c:holding_mace", bucket("fall_charge", level)], feedback(0, strength))
    # Aiming a tool at a block it can harvest: soft, medium, hard. Frequent, so weight-level only.
    for level in range(3, 0, -1):
        rule(ATTACK, "environment", bucket("target_resistance", level), feedback(2, level))
    rule(ATTACK, "combat", "ds4c:holding_weapon", feedback(3, 2))


# Trigger strength setting: rules are duplicated at these percentages, gated on
# ds4c:trigger_strength/<percent>, which DS4C contributes when the setting is below 100%.
STRENGTH_LEVELS = (50, 75)
STRENGTH_FIELDS = ("strength", "amplitude", "start_strength", "end_strength", "snap_force")


def scaled(effect, percent):
    """The effect with every strength/amplitude field scaled, never dropping a non-zero value to 0."""
    out = dict(effect)
    for key in STRENGTH_FIELDS:
        if key in out and out[key] > 0:
            out[key] = max(1, round(out[key] * percent / 100))
    return out


def add_strength_variants():
    """Insert a scaled copy of each rule just before it, so the copy wins only at that setting."""
    result = []
    for r in RULES:
        if r["then"]["type"] != "off":
            for percent in STRENGTH_LEVELS:
                variant = json.loads(json.dumps(r))
                variant["if"]["all_of"].insert(0, f"ds4c:trigger_strength/{percent}")
                variant["then"] = scaled(r["then"], percent)
                result.append(variant)
        result.append(r)
    RULES[:] = result


def build():
    # Environmental and passive states (ice, magma, portals, rides, water...) are deliberately not on
    # the triggers: they have nothing to do with either trigger's action. The grips carry them.
    bursts()
    actions()
    held_item_baselines()
    attack_baselines()
    check()


def check():
    """Enforce the design rules that are mechanical."""
    for r in RULES:
        effect = r["then"]
        if effect.get("frequency", 0) > MAX_VIBRATION_HZ:
            raise ValueError(f"vibration above {MAX_VIBRATION_HZ} Hz: {r}")


def gallery_name(r):
    """A readable command name for a rule, e.g. r2_hit_crit, l2_use_anim_bow_draw_7."""
    parts = []
    for c in r["if"].get("all_of", []):
        if c == "ds4c:enabled" or str(c).startswith("ds4c:category/"):
            continue
        if isinstance(c, str):
            parts.append(c.split(":", 1)[1].removeprefix("event/"))
        elif "slot" in c:
            target = c.get("item") or c.get("block") or {}
            items = target.get("items") or target.get("blocks") or "item"
            items = items[0] if isinstance(items, list) else items
            name = items.split("/")[-1].split(":")[-1]
            if "components" in target:
                name += "_uncharged"
            parts.append(f"{c['slot'].split(':')[1]}_{name}")
        elif "any_of" in c:
            parts.append("_or_".join(x.split(":", 1)[1] for x in c["any_of"]))
    trigger = "l2" if r["for"] == USE else "r2"
    return f"{trigger}_" + "_".join(parts).replace("/", "_").replace(".", "_")


def gallery():
    """One top-priority rule per DS4C rule, switched on by ds4c:gallery/<name>.

    The /ds4c gallery trigger command opens that fact for a few seconds so each effect can be felt
    on its own. Gallery rules ignore categories and the master switch: they're a tuning tool.
    """
    entries, names = [], set()
    for r in RULES:
        if r["then"]["type"] == "off":
            continue
        name = gallery_name(r)
        base, n = name, 2
        while name in names:
            name, n = f"{base}_{n}", n + 1
        names.add(name)
        entries.append({"name": name, "trigger": "L2" if r["for"] == USE else "R2", "effect": r["then"]})
    rules = [{"for": USE if e["trigger"] == "L2" else ATTACK, "if": f"ds4c:gallery/{e['name']}", "then": e["effect"]}
             for e in entries]
    return rules, entries


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")
    print(f"wrote {path.relative_to(ROOT)}")


if __name__ == "__main__":
    build()
    gallery_rules, gallery_entries = gallery()
    add_strength_variants()
    rules = gallery_rules + RULES
    write(PACK / CONTEXTUAL / "facts/in_game.json", FACTS)
    write(PACK / CONTEXTUAL / "trigger_effect/in_game.json", {"rules": rules})
    write(NO_BOW_PACK / CONTEXTUAL / "facts/in_game.json", FACTS)
    write(NO_BOW_PACK / CONTEXTUAL / "trigger_effect/in_game.json",
          {"rules": [dict(r, then=without_bow(r["then"])) for r in rules]})
    (NO_BOW_PACK / "pack.mcmeta").write_text((PACK / "pack.mcmeta").read_text())
    write(GALLERY_INDEX, gallery_entries)
    print(f"{len(FACTS)} facts, {len(RULES)} rules, {len(gallery_rules)} gallery entries")
