#!/bin/bash
# Shelf for TV: installer for Raspberry Pi OS Lite (64-bit) on a Raspberry Pi 3 or newer.
# Run it as your normal user, from the folder you unpacked:   ./install.sh
set -euo pipefail
SRC="$(cd "$(dirname "$0")" && pwd)"
SHELF_HOME="$HOME/shelf"
APP="$SHELF_HOME/app"
step() { printf '\n\033[1m%s\033[0m\n' "$*"; }

if [ "$(id -u)" -eq 0 ]; then echo "Run this as your normal user, without sudo. It asks for your password when it needs it."; exit 1; fi

step "1 of 7: Installing RetroArch, the emulators, and the tools to build Shelf"
sudo apt-get update
PACKAGES="retroarch libretro-nestopia libretro-snes9x libretro-genesisplusgx libretro-gambatte libretro-mgba
          build-essential pkg-config libsdl2-dev libgles-dev libfreetype-dev libpng-dev
          python3-evdev samba fonts-dejavu-core curl"
if ! sudo apt-get install -y $PACKAGES; then
    echo
    echo "Some packages couldn't be installed. Snes9x and Genesis Plus GX are in Debian's 'non-free'"
    echo "section; check that your apt sources include it, then run this installer again."
    exit 1
fi

step "2 of 7: Copying Shelf to $APP"
mkdir -p "$APP"
if [ "$SRC" != "$APP" ]; then cp -r "$SRC"/. "$APP"/; fi
chmod +x "$APP"/scripts/*

step "3 of 7: Building Shelf"
make -C "$APP" shelf

step "4 of 7: Fonts"
mkdir -p "$SHELF_HOME/fonts"
get_font() { curl -fsSL -o "$SHELF_HOME/fonts/$1.ttf" "https://github.com/google/fonts/raw/main/ofl/$2/$1%5Bwght%5D.ttf" || echo "Couldn't download $1. Shelf will use DejaVu Sans instead."; }
get_font Manrope manrope
get_font Unbounded unbounded

step "5 of 7: Game folders and profiles"
for s in nes snes genesis gb gbc gba; do mkdir -p "$SHELF_HOME/roms/$s" "$SHELF_HOME/art/$s"; done
if [ ! -f "$SHELF_HOME/profiles.txt" ]; then
    read -rp "Who will play? Type names separated by commas [Player 1]: " names
    names="${names:-Player 1}"
    echo "$names" | tr ',' '\n' | sed 's/^ *//; s/ *$//' | grep -v '^$' >"$SHELF_HOME/profiles.txt"
fi
echo "Profiles: $(paste -sd, "$SHELF_HOME/profiles.txt")   (edit $SHELF_HOME/profiles.txt to change them)"

step "6 of 7: Sharing the shelf folder on your network"
if ! grep -q '^\[shelf\]' /etc/samba/smb.conf; then
    sudo tee -a /etc/samba/smb.conf >/dev/null <<SAMBA

[shelf]
   path = $SHELF_HOME
   browseable = yes
   read only = no
   valid users = $USER
SAMBA
fi
echo "Choose a password for opening the shelf folder from your computer (it can match your Pi password):"
sudo smbpasswd -a "$USER"
sudo systemctl restart smbd

step "7 of 7: Starting Shelf when the Pi turns on"
# RetroArch draws straight to the screen if it was built that way; otherwise games run under a minimal X server
if retroarch --features 2>/dev/null | grep -i 'kms' | grep -qi 'yes'; then
    echo kms >"$APP/display-mode"; echo "RetroArch can draw straight to the screen."
else
    echo "This RetroArch needs a display server, so a minimal one is being installed (no desktop)."
    sudo apt-get install -y xserver-xorg xinit
    echo x11 >"$APP/display-mode"
fi
sudo ln -sf "$APP/scripts/shelf-start" /usr/local/bin/shelf-start
sudo raspi-config nonint do_boot_behaviour B2   # log in automatically on the TV's console
if ! grep -q shelf-start "$HOME/.bash_profile" 2>/dev/null; then
    cat >>"$HOME/.bash_profile" <<'PROFILE'

# start Shelf on the TV after logging in; logins over SSH are left alone
if [ "$(tty)" = "/dev/tty1" ] && [ -z "${SHELF_NO_AUTOSTART:-}" ]; then exec shelf-start; fi
PROFILE
fi

step "All set."
echo "Copy games into \\\\$(hostname)\\shelf\\roms from your computer, then restart:   sudo reboot"
