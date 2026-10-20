#!/usr/bin/env python3
"""Install Shelf into a retro-go source folder.

    python install_into_retrogo.py C:\\retro-go

It does three things, and is safe to run again:
  1. Replaces retro-go's launcher with Shelf (the original is kept as launcher/main-original).
  2. Makes every way of quitting a game save your spot first (a small change to rg_system.c).
  3. Applies retro-go's ESP-IDF patches, including the SD card fix the Odroid GO needs.
     Run it from a prompt where C:\\esp\\idf.bat has been run, so it can find ESP-IDF.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SHELF_SRC = os.path.join(HERE, "..", "launcher-main")
MARKER = "Shelf: every way of quitting"


def fail(msg):
    print("\nStopped: " + msg)
    sys.exit(1)


def install_launcher(rg):
    main = os.path.join(rg, "launcher", "main")
    backup = os.path.join(rg, "launcher", "main-original")
    if not os.path.isdir(main):
        fail(f"{main} doesn't exist. Is {rg} a retro-go folder?")
    if not os.path.isdir(backup):
        shutil.copytree(main, backup)
        print(f"  Kept retro-go's original launcher in {backup}")
    for name in os.listdir(main):
        path = os.path.join(main, name)
        shutil.rmtree(path) if os.path.isdir(path) else os.remove(path)
    for name in os.listdir(SHELF_SRC):
        shutil.copy2(os.path.join(SHELF_SRC, name), main)
    print(f"  Copied Shelf into {main}")


def patch_autosave(rg):
    path = os.path.join(rg, "components", "retro-go", "rg_system.c")
    if not os.path.isfile(path):
        fail(f"{path} doesn't exist.")
    src = open(path, encoding="utf-8").read()
    if MARKER in src:
        print("  Auto-save on quit: already in place")
        return
    pattern = re.compile(
        r'(void\s+rg_system_exit\s*\(\s*void\s*\)\s*\{\s*RG_LOGW\("Exiting application!"\);)(\s*rg_system_switch_app\(RG_APP_LAUNCHER,\s*0,\s*0,\s*0\);)')
    m = pattern.search(src)
    if not m:
        fail("couldn't find rg_system_exit() in the form this script expects.\n"
             "Your retro-go version may differ from the one Shelf was written against. Send me that function.")
    insert = ("\n    // " + MARKER + " saves your spot first, so Resume picks up where you left off.\n"
              "    if (app.initialized && !app.isLauncher && app.handlers.saveState && app.romPath && app.romPath[0])\n"
              "        rg_emu_save_state(app.saveSlot);")
    src = src[:m.end(1)] + insert + src[m.end(1):]
    open(path, "w", encoding="utf-8", newline="").write(src)
    print("  Auto-save on quit: added to rg_system.c")


def apply_idf_patches(rg, idf):
    pdir = os.path.join(rg, "tools", "patches")
    if not idf or not os.path.isdir(idf):
        print("  ESP-IDF patches: skipped, ESP-IDF not found. Run C:\\esp\\idf.bat first, then run this again.")
        return False
    if not os.path.isdir(pdir):
        print(f"  ESP-IDF patches: no {pdir} folder, nothing to apply")
        return True
    patches = sorted(f for f in os.listdir(pdir) if f.endswith((".diff", ".patch")))
    sd_ok = False
    for name in patches:
        p = os.path.join(pdir, name)
        def git(*args):
            return subprocess.run(["git", "-C", idf, "apply", *args, p], capture_output=True, text=True).returncode == 0
        if git("--reverse", "--check"):
            status = "already applied"
        elif git("--check"):
            status = "applied" if git() else "FAILED to apply"
        else:
            status = "not for this ESP-IDF version, skipped"
        if "sdcard" in name.lower() and status in ("applied", "already applied"):
            sd_ok = True
        print(f"  ESP-IDF patch {name}: {status}")
    if not sd_ok:
        print("  Warning: no SD card fix patch applied. Retro-go says it's required on the Odroid GO.\n"
              "  Send me the list of files in " + pdir)
    return sd_ok


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("retrogo", help="your retro-go folder, for example C:\\retro-go")
    ap.add_argument("--idf", default=os.environ.get("IDF_PATH"), help="ESP-IDF folder (found automatically after idf.bat)")
    args = ap.parse_args()
    rg = os.path.abspath(args.retrogo)
    if not os.path.isfile(os.path.join(rg, "rg_tool.py")):
        fail(f"{rg} doesn't look like retro-go (no rg_tool.py).")
    print("Installing Shelf into " + rg)
    install_launcher(rg)
    patch_autosave(rg)
    apply_idf_patches(rg, args.idf)
    print("\nDone. Next, build it:\n"
          f"  cd {rg}\n"
          "  python rg_tool.py --target odroid-go build-fw launcher retro-core")


if __name__ == "__main__":
    main()
