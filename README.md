<img src="docs/icon.svg" width="96" alt="" align="right">

# DualSense for Controlify

DualSense for Controlify (DS4C) is a client-side Minecraft mod that adds DualSense feedback on
top of [Controlify](https://github.com/isXander/Controlify):

- **HD haptics from the game.** The grip actuators play game sounds that work as touch (eating,
  goat horns, attacks, thunder) and synthesised effects for everything else: footsteps shaped by
  the ground underfoot, landings, mining voiced by the tool, damage by cause, rain, water,
  rides, the heartbeat at low health, and more. Directional effects lean towards one grip.
- **Adaptive triggers.** Resistance and vibration on L2 and R2 for drawing a bow or crossbow,
  swinging, mining, eating, the mace, explosions and so on. Bows and tridents can shoot by
  pressing the trigger through the resistance.
- **Controller speaker.** A few sounds that happen in your hands (bows, crossbows, a tool
  breaking, bundles, books, the spyglass, the level-up chime) play from the DualSense speaker
  instead of your speakers or headphones, and pickups get a small echo from the controller.

[docs/coverage.md](docs/coverage.md) lists what each game event does on the triggers and grips.

## Requirements

- Minecraft 26.3
- Fabric (with Fabric API) or NeoForge
- Controlify 3.5.3 or newer
- A DualSense controller
- Optional on Fabric: Mod Menu, to open the settings screen

## USB and Bluetooth

Adaptive triggers work over USB and Bluetooth. HD haptics and the controller speaker use the
DualSense's audio device, which only exists over USB on most systems.

- **USB:** on Linux with PipeWire, the controller's card profile and channel map usually need a
  change; see [docs/linux-hd-haptics.md](docs/linux-hd-haptics.md).
- **Bluetooth on Linux:** run [DS4L](https://github.com/Mac898/DS4L), which gives a Bluetooth
  DualSense the same audio device as a wired one.
- **Bluetooth elsewhere:** no HD haptics or speaker; triggers still work.

If a DualSense is connected but HD haptics can't play, DS4C says so once in chat.

## Controlify settings

- Turn **HD Haptics** on.
- Turn **Allow Vibrations** off. DS4C plays its own versions of the events Controlify rumbles for
  (damage, explosions, mining, and so on), so with both on you feel them twice.

## Settings

Open DS4C's settings from Mod Menu (Fabric) or the mod list (NeoForge). They are saved in
`config/ds4c.json`.

- **Adaptive triggers:** trigger strength (off, 50%, 75%, 100%) and pull through to shoot.
- **HD haptics:** master intensity, synthesised effect intensity, and the distance range over
  which sounds fade out.
- **Controller speaker:** volume (0 keeps every sound on your main audio) and a delay for when
  the speaker plays over Bluetooth.
- **Categories:** a strength for movement, combat, ambient, items, environment and interface
  feedback, plus footsteps. 0 turns a category off, its trigger effects included.
- **Debug:** the effect gallery, off by default. When on, `/ds4c gallery` plays one trigger
  effect, grip haptic or game sound at a time, which helps when tuning or checking a setup. See
  [docs/coverage.md](docs/coverage.md#trying-effects-on-their-own).

Other mods can route more of their own sounds to the controller speaker through
`au.mltech.ds4c.api.SpeakerRouting`.

## Building

```sh
./gradlew build
```

The jars are written to `fabric/build/libs` and `neoforge/build/libs`. Java 25 is required.

The trigger rules in `common/src/main/resources/resourcepacks/effects` are generated: edit
`tools/generate_rules.py`, run `python3 tools/generate_rules.py` from the repository root, and
commit the generated JSON with it.

## Licence

LGPL-3.0-or-later. See [LICENSE.txt](LICENSE.txt).

## Credits

- [Controlify](https://github.com/isXander/Controlify) by isXander, which DS4C builds on for
  controller input, trigger effects and HD haptics output.
- Built from the [MultiLoader Template](https://github.com/jaredlll08/MultiLoader-Template).
