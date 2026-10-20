# Shelf for TV: first version

This first slice runs the whole loop on the real Raspberry Pi:

- **Booting** straight into Shelf, with no desktop.
- **Who's playing?:** pick a profile.
- **Home:** your games as 3D cartridges over the space backdrop, most recently played first. Real label art is used when you add it; otherwise Shelf makes a stand-in label.
- **Playing:** press A to start a game in RetroArch. If that profile has a save, the game picks up from it.
- **Coming back:** press Home on the controller to save and return. The cartridge flies back to the front of Home.

The Library, game pages, settings, achievements and media come next, ported screen by screen from the prototype.

## What you need

- Raspberry Pi 3B+ with its power supply, a microSD card (16 GB or more), and an HDMI cable to the TV
- A USB or Bluetooth controller (a keyboard works too)
- A Windows PC on the same network, with [Raspberry Pi Imager](https://www.raspberrypi.com/software/)

## 1. Prepare the SD card

In Raspberry Pi Imager:

1. Choose **Raspberry Pi 3** as the device.
2. For the operating system, choose **Raspberry Pi OS (other)**, then **Raspberry Pi OS Lite (64-bit)**.
3. Choose your SD card, then **Edit settings** when asked:
   - **Hostname:** `shelf`
   - **Username and password:** your choice
   - **Wi-Fi:** your network (or plug in Ethernet)
   - **Services tab:** turn on **SSH**, using password authentication
4. Write the card, put it in the Pi, connect the TV and power it on. The first boot takes a few minutes.

## 2. Install Shelf

In **Windows Terminal** (or Command Prompt), from the folder with `shelf-tv.tar.gz`. Replace `you` with your Pi username:

```
scp shelf-tv.tar.gz you@shelf.local:~
ssh you@shelf.local
```

Then, on the Pi:

```
tar xzf shelf-tv.tar.gz
cd shelf-tv
./install.sh
```

The installer asks for your password a few times, for your profile names, and for a password for the shared folder. It takes about 10–20 minutes. When it's done, run `sudo reboot` and Shelf starts on the TV.

## 3. Add games

In File Explorer, open `\\shelf\shelf`, and sign in with your Pi username and the shared-folder password.

- **Games** go in `roms`, in the folder for each console: `nes`, `snes`, `genesis`, `gb`, `gbc`, `gba`. Zipped games work too.
- **Label art** goes in `art\<console>\`, named after the game file with `.label.png` added, for example `art\snes\Super Game (USA).label.png`. The full-size crops your label cropper saves in its `cropped` folder work as they are.

Shelf looks for new games each time it starts. To refresh it right away, hold **Select and Start** for two seconds.

## Controls

| | In Shelf | While playing |
|---|---|---|
| D-pad or left stick | Move | |
| A | Choose / play | |
| B | Back to Who's playing | |
| Home | | Save and go back to Shelf |
| Select + Start, held | Restart Shelf (picks up new games) | Same as Home |

With a keyboard: arrows, Enter (A), Esc (B), and the Home key while playing.

Each profile has its own saves. Pressing Home while playing quits the game, and RetroArch saves it on the way out. The next time that profile plays the game, it continues from there.

## If something goes wrong

Everything is logged to `\\shelf\shelf\.run\session.log` (`.run` is a hidden folder).

- **Black screen when a game starts:** RetroArch couldn't draw straight to the screen. Over SSH, run `sudo apt install -y xserver-xorg xinit`, then `echo x11 > ~/shelf/app/display-mode`, then `sudo reboot`.
- **Home does nothing while playing:** try holding Select and Start. If that works but Home doesn't, tell me your controller's model.
- **A console's games won't start:** the log says which emulator is missing.
- **Getting to a command line on the TV:** press Ctrl+Alt+F2 on a keyboard and log in. Over SSH, `pkill -f shelf-session` stops Shelf until the next reboot.

## For development

`make shelf-test` builds an offscreen version that renders through Mesa, presses buttons from a script and saves screenshots (see `test/platform_egl.c`). The Pi build is `make shelf`.
