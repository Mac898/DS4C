# HD haptics on Linux

DS4C's grip haptics go through Controlify to the DualSense's USB audio interface, which has four
output channels: the controller speaker on 1–2 and the left and right grip actuators on 3–4.
Controlify only uses a DualSense audio device that offers all four channels. On PipeWire, two
things can get in the way.

If DS4C can't find HD haptics while a DualSense is connected, it says so once in chat and in the log.

## Requirements on every platform

- Connect the DualSense with a USB cable. HD haptics don't work over Bluetooth.
- Turn on **HD Haptics** in Controlify's controller settings.
- Turn off **Allow Vibrations** in Controlify's controller settings. DS4C plays its own versions of
  the events Controlify rumbles for (damage, explosions, mining...), so with both on you feel each
  of them twice.

## 1. The card profile

The default PipeWire profile (`HiFi`, from alsa-ucm-conf) exposes only a mono speaker, so
Controlify finds no 4-channel device. Switch the card to the `pro-audio` profile:

```sh
pactl list cards short | grep -i dualsense                  # the card name is the second column
pactl set-card-profile <card name> pro-audio
```

(or pick "Pro Audio" for the controller in your desktop's sound settings).

Newer alsa-ucm-conf releases (January 2026 onwards) add a "Direct" profile for the DualSense that
exposes all four channels with the right positions. If your distribution ships it, use it instead
of `pro-audio` and skip step 2.

## 2. Channel positions

With `pro-audio`, PipeWire labels the four channels `AUX0`–`AUX3`. It then remixes the haptics
stream onto what it thinks are the front channels, so you hear the haptics through the controller
speaker instead of feeling them. Label the channels with a WirePlumber rule in
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

Then restart WirePlumber (`systemctl --user restart wireplumber`) and replug the controller.
Check the result with:

```sh
pactl list sinks | grep -A30 pro-output | grep "Channel Map"
# Channel Map: front-left,front-right,rear-left,rear-right
```

## Checking it works

In game, run `/ds4c gallery haptic footstep_stone`. You should feel a tap in the grips and hear
nothing from the controller speaker.
