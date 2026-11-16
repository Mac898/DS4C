# HD haptics on Linux

DS4C's grip haptics go through Controlify to the DualSense's audio device, which has four output
channels: the controller speaker on 1–2 and the left and right grip actuators on 3–4. Controlify
only uses a DualSense audio device that offers all four channels.

- **USB:** the controller's own sound card. On PipeWire two things can get in the way; see steps 1
  and 2.
- **Bluetooth:** the stock Linux stack has no audio device for the controller. The DS4L daemon adds
  one; see "Bluetooth with DS4L". Steps 1 and 2 don't apply to it.

If DS4C can't find HD haptics while a DualSense is connected, it says so once in chat and in the log.

## Requirements on every platform

- Connect the DualSense with a USB cable. On Linux, Bluetooth also works once DS4L is running.
  On other systems HD haptics don't work over Bluetooth.
- Turn on **HD Haptics** in Controlify's controller settings.
- Turn off **Allow Vibrations** in Controlify's controller settings. DS4C plays its own versions of
  the events Controlify rumbles for (damage, explosions, mining...), so with both on you feel each
  of them twice.

## Bluetooth with DS4L

[DS4L](https://github.com/Mac898/DS4L) is a userspace daemon that gives a Bluetooth DualSense the
same 4-channel playback device as a wired one, with the channels already labelled. Build and
install it as its README describes (`./install.sh` sets up a systemd user service), then connect
the controller and start the game. Nothing else needs configuring.

- The controller speaker works too. Over Bluetooth it plays a little later than over USB; if the
  pickup echo sounds like a separate sound, raise DS4C's **Speaker delay** setting.
- If haptics skip every few seconds, see the Bluetooth adapter notes in DS4L's README.

## 1. The card profile (USB)

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

## 2. Channel positions (USB)

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

Walk on stone: you should feel a light tap in the grips for each step and hear nothing from the
controller speaker. With **Effect gallery** turned on under Debug in DS4C's settings, you can also
run `/ds4c gallery haptic footstep_stone` for a single tap.
