#!/bin/bash
# The Shelf session: runs Shelf, and the games Shelf asks for, until you leave.
# Started automatically on the TV after the Pi boots (see install.sh).
SHELF_HOME="${SHELF_HOME:-$HOME/shelf}"
APP="$SHELF_HOME/app"
SHELF_BIN="${SHELF_BIN:-$APP/shelf}"
RUN="$SHELF_HOME/.run"
BASE_CFG="$APP/config/retroarch-shelf.cfg"
LOG="$RUN/session.log"
mkdir -p "$RUN"
rounds=0
while true; do
    rounds=$((rounds + 1))
    if [ -n "$SHELF_MAX_ROUNDS" ] && [ "$rounds" -gt "$SHELF_MAX_ROUNDS" ]; then exit 0; fi   # for testing
    rm -f "$RUN/launch.req"
    "$SHELF_BIN" >>"$LOG" 2>&1
    code=$?
    if [ "$code" -eq 2 ]; then
        echo "You left Shelf. Type shelf-start to go back, or sudo poweroff to turn off."
        exit 0
    fi
    [ "$code" -eq 3 ] && continue   # Select and Start held: restart, which picks up new games
    if [ ! -f "$RUN/launch.req" ]; then   # Shelf closed or crashed without asking for a game: start it again
        echo "$(date): Shelf exited with code $code" >>"$LOG"
        sleep 1
        continue
    fi

    CORE= ROM= CONFIG= AUTO= SLOT= GAME=
    while IFS='=' read -r key value; do
        case "$key" in
            CORE) CORE=$value ;; ROM) ROM=$value ;; CONFIG) CONFIG=$value ;;
            AUTO) AUTO=$value ;; SLOT) SLOT=$value ;; GAME) GAME=$value ;;
        esac
    done <"$RUN/launch.req"

    echo "$(date): starting $GAME" >>"$LOG"
    start=$(date +%s)
    python3 "$APP/scripts/homewatch.py" >>"$LOG" 2>&1 &
    watcher=$!
    "${RETROARCH:-retroarch}" -L "$CORE" "$ROM" --appendconfig "$BASE_CFG|$CONFIG" >>"$LOG" 2>&1
    echo "$(date): RetroArch exited with code $?" >>"$LOG"
    kill "$watcher" 2>/dev/null
    wait "$watcher" 2>/dev/null

    # RetroArch saved the game as <game>.state.auto when it quit; keep it as the profile's save
    saved=0
    if [ -f "$AUTO" ]; then
        mv -f "$AUTO" "$SLOT" && saved=1
        [ -f "$AUTO.png" ] && mv -f "$AUTO.png" "$SLOT.png"
    fi
    printf 'GAME=%s\nSECONDS=%s\nSAVED=%s\n' "$GAME" "$(($(date +%s) - start))" "$saved" >"$RUN/return.txt"
done
