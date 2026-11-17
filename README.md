<img src="docs/icon.svg" width="96" alt="" align="right">

# DualSense for Controlify

DualSense for Controlify (DS4C) is a client-side Minecraft mod that adds DualSense feedback on
top of [Controlify](https://github.com/isXander/Controlify):

- **HD haptics.** Some game sounds play straight through the grips (eating, goat horns, attacks,
  thunder). Everything else is synthesised: footsteps that change with the ground, landings,
  mining that changes with the tool, damage by cause, rain, water, rides, a heartbeat at low
  health, and more. Effects with a direction are panned towards that grip.
- **Adaptive triggers.** Resistance and vibration on L2 and R2 for bows and crossbows, swinging,
  mining, eating, the mace, explosions and so on. Bows and tridents can fire when you pull the
  trigger through the resistance.
- **Controller speaker.** A few handheld sounds (bows, crossbows, a tool breaking, bundles,
  books, the spyglass) and the level-up chime play from the DualSense speaker instead of your
  speakers or headphones. Pickups get a short echo from the controller.

[docs/coverage.md](docs/coverage.md) lists what each game event does on the triggers and grips.

## Requirements

- Minecraft 26.3
- Fabric (with Fabric API) or NeoForge
- Controlify 3.5.3 or a later 3.5.x
- A DualSense controller
- Optional on Fabric: Mod Menu, to open the settings screen

DS4C hooks into Controlify internals for HD haptics and the speaker, so each Controlify minor
version needs a matching DS4C release. If a hook doesn't apply, that feature turns off and the
log says which one.

Pull through to shoot works best on a Controlify with the bow trigger effect, which gives way and
then pushes back. 3.5.3 doesn't have it, so DS4C uses the weapon effect there, which gives way
but doesn't push back.

## USB and Bluetooth

Adaptive triggers work over USB and Bluetooth. HD haptics and the controller speaker need the
DualSense's audio device, which on most systems only exists over USB.

- **USB:** on Linux with PipeWire, the controller's card profile and channel map usually need
  changing; see [docs/linux-hd-haptics.md](docs/linux-hd-haptics.md).
- **Bluetooth on Linux:** run [DS4L](https://github.com/Mac898/DS4L), which gives a Bluetooth
  DualSense the same audio device as a wired one.
- **Bluetooth elsewhere:** triggers only.

If a DualSense is connected but HD haptics can't play, DS4C says so once in chat.

## Controlify settings

- Turn **HD Haptics** on.
- Turn **Allow Vibrations** off. DS4C replaces the events Controlify rumbles for (damage,
  explosions, mining and so on), so with both on you feel them twice.

## Settings

Open DS4C's settings from Mod Menu (Fabric) or the mod list (NeoForge). They are saved in
`config/ds4c.json`.

- **Adaptive triggers:** trigger strength (off, 50%, 75%, 100%) and pull through to shoot.
- **HD haptics:** master intensity, synthesised effect intensity, and the distance over which
  sounds fade out.
- **Controller speaker:** volume (0 keeps every sound on your main audio) and a delay for when
  the speaker plays over Bluetooth.
- **Categories:** a strength each for movement, combat, ambient, items, environment, interface
  and footsteps. 0 turns a category off, trigger effects included.
- **Debug:** the effect gallery, off by default. When on, `/ds4c gallery` plays one trigger
  effect, grip haptic or game sound at a time, for tuning or checking a setup. See
  [docs/coverage.md](docs/coverage.md#trying-effects-on-their-own).

Other mods can send their own sounds to the controller speaker through
`au.mltech.ds4c.api.SpeakerRouting`.

## Building

```sh
./gradlew build
```

Needs Java 25. The jars end up in `fabric/build/libs` and `neoforge/build/libs`.

The trigger rules in `common/src/main/resources/resourcepacks/effects` and `effects_no_bow` are
generated. To change them, edit `tools/generate_rules.py`, run `python3 tools/generate_rules.py`
from the repository root, and commit the generated JSON too.

## Licence

LGPL-3.0-or-later. See [LICENSE.txt](LICENSE.txt).

## Credits

- [Controlify](https://github.com/isXander/Controlify) by isXander, which DS4C builds on for
  controller input, trigger effects and HD haptics output.
- Built from the [MultiLoader Template](https://github.com/jaredlll08/MultiLoader-Template).
