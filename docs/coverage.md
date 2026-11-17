# DS4C feedback coverage

What each game event or state does on the adaptive triggers and the grips, and which events do nothing yet.

> The trigger tables come from `python3 tools/coverage_triggers.py`. Regenerate them after changing
> `tools/generate_rules.py`. Everything else is written by hand.

## How to read this

- **Priority:** on each trigger the first matching rule wins, reading top to bottom. Bursts beat actions, which beat
  baselines. Controlify's own defaults (items being used, off-hand fallbacks, attack-with-weapon) sit below all of
  these.
- **Bindings:** L2 is Use and R2 is Attack, as in the default bindings. A rule does nothing if its binding isn't on a
  trigger.
- **Numbers:** positions run from 0 (released) to 9 (fully pulled). Strengths and amplitudes run from 1 to 8.
- **Effects:**
  - `feedback`: resistance from a position onward.
  - `weapon`: resistance between two positions that then gives way.
  - `vibration`: buzzes the trigger from a position onward.
  - `slope`: resistance that ramps between two positions.
  - `bow`: tension that builds and then snaps back.
- **Durations:** `event/*` effects last a few ticks (50 ms each). State effects last as long as the state holds.

## Trying effects on their own

The effect gallery plays one effect at a time, for tuning and noise checks. Its commands only exist while **Effect
gallery** is on, under Debug in DS4C's settings.

- `/ds4c gallery trigger <name> [seconds]` holds a trigger effect (default 5 s). Names follow the rule's condition, e.g.
  `r2_swing`, `l2_use_anim_bow_draw_3`, `r2_explosion_3`; press Tab to list them. Controlify only applies trigger
  effects in-world while the controller is the active input, so touch the controller after closing chat, then press
  the trigger.
- `/ds4c gallery haptic <name>` plays one synthesised grip effect: `footstep_<surface>`, `footstep_sprint_<surface>`,
  `footstep_sprint_jump_<surface>`, `small_landing_<surface>`, `landing_<blocks>`, `mace_slam`, `damage_<type>`,
  `explosion_*`, `mining_<tool>`, `wrong_tool`, `shears_snip`, `lightning_near`/`lightning_far`, `fishing_*`.
- `/ds4c gallery sound <id>` plays a game sound at your position, so its passthrough and any overlay play as they would
  in game (e.g. `minecraft:block.note_block.harp`).
- `/ds4c gallery stop` ends the current trigger effect.

Category strengths and DS4C's other settings still apply, so a category at 0 plays nothing. Ambient loops and the
heartbeat aren't in the gallery yet.

## Adaptive triggers

Controlify 3.5.3 has no bow trigger effect. There, every `bow` below plays as `weapon` with the same positions and
strength, and doesn't snap back.

### Bursts (highest priority)

| Trigger | Category    | When                      | Effect                                    |
|---------|-------------|---------------------------|-------------------------------------------|
| R2      | combat      | event/hit_crit            | vibration pos 0 amp 8 30 Hz               |
| R2      | combat      | event/hit_knockback       | vibration pos 0 amp 8 18 Hz               |
| R2      | combat      | event/hit_strong          | vibration pos 0 amp 7 22 Hz               |
| R2      | combat      | event/hit_sweep           | vibration pos 0 amp 6 25 Hz               |
| R2      | combat      | event/hit_weak            | vibration pos 0 amp 4 30 Hz               |
| R2      | combat      | event/hit_no_damage       | vibration pos 0 amp 3 30 Hz               |
| R2      | combat      | event/mace_land/N         | vibration pos 0 amp 4..8 22 Hz (3 levels) |
| L2 + R2 | combat      | event/explosion/N         | vibration pos 0 amp 4..8 25 Hz (3 levels) |
| L2      | combat      | event/shield_break        | vibration pos 0 amp 8 20 Hz               |
| L2      | combat      | event/shield_block        | vibration pos 5 amp 8 25 Hz               |
| L2      | combat      | event/crossbow_fire       | vibration pos 2 amp 8 30 Hz               |
| L2      | combat      | event/crossbow_loaded     | weapon 7-8 str 8                          |
| L2      | combat      | event/bow_release         | vibration pos 2 amp 7 30 Hz               |
| L2      | combat      | event/trident_release     | weapon 2-8 str 8                          |
| R2      | combat      | event/tool_warning_attack | vibration pos 4 amp 3 6 Hz                |
| L2      | environment | event/tool_warning_use    | vibration pos 4 amp 3 6 Hz                |
| L2      | environment | event/bucket_lava         | vibration pos 4 amp 3 25 Hz               |
| L2      | items       | chew                      | vibration pos 4 amp 3 8 Hz                |
| L2      | items       | fishing_biting            | vibration pos 2 amp 4 25 Hz               |

### Actions

| Trigger | Category    | When                                     | Effect                                 |
|---------|-------------|------------------------------------------|----------------------------------------|
| R2      | combat      | event/swing_full                         | vibration pos 0 amp 7 18 Hz            |
| R2      | combat      | event/swing_weak                         | vibration pos 0 amp 2 30 Hz            |
| R2      | environment | event/block_broken                       | vibration pos 0 amp 4 20 Hz            |
| R2      | environment | event/break_pulse + tool/pickaxe         | feedback pos 0 str 6                   |
| R2      | environment | event/break_pulse + tool/shovel          | feedback pos 0 str 5                   |
| R2      | environment | event/break_pulse + tool/axe             | feedback pos 0 str 7                   |
| R2      | environment | event/break_pulse + tool/hoe             | feedback pos 0 str 4                   |
| R2      | environment | event/break_pulse + tool/shears          | feedback pos 0 str 4                   |
| R2      | environment | event/break_pulse + tool/mace            | feedback pos 0 str 6                   |
| R2      | environment | event/break_pulse + tool/other           | feedback pos 0 str 5                   |
| L2      | environment | event/bucket                             | feedback pos 4 str 3                   |
| L2      | environment | event/hoe_till                           | vibration pos 3 amp 3 18 Hz            |
| L2      | environment | event/spawn_egg                          | weapon 3-5 str 4                       |
| L2      | environment | event/shears_clip                        | weapon 4-6 str 7                       |
| L2      | items       | event/fishing_cast                       | weapon 3-5 str 4                       |
| L2      | combat      | crossbow_loaded                          | off                                    |
| L2      | combat      | pull_through + use_anim/bow + draw/N     | bow 2..7-8 str 2..8 snap 8 (4 levels)  |
| L2      | combat      | use_anim/bow + draw/N                    | slope 2..5-9 str 1..4->2..8 (4 levels) |
| L2      | combat      | pull_through + use_anim/trident + draw/N | bow 2..7-8 str 2..8 snap 8 (4 levels)  |
| L2      | combat      | use_anim/trident + draw/N                | slope 2..5-9 str 1..4->2..8 (4 levels) |
| L2      | combat      | use_anim/crossbow + draw/N               | slope 2-9 str 2..8->8 (4 levels)       |
| L2      | items       | use_anim/eat or use_anim/drink           | slope 0-9 str 2->6                     |
| L2      | combat      | use_anim/block                           | feedback pos 6 str 6                   |

### Held-item baselines (idle only)

| Trigger | Category | When                                                            | Effect               |
|---------|----------|-----------------------------------------------------------------|----------------------|
| L2      | combat   | holding_loaded_crossbow *(L2: not using an item)*               | feedback pos 4 str 8 |
| L2      | combat   | main_hand=#c:tools/crossbow *(L2: not using an item)*           | feedback pos 4 str 2 |
| L2      | combat   | pull_through + main_hand=#c:tools/bow *(L2: not using an item)* | bow 7-8 str 2 snap 8 |
| L2      | combat   | main_hand=#c:tools/bow *(L2: not using an item)*                | slope 5-9 str 1->2   |
| L2      | combat   | main_hand=#c:tools/trident *(L2: not using an item)*            | feedback pos 3 str 3 |
| L2      | items    | main_hand=minecraft:spyglass *(L2: not using an item)*          | feedback pos 5 str 3 |

### Attack baselines

| Trigger | Category    | When                         | Effect                             |
|---------|-------------|------------------------------|------------------------------------|
| R2      | combat      | holding_mace + fall_charge/N | feedback pos 0 str 3..8 (3 levels) |
| R2      | environment | target_resistance/N          | feedback pos 2 str 1..3 (3 levels) |
| R2      | combat      | holding_weapon               | feedback pos 3 str 2               |

Passive states (portal, ice, magma, hunger, rides, underwater, sticky ground) have no trigger effect. The grips handle
them.

## Grip HD haptics

Every grip effect goes through `Haptics`, which checks the DS4C and Controlify switches, scales by the master and
category intensity, and keeps to a priority budget:

- Each category has a tier: danger > action > movement > ambient.
- At most 3 effects play at once.
- While a danger or action effect plays, lower tiers drop to 40%. Repeating cues and loop chunks never duck anything.
- At most 2 loops play at once, and a loop fades to 50% after 20 s without change.

Effects with a direction are panned between the grips.

In the tables, a thump is a decaying sine (an impact), noise is band-passed noise (a texture) and a throb is an
amplitude-modulated sine. Synthesised effects stay between 80 and 250 Hz.

Rows marked ⓒ replace one of Controlify's rumble effects. Turn Controlify's **Allow Vibrations** off, or you feel those
events twice.

### Game events (`HapticEvents`, from DS4C mixins)

| Event                                                                     | Category    | What plays                                                                                                                                                                                                             |
|---------------------------------------------------------------------------|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Footstep (Footsteps strength; 0 is off)                                   | movement    | Surface tap: hard surfaces click (180 Hz), wood knocks (120 Hz), sand/gravel/snow crunch (noise), soft ground thuds (85–90 Hz); alternates left/right; ×1.25 sprinting, ×1.6 within 15 ticks of a jump while sprinting |
| Landing from a jump or one-block drop (0.6–2 blocks) (Footsteps strength) | movement    | A heavier surface tap                                                                                                                                                                                                  |
| Landing from 2+ blocks                                                    | movement    | 90 Hz thump + crunch, growing with the fall                                                                                                                                                                            |
| Mace landing from 3+ blocks                                               | combat      | 80 Hz heavy thump + crackle                                                                                                                                                                                            |
| Damage ⓒ                                                                  | combat      | By cause: fall thump, fire crackle, drowning throb, wither/magic throb, freezing hiss, lightning crack, projectile thwack, anything else a 110 Hz hit. Panned towards the attacker                                     |
| Explosion within 32 blocks of its edge ⓒ                                  | combat      | Crack + 80 Hz body + rolling aftershock, by distance and size, leaning towards it                                                                                                                                      |
| Mining, per crack stage ⓒ                                                 | environment | By tool: pickaxe chink, shovel scrape, axe chop, hoe, shears, hand; builds as the block gives                                                                                                                          |
| Placing a block                                                           | environment | A low seating thunk (75 Hz) under a tap voiced by the placed block's material                                                                                                                                          |
| Started mining with the wrong tool                                        | environment | Dull scrape                                                                                                                                                                                                            |
| Shears used on an entity                                                  | environment | Two snips                                                                                                                                                                                                              |
| Lightning within 16 blocks ⓒ                                              | ambient     | Crack, then a rolling thump, leaning towards it                                                                                                                                                                        |
| Fishing: fish approaching, nibble, bobber bob ⓒ                           | items       | Soft 110–160 Hz taps                                                                                                                                                                                                   |

### Continuous and periodic (`HapticTickHandler`)

| State                                                                        | Category | What plays                                                                                                                                                                                     |
|------------------------------------------------------------------------------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Health at 3 hearts or less                                                   | combat   | Lub-dub heartbeat, faster as health drops                                                                                                                                                      |
| Bow at full draw                                                             | combat   | One "ready" click, then a faint arm tremble                                                                                                                                                    |
| Holding a compass                                                            | items    | Ticks that speed up as you face the target and lean towards its side                                                                                                                           |
| Holding a clock at noon/midnight                                             | items    | One click (bright at noon, dull at midnight)                                                                                                                                                   |
| Elytra flight                                                                | movement | Wind noise loop by speed, with gusts                                                                                                                                                           |
| Pushing through soul sand, honey, slime, cobweb, powder snow, berry bushes ⓒ | movement | A texture per block while moving                                                                                                                                                               |
| Minecart                                                                     | movement | Rolling bed by speed + a click at every rail join                                                                                                                                              |
| Boat                                                                         | movement | A faint swell by speed (paddle strokes are a synth overlay)                                                                                                                                    |
| Horse                                                                        | movement | Hoof beats at stride rate (three-beat canter when fast), landing thump                                                                                                                         |
| Underwater                                                                   | ambient  | Swelling 90 Hz hum, stronger the deeper you are, mostly when still                                                                                                                             |
| Rain under open sky                                                          | ambient  | Layered raindrops (small, full and heavy, some splashing into a second tap) over a faint wash; density by rain/storm strength with a slow random ebb and flow; fades to a fifth when unchanged |
| Standing on magma (not sneaking)                                             | ambient  | 2 Hz heat throb                                                                                                                                                                                |
| High ground (y > 180) under open sky                                         | ambient  | Wind noise by altitude, with gusts                                                                                                                                                             |
| Conduit Power, near a beacon, near bees                                      | ambient  | Soft pulses / a buzz towards the nearest bee                                                                                                                                                   |
| Standing in a portal                                                         | ambient  | Swirl building over 4 s, pulsing faster                                                                                                                                                        |
| Changing dimension                                                           | ambient  | Long sweep                                                                                                                                                                                     |

### Sound passthrough and overlays (`HapticSoundDefaults`)

About 85 game sounds are mapped, each with a category, intensity and range. Passthrough plays the sound itself through
the grips; an overlay is a synthesised effect played with it. Everything below is passthrough unless noted.

- **Combat:** player attack sounds; your arrow hitting a player (overlay); bow, crossbow and trident shots; crossbow
  loading clicks; shield break ⓒ, totem of undying, Warden heartbeat and roar, sculk shrieker (overlays).
- **Environment:** every `*.break` and `*.place` sound within 6 blocks; doors, trapdoors, gates, chests, barrels,
  shulker boxes, buttons, levers and pressure plates within 6 blocks. Buttons and pressure plates also get a synth click
  overlay (a snap in, a press down, lighter on release), since their sounds barely pass through.
- **Items:** goat horns; eating and drinking; item breaking ⓒ (overlay); anvil ⓒ, brewing stand, blast furnace, crafter,
  bone meal, enchanting table (plus overlay), XP bottle; fishing throw, splash and retrieve; note blocks and bells
  (synth only).
- **UI:** item and XP pickups (plus overlay), level-up and challenge toasts (plus overlays), button clicks, villager
  "yes", loom/stonecutter/cartography results.
- **Ambient:** lightning, and thunder (overlay of two or three randomised low rumbles, at any distance); Ender Dragon
  growl (overlay) and death ⓒ (passthrough + long overlay); Wither spawn ⓒ (overlay) and sounds; phantom swoop; squid
  ink; Enderman teleport; beacon and conduit (de)activation; End portal frame/spawn; Nether portal travel, trigger and
  ambient; furnace crackle.
- **Movement:** firework launch (plus an overlay: a kick, then a surging thrust); slime-block landing; horse jump and
  land; boat paddles (synth overlay); swimming and splashes ⓒ (synth overlays only).

Turning Controlify's vibration off also loses two things DS4C doesn't replace: rumble sent by servers through
Controlify's networking API, and Controlify's GUI vibrations.

## Gaps

Events with no DS4C feedback yet:

- **Movement:** ladders, vines and scaffolding (beyond their step sounds); sneaking at an edge; elytra take-off and wall
  collisions; riptide; strider and pig riding.
- **Combat and danger:** death and respawn; goat ram; being knocked back (felt only as the hit).
- **World and items:** sleeping; jukebox music.

## Controller speaker

When Speaker volume is above 0 and the controller's audio device is available, some of the player's own sounds move
from the main mix to the controller speaker, high-passed at 250 Hz. They still show as subtitles. A sound that hasn't
been decoded yet stays on the main mix.

- **Moved:** arrow shoot, crossbow shoot, crossbow loading and quick charge, item break, spyglass use and stop, book
  page turn, bundle insert, insert fail, remove and drop, and the level-up chime.
- **Echoed:** item and XP orb pickups stay on the main mix. The speaker plays an echo 80 ms later (minus the Speaker
  delay setting) at 55% volume and 1.25× pitch.
