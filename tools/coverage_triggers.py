#!/usr/bin/env python3
"""Print a markdown table of every DS4C trigger rule, grouped by priority section.

Reuses generate_rules.py so the table always matches what ships. Usage:
  python3 tools/coverage_triggers.py > triggers.md
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_rules as g  # noqa: E402

SECTIONS = [
    ("Bursts (highest priority)", g.bursts),
    ("Actions", g.actions),
    ("Held-item baselines (idle only)", g.held_item_baselines),
    ("Attack baselines", g.attack_baselines),
]


def describe_condition(predicate):
    parts = [c for c in predicate.get("all_of", []) if c not in ("ds4c:enabled",) and not str(c).startswith("ds4c:category/")]
    out = []
    for c in parts:
        if isinstance(c, str):
            out.append(c.replace("ds4c:", ""))
        elif "slot" in c:
            item = c.get("item") or c.get("block") or c.get("entity")
            out.append(f"{c['slot'].split(':')[1]}={item.get('items') or item.get('blocks') or item}")
        elif "any_of" in c:
            out.append(" or ".join(x.replace("ds4c:", "") for x in c["any_of"]))
        else:
            out.append(str(c))
    text = " + ".join(out)
    if predicate.get("none_of"):
        text += " (idle)"
    return text


def describe_effect(e):
    t = e["type"]
    if t == "feedback":
        return f"feedback pos {e['position']} str {e['strength']}"
    if t == "vibration":
        return f"vibration pos {e['position']} amp {e['amplitude']} {e['frequency']} Hz"
    if t == "weapon":
        return f"weapon {e['start_position']}-{e['end_position']} str {e['strength']}"
    if t == "feedback_slope":
        return f"slope {e['start_position']}-{e['end_position']} str {e['start_strength']}->{e['end_strength']}"
    if t == "bow":
        return f"bow {e['start_position']}-{e['end_position']} str {e['strength']} snap {e['snap_force']}"
    return t


BUCKET = re.compile(r"/(\d+)")


def summarise_effects(effects):
    """One description for a family of bucketed effects: numeric fields become min-max ranges."""
    if len(effects) == 1:
        return describe_effect(effects[0])
    merged = {}
    for key in effects[0]:
        values = sorted({e[key] for e in effects})
        merged[key] = values[0] if len(values) == 1 else f"{values[0]}..{values[-1]}"
    return describe_effect(merged) + f" ({len(effects)} levels)"


def main():
    for title, fn in SECTIONS:
        g.RULES.clear()
        fn()
        # Group rules that differ only by a bucket number (and merge L2/R2 duplicates).
        groups = {}
        for r in g.RULES:
            category = next(c.split("/")[1] for c in r["if"]["all_of"] if str(c).startswith("ds4c:category/"))
            condition = BUCKET.sub("/N", describe_condition(r["if"]))
            idle = condition.endswith(" (idle)")
            condition = condition.removesuffix(" (idle)")
            key = (category, condition, r["then"]["type"])
            entry = groups.setdefault(key, {"triggers": [], "effects": [], "idle": False})
            trigger = "L2" if r["for"] == g.USE else "R2"
            if trigger not in entry["triggers"]:
                entry["triggers"].append(trigger)
            entry["idle"] |= idle
            if r["then"] not in entry["effects"]:
                entry["effects"].append(r["then"])
        print(f"### {title}\n")
        print("| Trigger | Category | When | Effect |")
        print("|---|---|---|---|")
        for (category, condition, _), entry in groups.items():
            when = condition + (" *(L2: not using an item)*" if entry["idle"] else "")
            print(f"| {' + '.join(entry['triggers'])} | {category} | {when} | {summarise_effects(entry['effects'])} |")
        print()


if __name__ == "__main__":
    main()
