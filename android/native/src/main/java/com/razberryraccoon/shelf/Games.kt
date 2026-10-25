package com.razberryraccoon.shelf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Game(val sys: String, val title: String, val path: String)

/**
 * Your games (found by the shared Scanner), what you've played, and how each one starts. Everything Shelf remembers is
 * kept in the app's own preferences: when you last played each game, its shell color, and each console's emulator.
 */
class Games(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("shelf", Context.MODE_PRIVATE)
    var all: List<Game> = emptyList(); private set
    var installed: Set<String> = emptySet(); private set

    companion object {
        /** Console names, from the prototype's PLAT table. */
        val NAMES = mapOf("nes" to "NES", "snes" to "Super Nintendo", "n64" to "Nintendo 64", "genesis" to "Sega Genesis", "gb" to "Game Boy",
            "gbc" to "Game Boy Color", "gba" to "Game Boy Advance", "ps1" to "PlayStation", "ds" to "Nintendo DS", "psp" to "PlayStation Portable")
        /** RetroArch cores and standalone emulators for each console (the prototype's list); `first` is preferred when installed. */
        private class Emus(val cores: List<String>, val apps: List<String>, val first: String? = null)
        private val EMUS = mapOf(
            "nes" to Emus(listOf("Mesen", "FCEUmm", "Nestopia UE", "QuickNES"), listOf("NES.emu", "Nesoid")),
            "snes" to Emus(listOf("Snes9x", "bsnes", "bsnes-hd", "Snes9x 2010", "Mesen-S", "Beetle Supafaust"), listOf("Snes9x EX+")),
            "n64" to Emus(listOf("Mupen64Plus-Next", "ParaLLEl N64"), listOf("Mupen64Plus FZ", "Mupen64Plus AE"), "Mupen64Plus FZ"),
            "genesis" to Emus(listOf("Genesis Plus GX", "PicoDrive", "BlastEm"), listOf("MD.emu", "Pizza Boy SC")),
            "gb" to Emus(listOf("Gambatte", "SameBoy", "Gearboy", "mGBA", "TGB Dual"), listOf("Pizza Boy GBC", "My OldBoy!", "GBC.emu", "Linkboy", "SkyEmu")),
            "gbc" to Emus(listOf("Gambatte", "SameBoy", "Gearboy", "mGBA", "TGB Dual"), listOf("Pizza Boy GBC", "My OldBoy!", "GBC.emu", "Linkboy", "SkyEmu")),
            "gba" to Emus(listOf("mGBA", "gpSP", "VBA-M", "VBA Next"), listOf("Pizza Boy GBA", "My Boy!", "GBA.emu", "Linkboy", "NooDS", "SkyEmu")),
            "ps1" to Emus(listOf("SwanStation", "Beetle PSX HW", "Beetle PSX", "PCSX ReARMed"), listOf("DuckStation", "ePSXe", "FPseNG", "FPse"), "DuckStation"),
            "ds" to Emus(listOf("melonDS DS", "melonDS", "DeSmuME", "DeSmuME 2015", "NooDS"), listOf("melonDS", "DraStic", "NooDS", "SkyEmu"), "melonDS"),
            "psp" to Emus(listOf("PPSSPP"), listOf("PPSSPP", "PPSSPP Gold"), "PPSSPP"))
        /** RetroArch core names → the core files RetroArch installs (as in rn.js). */
        private val CORE_FILES = mapOf("Mesen" to "mesen", "FCEUmm" to "fceumm", "Nestopia UE" to "nestopia", "QuickNES" to "quicknes", "Snes9x" to "snes9x",
            "bsnes" to "bsnes", "bsnes-hd" to "bsnes_hd_beta", "Snes9x 2010" to "snes9x2010", "Mesen-S" to "mesen-s", "Beetle Supafaust" to "mednafen_supafaust",
            "Mupen64Plus-Next" to "mupen64plus_next_gles3", "ParaLLEl N64" to "parallel_n64", "Genesis Plus GX" to "genesis_plus_gx", "PicoDrive" to "picodrive",
            "BlastEm" to "blastem", "Gambatte" to "gambatte", "SameBoy" to "sameboy", "Gearboy" to "gearboy", "mGBA" to "mgba", "TGB Dual" to "tgbdual",
            "gpSP" to "gpsp", "VBA-M" to "vbam", "VBA Next" to "vba_next", "SwanStation" to "swanstation", "Beetle PSX HW" to "mednafen_psx_hw",
            "Beetle PSX" to "mednafen_psx", "PCSX ReARMed" to "pcsx_rearmed", "melonDS DS" to "melondsds", "melonDS" to "melonds", "DeSmuME" to "desmume",
            "DeSmuME 2015" to "desmume2015", "NooDS" to "noods", "PPSSPP" to "ppsspp")
        const val RA = "RetroArch: "
    }

    // ---------- you, and where your games are ----------

    var name: String get() = prefs.getString("name", "") ?: ""; set(v) = prefs.edit().putString("name", v).apply()
    var color: Int get() = prefs.getInt("color", 0); set(v) = prefs.edit().putInt("color", v).apply()
    var lightEnv: Float get() = prefs.getFloat("light:env", Look.ENV); set(v) = prefs.edit().putFloat("light:env", v).apply()
    var lightExposure: Float get() = prefs.getFloat("light:exposure", Look.EXPOSURE); set(v) = prefs.edit().putFloat("light:exposure", v).apply()
    var lightKey: Float get() = prefs.getFloat("light:key", Look.KEY); set(v) = prefs.edit().putFloat("light:key", v).apply()
    var sounds: Boolean get() = prefs.getBoolean("sounds", true); set(v) = prefs.edit().putBoolean("sounds", v).apply()
    var setupDone: Boolean get() = prefs.getBoolean("setup", false); set(v) = prefs.edit().putBoolean("setup", v).apply()

    /** Folders you chose: one per console, and main folders that hold a folder per console. */
    fun folder(sys: String): String? = prefs.getString("folder:$sys", null)
    fun setFolder(sys: String, path: String?) = prefs.edit().apply { if (path == null) remove("folder:$sys") else putString("folder:$sys", path) }.apply()
    var roots: List<String>
        get() = JSONArray(prefs.getString("roots", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
        set(v) = prefs.edit().putString("roots", JSONArray(v).toString()).apply()
    private fun foldersJson() = JSONObject().apply { for (k in NAMES.keys) folder(k)?.let { put(k, it) }; put("roots", JSONArray(roots)) }.toString()

    /** How many games were found for each console. */
    fun counts(): Map<String, Int> = all.groupingBy { it.sys }.eachCount()

    /** Looks for games in every ROMs folder and the folders you chose (the shared Scanner). Slow; call it off the main thread. */
    fun scan() {
        val r = Scanner.scan(ctx, foldersJson())
        val list = ArrayList<Game>()
        r.optJSONArray("games")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { g -> if (g.optString("sys") in NAMES) list.add(Game(g.getString("sys"), g.getString("title"), g.getString("path"))) } }
        installed = r.optJSONArray("emus")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        all = list
    }

    /** Home's row: what you played most recently first, then everything else by name (minus games you hid from Home). */
    fun recent(): List<Game> = all.filter { onHome(it) }.sortedWith(compareByDescending<Game> { lastPlayed(it) }.thenBy { it.title.lowercase() })

    fun lastPlayed(g: Game) = prefs.getLong("played:" + g.path, 0L)
    fun timesPlayed(g: Game) = prefs.getInt("plays:" + g.path, 0)
    fun favorite(g: Game) = prefs.getBoolean("fav:" + g.path, false)
    fun setFavorite(g: Game, on: Boolean) = prefs.edit().putBoolean("fav:" + g.path, on).apply()
    fun onHome(g: Game) = !prefs.getBoolean("hide:" + g.path, false)
    fun setOnHome(g: Game, on: Boolean) = prefs.edit().putBoolean("hide:" + g.path, !on).apply()
    /** Which game in libretro's database this file is, when you've told Shelf (Game settings › Which game is this?). */
    fun match(g: Game): String? = prefs.getString("match:" + g.path, null)
    fun setMatch(g: Game, name: String?) = prefs.edit().apply { if (name == null) remove("match:" + g.path) else putString("match:" + g.path, name) }.apply()
    fun variant(g: Game): String? = prefs.getString("variant:" + g.path, null)
    fun setVariant(g: Game, name: String) = prefs.edit().putString("variant:" + g.path, name).apply()
    /** The shell a console's standard cartridges wear (the Museum), if you chose one. */
    fun defaultShell(sys: String): String? = prefs.getString("default-shell:$sys", null)
    fun setDefaultShell(sys: String, key: String?) = prefs.edit().apply { if (key == null) remove("default-shell:$sys") else putString("default-shell:$sys", key) }.apply()
    /** A console's colors (the Museum): one it really came in, by name, or "custom:<id>" for yours; null, its standard colors. */
    fun consoleLook(sys: String): String? = prefs.getString("console-look:$sys", null)
    fun setConsoleLook(sys: String, key: String?) = prefs.edit().apply { if (key == null) remove("console-look:$sys") else putString("console-look:$sys", key) }.apply()

    /** Every way this console's games can start: RetroArch's cores first, then standalone emulators, installed ones marked. */
    fun emulatorChoices(sys: String): List<Pair<String, Boolean>> {
        val e = EMUS[sys] ?: return emptyList()
        return e.cores.map { RA + it to ("RetroArch" in installed) } + e.apps.map { it to (it in installed) }
    }
    /** A good first choice for this console: its preferred standalone emulator if installed, else RetroArch, else any installed app. */
    fun suggestion(sys: String): String? {
        val e = EMUS[sys] ?: return null
        e.first?.let { if (it in installed) return it }
        if ("RetroArch" in installed) return RA + e.cores[0]
        return e.apps.firstOrNull { it in installed }
    }

    /** The emulator you chose for this console, if you have. */
    fun emulator(sys: String): String? = prefs.getString("emu:$sys", null)
    fun setEmulator(sys: String, emu: String) = prefs.edit().putString("emu:$sys", emu).apply()
    /** This game's own emulator, if you chose one for it (the game page), otherwise its console's. */
    fun emulatorFor(g: Game): String? = ownEmulator(g) ?: emulator(g.sys)
    fun ownEmulator(g: Game): String? = prefs.getString("emu-game:" + g.path, null)
    fun setOwnEmulator(g: Game, emu: String?) = prefs.edit().apply { if (emu == null) remove("emu-game:" + g.path) else putString("emu-game:" + g.path, emu) }.apply()

    /** Starts the game. Returns "" when it opened, otherwise what went wrong in plain words. */
    fun launch(g: Game): String {
        val emu = emulatorFor(g) ?: return "Choose an emulator first"
        val ra = emu.startsWith(RA)
        val req = JSONObject().put("emu", if (ra) "RetroArch" else emu).put("core", if (ra) CORE_FILES[emu.removePrefix(RA)] ?: "" else "")
            .put("path", g.path).put("sys", g.sys)
        val err = Emulators.launch(ctx, req) ?: ""
        if (err.isEmpty()) prefs.edit().putLong("played:" + g.path, System.currentTimeMillis()).putInt("plays:" + g.path, timesPlayed(g) + 1).apply()
        return err
    }
}

/** "/storage/emulated/0/ROMs/gba" → "Internal storage › ROMs › gba" (as the prototype's prettyPath). */
fun prettyPath(p: String): String = p.replace(Regex("^/storage/emulated/0/?"), "Internal storage/").replace(Regex("^/storage/[^/]+/?"), "SD card/")
    .trimEnd('/').split('/').joinToString(" › ")
