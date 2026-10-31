# DS4C feedback coverage

What DS4C does for each game event or state, on the adaptive triggers and the grip HD haptics, and which events have no feedback at all. The reasoning behind each choice is in `haptics-review.md`.

> Trigger tables are generated: `python3 tools/coverage_triggers.py`. Regenerate after changing `tools/generate_rules.py`. The grip haptics and gaps sections are maintained by hand.

## How to read this

- **Priority:** for each trigger, the first matching rule wins, top to bottom. Bursts beat actions, which beat passive effects. Controlify's own defaults (items being used, off-hand fallbacks, attack-with-weapon) sit below all of these.
- **L2 is Use, R2 is Attack**, assuming the default bindings. A rule does nothing if its binding isn't on a trigger.
- **Trigger effect positions** run 0 (fully released) to 9 (fully pulled). Strengths and amplitudes run 1 to 8. Feedback is resistance from a position onward; Weapon is a resistance band between two positions that gives way; Vibration buzzes the trigger from a position onward; Slope is resistance that ramps between two positions; Bow is tension that builds and snaps back.
- **Durations:** event (`event/*`) effects last a few ticks (50 ms each); state effects last as long as the state holds.

## Trying effects on their own

The effect gallery plays one effect at a time, for tuning and noise checks:

- `/ds4c gallery trigger <name> [seconds]` holds a trigger effect (default 5 s). Names follow the rule's condition, e.g. `r2_swing`, `l2_use_anim_bow_draw_3`, `r2_explosion_3`; press Tab to list them. Controlify only applies trigger effects in-world with controller input active, so touch the controller after closing chat, then press the trigger.
- `/ds4c gallery haptic <name>` plays a grip event synth: `footstep_<surface>`, `footstep_sprint_<surface>`, `footstep_sprint_jump_<surface>`, `small_landing_<surface>`, `landing_<blocks>`, `mace_slam`, `damage_<type>`, `explosion_*`, `mining_<tool>`, `wrong_tool`, `shears_snip`, `lightning_near`/`lightning_far`, `fishing_*`.
- `/ds4c gallery sound <id>` plays a game sound at your position, so its passthrough and any overlay play as they would in game (e.g. `minecraft:block.note_block.harp`).
- `/ds4c gallery stop` ends the current trigger effect.

Category toggles and DS4C's switches still apply. Ambient loops and the heartbeat aren't in the gallery yet.

## Adaptive triggers

### Bursts (highest priority)

| Trigger | Category | When | Effect |
|---|---|---|---|
| R2 | combat | event/hit_crit | vibration pos 0 amp 8 30 Hz |
| R2 | combat | event/hit_knockback | vibration pos 0 amp 7 18 Hz |
| R2 | combat | event/hit_strong | vibration pos 0 amp 6 25 Hz |
| R2 | combat | event/hit_sweep | vibration pos 0 amp 5 30 Hz |
| R2 | combat | event/hit_weak | vibration pos 0 amp 3 30 Hz |
| R2 | combat | event/hit_no_damage | vibration pos 0 amp 2 30 Hz |
| R2 | combat | event/mace_land/N | vibration pos 0 amp 4..8 22 Hz (3 levels) |
| L2 + R2 | combat | event/explosion/N | vibration pos 0 amp 4..8 25 Hz (3 levels) |
| L2 | combat | event/shield_break | vibration pos 0 amp 8 20 Hz |
| L2 | combat | event/shield_block | vibration pos 5 amp 8 25 Hz |
| L2 | combat | event/crossbow_fire | vibration pos 2 amp 8 30 Hz |
| L2 | combat | event/crossbow_loaded | weapon 7-8 str 8 |
| L2 | combat | event/bow_release | vibration pos 2 amp 7 30 Hz |
| L2 | combat | event/trident_release | weapon 2-8 str 8 |
| R2 | combat | event/tool_warning_attack | vibration pos 4 amp 3 6 Hz |
| L2 | environment | event/tool_warning_use | vibration pos 4 amp 3 6 Hz |
| L2 | environment | event/bucket_lava | vibration pos 4 amp 4 25 Hz |
| L2 | items | chew | vibration pos 4 amp 3 8 Hz |
| L2 | items | fishing_biting | vibration pos 2 amp 6 25 Hz |

### Actions

| Trigger | Category | When | Effect |
|---|---|---|---|
| R2 | combat | event/swing | vibration pos 0 amp 4 30 Hz |
| R2 | environment | event/block_broken | vibration pos 0 amp 8 30 Hz |
| R2 | environment | event/break_pulse/N + tool/pickaxe | vibration pos 0 amp 1..8 30 Hz (8 levels) |
| R2 | environment | event/break_pulse/N + tool/shovel | vibration pos 0 amp 1..8 20 Hz (8 levels) |
| R2 | environment | event/break_pulse/N + tool/axe | vibration pos 0 amp 1..8 24 Hz (8 levels) |
| R2 | environment | event/break_pulse/N + tool/hoe | vibration pos 0 amp 1..8 16 Hz (8 levels) |
| R2 | environment | event/break_pulse/N + tool/shears | vibration pos 0 amp 1..8 35 Hz (8 levels) |
| R2 | environment | event/break_pulse/N + tool/mace | vibration pos 0 amp 1..8 22 Hz (8 levels) |
| R2 | environment | event/break_pulse/N + tool/other | vibration pos 0 amp 1..8 26 Hz (8 levels) |
| L2 | environment | event/bucket | feedback pos 4 str 5 |
| L2 | environment | event/hoe_till | vibration pos 3 amp 5 18 Hz |
| L2 | environment | event/spawn_egg | weapon 3-4 str 2 |
| L2 | environment | event/block_place | weapon 3-4 str 3 |
| L2 | environment | event/shears_clip | weapon 4-6 str 7 |
| L2 | items | event/fishing_cast | weapon 3-5 str 4 |
| L2 | movement | event/firework | vibration pos 0 amp 7 30 Hz |
| L2 | combat | crossbow_loaded | off |
| L2 | combat | use_anim/bow + draw/N | bow 2..7-8 str 2..8 snap 8 (4 levels) |
| L2 | combat | use_anim/trident + draw/N | bow 2..7-8 str 2..8 snap 2..8 (4 levels) |
| L2 | combat | use_anim/crossbow + draw/N | slope 2-9 str 2..8->8 (4 levels) |
| L2 | items | use_anim/eat or use_anim/drink | slope 0-9 str 2->6 |
| L2 | combat | use_anim/block | feedback pos 6 str 5 |
| L2 | movement | horse_jump/N | bow 2..7-8 str 2..8 snap 8 (4 levels) |

### Held-item baselines (idle only)

| Trigger | Category | When | Effect |
|---|---|---|---|
| L2 | combat | main_hand=#c:tools/crossbow *(L2: not using an item)* | feedback pos 4 str 2..8 (2 levels) |
| L2 | combat | main_hand=#c:tools/bow *(L2: not using an item)* | bow 7-8 str 2 snap 8 |
| L2 | combat | main_hand=#c:tools/trident *(L2: not using an item)* | feedback pos 3 str 3 |
| L2 | items | main_hand=minecraft:spyglass *(L2: not using an item)* | feedback pos 5 str 3 |
| L2 | environment | place_ready *(L2: not using an item)* | feedback pos 4 str 1 |

### Attack baselines

| Trigger | Category | When | Effect |
|---|---|---|---|
| R2 | combat | holding_mace + fall_charge/N | feedback pos 0 str 3..8 (3 levels) |
| R2 | environment | target_resistance/N | feedback pos 2 str 1..3 (3 levels) |
| R2 | combat | holding_weapon | feedback pos 3 str 2 |

Environmental and passive states (portal, ice, magma, hunger, rides, underwater, sticky ground) were removed from the triggers in the design review; the grips carry them.

## Grip HD haptics

Everything goes through `Haptics` (DS4C and Controlify switches, categories, master intensity), which also applies the priority budget: four tiers (danger > action > movement > ambient, from the category), at most 3 effects at once, and lower tiers ducked to 40% while an action or danger effect plays. Repeating cues and loop chunks never duck anything. At most 2 loops are audible; loops fade to 50% after 20 s without change. Effects with a direction are panned between the grips.

"Thump" is a decaying sine (an impact), "noise" is band-passed noise (a texture), "AM" is an amplitude-modulated sine (a throb). Synthesised content sits in 80–250 Hz.

DS4C expects Controlify's vibration to be off (legacy rumble mutes HD haptics); rows marked ⓒ replace a Controlify rumble effect.

### Game events (`HapticEvents`, from DS4C mixins)

| Event | Category | What plays |
|---|---|---|
| Footstep (toggle) | movement | Surface tap: hard surfaces click (180 Hz), wood knocks (120 Hz), sand/gravel/snow crunch (noise), soft ground thuds (85–90 Hz); alternates left/right; ×1.25 sprinting, ×1.6 within 15 ticks of a jump while sprinting |
| Landing from a jump or one-block drop (0.6–2 blocks) (toggle) | movement | A heavier surface tap |
| Landing from 2+ blocks | movement | 90 Hz thump + crunch, growing with the fall |
| Mace landing from 3+ blocks | combat | 80 Hz heavy thump + crackle |
| Damage ⓒ | combat | By cause: fall thump, fire crackle, drowning throb, wither/magic throb, freezing hiss, lightning crack, projectile thwack, anything else a 110 Hz hit. Panned towards the attacker |
| Explosion within 32 blocks of its edge ⓒ | combat | Crack + 80 Hz body + rolling aftershock, by distance and size, leaning towards it |
| Mining, per crack stage ⓒ | environment | By tool: pickaxe chink, shovel scrape, axe chop, hoe, shears, hand; builds as the block gives |
| Started mining with the wrong tool | environment | Dull scrape |
| Shears used on an entity | environment | Two snips |
| Lightning within 16 blocks ⓒ | ambient | Crack, then a rolling thump, leaning towards it |
| Fishing: fish approaching, nibble, bobber bob ⓒ | items | Soft 110–160 Hz taps |

### Continuous and periodic (`HapticTickHandler`)

| State | Category | What plays |
|---|---|---|
| Health at 3 hearts or less | combat | Lub-dub heartbeat, faster as health drops |
| Bow at full draw | combat | One "ready" click, then a faint arm tremble |
| Holding a compass | items | Ticks that speed up as you face the target and lean towards its side |
| Holding a clock at noon/midnight | items | One click (bright at noon, dull at midnight) |
| Elytra flight | movement | Wind noise loop by speed, with gusts |
| Pushing through soul sand, honey, cobweb, powder snow, berry bushes ⓒ | movement | A texture per block while moving |
| Minecart | movement | Rolling bed by speed + a click at every rail join |
| Boat | movement | Swell by speed (paddle sounds pass through) |
| Horse | movement | Hoof beats at stride rate (three-beat canter when fast), landing thump |
| Underwater | ambient | Swelling 90 Hz hum, stronger the deeper you are |
| Rain under open sky | ambient | Random raindrop taps, density by rain/storm strength |
| Standing on magma (not sneaking) | ambient | 4 Hz heat throb |
| High ground (y > 180) under open sky | ambient | Wind noise by altitude, with gusts |
| Conduit Power, near a beacon, near bees | ambient | Soft pulses / a buzz towards the nearest bee |
| Standing in a portal | ambient | Swirl building over 4 s, pulsing faster |
| Changing dimension | ambient | Long sweep |

### Sound passthrough and overlays (`HapticSoundDefaults`)

About 85 sound mappings. Passthrough unless noted, each with a category, intensity and range:

- **Combat:** player attack sounds; your arrow hitting a player (overlay); bow, crossbow and trident shots; crossbow loading clicks; shield break ⓒ, totem of undying, Warden heartbeat and roar, sculk shrieker (overlays).
- **Environment:** every `*.break` and `*.place` sound within 6 blocks; doors, trapdoors, gates, chests, barrels, shulker boxes, buttons, levers and pressure plates within 3 blocks.
- **Items:** goat horns; eating and drinking; item breaking ⓒ (overlay); anvil ⓒ, brewing stand, blast furnace, crafter, bone meal, enchanting table (plus overlay), XP bottle; fishing throw, splash and retrieve; note blocks and bells (synth only).
- **UI:** item and XP pickups (plus overlay), level-up and challenge toasts (plus overlays), button clicks, villager "yes", loom/stonecutter/cartography results.
- **Ambient:** lightning; Ender Dragon growl (overlay) and death ⓒ (passthrough + long overlay); Wither spawn ⓒ (overlay) and sounds; phantom swoop; squid ink; Enderman teleport; beacon and conduit (de)activation; End portal frame/spawn; Nether portal travel, trigger and ambient; furnace crackle.
- **Movement:** firework launch; slime-block landing; horse jump and land; boat paddles; swimming and splashes ⓒ (synth overlays only).

Not covered after turning Controlify's vibration off: server-sent rumble (Controlify's networking API) and Controlify's GUI vibrations.

## Gaps: events with no DS4C feedback

- **Movement:** ladders, vines and scaffolding (beyond their step sounds); sneaking at an edge; elytra take-off and wall collisions; riptide; strider and pig riding.
- **Combat and danger:** death and respawn; goat ram; being knocked back (felt only as the hit).
- **World and items:** sleeping; jukebox music; thunder without a strike nearby.
