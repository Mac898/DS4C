# HD haptics on Linux

HD haptics and the controller speaker go through Controlify to the DualSense's audio device. It
has four output channels: the speaker on 1–2 and the left and right grip actuators on 3–4.
Controlify only uses the device if it offers all four.

- **USB:** the controller is its own sound card, but PipeWire usually needs two changes; see
  [USB](#usb).
- **Bluetooth:** stock Linux has no audio device for the controller. DS4L adds one; see
  [Bluetooth](#bluetooth).

If a DualSense is connected and DS4C can't find HD haptics, it says so once in chat and in the
log.

## Controlify settings

In Controlify's controller settings:

- Turn **HD Haptics** on.
- Turn **Allow Vibrations** off. DS4C replaces the events Controlify rumbles for (damage,
  explosions, mining and so on), so with both on you feel them twice.

## USB

### 1. Card profile

PipeWire's default profile for the controller (`HiFi`, from alsa-ucm-conf) only exposes a mono
speaker, so Controlify finds no 4-channel device. Switch the card to `pro-audio`:

```sh
pactl list cards short | grep -i dualsense                  # the card name is the second column
pactl set-card-profile <card name> pro-audio
```

Or pick "Pro Audio" for the controller in your desktop's sound settings.

alsa-ucm-conf releases from January 2026 add a "Direct" profile for the DualSense, which exposes
all four channels with the right positions. If your distribution has it, use it instead of
`pro-audio` and skip step 2.

### 2. Channel positions

With `pro-audio`, PipeWire labels the four channels `AUX0`–`AUX3` and remixes the haptics stream
onto what it takes to be the front pair. You then hear the haptics from the controller speaker
instead of feeling them. Label the channels with a WirePlumber rule in
`~/.config/wireplumber/wireplumber.conf.d/51-dualsense-haptics.conf`:

```
monitor.alsa.rules = [
  {
    matches = [
      { node.name = "~alsa_output.usb-Sony_Interactive_Entertainment_DualSense.*pro-output.*" }
    ]
    actions = {
      update-props = {
        audio.position = [ FL, FR, RL, RR ]
      }
    }
  }
]
```

Restart WirePlumber (`systemctl --user restart wireplumber`), replug the controller, and check
the channel map:

```sh
pactl list sinks | grep -A30 pro-output | grep "Channel Map"
# Channel Map: front-left,front-right,rear-left,rear-right
```

## Bluetooth

[DS4L](https://github.com/Mac898/DS4L) is a userspace daemon that gives a Bluetooth DualSense the
same 4-channel playback device as a wired one, with the channels already labelled, so the USB
steps above don't apply. Build and install it as its README describes (`./install.sh` sets up a
systemd user service), then connect the controller and start the game.

- The controller speaker plays slightly later over Bluetooth than over USB. If the pickup echo
  sounds like a separate sound, raise DS4C's **Speaker delay** setting.
- If haptics skip every few seconds, see the Bluetooth adapter notes in DS4L's README.

## Checking it works

Walk on stone. You should feel a light tap in the grips for each step and hear nothing from the
controller speaker. With **Effect gallery** turned on under Debug in DS4C's settings,
`/ds4c gallery haptic footstep_stone` plays a single tap.
