# Shelf for Android: the app

The Shelf interface is the same page as the browser prototype (`prototype-source/android/`), running in a
full-screen WebView. This small Java app adds what a web page can't do, through a bridge the page sees as
`ShelfNative`:

| File | What it does |
|---|---|
| `MainActivity.java` | Shows the interface, passes in controller buttons, tells it when you come back from a game, and handles the Home button. |
| `Bridge.java` | What the page can ask for: find games and apps, launch, open apps, save Shelf's state, permissions. |
| `Art.java` | Downloads art and details for the page. |

What both apps share is in `shared/` (an Android library):

| File | What it does |
|---|---|
| `Scanner.java` | Finds ROMs in `ROMs/<console>` folders, lists apps and installed emulators. |
| `Emulators.java` | How to start RetroArch and 24 standalone emulators, following ES-DE's Android configuration. |
| `RomProvider.java` | Lends a ROM, read-only, to emulators that want a `content://` link (each app under its own name). |
| `ArtIndex.java`, `ArtSources.java` | libretro's database, and the art services. |

**Shelf Native**, the native app being built to replace the WebView, is in `native/` (see its README).

On the page side, `prototype-source/android/rn.js` is the native mode: real games and apps instead of the invented
ones, and launching through the bridge.

## Building

GitHub Actions builds it (`.github/workflows/android.yml`): every push to `main` publishes a release with
`Shelf.apk` and `ShelfNative.apk`, and pushes to `work-in-progress` attach both to the run. To build on a computer with the Android SDK:

```
bash android/prepare-assets.sh      # builds the interface into app/src/main/assets/index.html
cd android && ./gradlew assembleDebug
```

`shelf-debug.keystore` is a debug signing key, checked in on purpose so every build installs over the last one.
It's only for sideloading onto your own device.
