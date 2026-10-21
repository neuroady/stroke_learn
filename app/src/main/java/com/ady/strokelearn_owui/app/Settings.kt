package com.ady.strokelearn_owui.app

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/* ==================== settings ==================== */

enum class ThemeMode { LIGHT, DARK, AUTOMATIC }
/** Multiplier applied to all text sizes. */
const val FONT_SCALE_MIN = 0.85f
const val FONT_SCALE_MAX = 1.50f

/**
 * UI preferences, backed by SharedPreferences and exposed as Compose state
 * so screens recompose when a toggle changes.
 */
/**
 * UI preferences, backed by SharedPreferences and exposed as Compose state
 * so screens recompose when a toggle changes. Assigning a property persists it.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("strokelearn", Context.MODE_PRIVATE)

    private val autoPlayState = mutableStateOf(prefs.getBoolean(KEY_AUTOPLAY, true))
    var autoPlayAudio: Boolean
        get() = autoPlayState.value
        set(value) {
            autoPlayState.value = value
            prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()
        }

    private val guideGlyphState = mutableStateOf(prefs.getBoolean(KEY_GUIDE_GLYPH, true))
    var showGuideGlyph: Boolean
        get() = guideGlyphState.value
        set(value) {
            guideGlyphState.value = value
            prefs.edit().putBoolean(KEY_GUIDE_GLYPH, value).apply()
        }

    private val themeState = mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.AUTOMATIC)
    )
    var themeMode: ThemeMode
        get() = themeState.value
        set(value) {
            themeState.value = value
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    private val fontScaleState = mutableStateOf(prefs.getFloat(KEY_FONT_SCALE, 1.0f))
    var fontScale: Float
        get() = fontScaleState.value
        set(value) {
            val clamped = value.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)
            fontScaleState.value = clamped
            prefs.edit().putFloat(KEY_FONT_SCALE, clamped).apply()
        }

    private companion object {
        const val KEY_AUTOPLAY = "auto_play_audio"
        const val KEY_GUIDE_GLYPH = "show_guide_glyph"
        const val KEY_THEME = "theme_mode"
        const val KEY_FONT_SCALE = "font_scale"
    }
}
/* ==================== theme ==================== */

@Composable
fun StrokeLearnTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTOMATIC -> isSystemInDarkTheme()
    }
    val density = LocalDensity.current
    // scaling fontScale here resizes every sp in the app at once
    val scaled = Density(density.density, density.fontScale * settings.fontScale)

    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
            content = content
        )
    }
}

/* ==================== audio ==================== */

/**
 * Plays per-letter audio from assets.
 *
 * Files live at assets/audio/<audioDir>/<id>.mp3 by convention; a letter's
 * explicit [Letter.audio] field overrides that filename when set.
 */
class AudioPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    fun play(script: Script, letter: Letter) {
        stop()
        val path = assetPath(script, letter) ?: return
        runCatching {
            val descriptor = context.assets.openFd(path)
            player = MediaPlayer().apply {
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                descriptor.close()
                setOnCompletionListener { release(); player = null }
                prepare()
                start()
            }
        }
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
    }

    /** True if an audio file actually exists for this letter. */
    fun hasAudio(script: Script, letter: Letter): Boolean {
        val path = assetPath(script, letter) ?: return false
        return runCatching { context.assets.openFd(path).close(); true }.getOrDefault(false)
    }

    private fun assetPath(script: Script, letter: Letter): String? {
        if (script.audioDir.isBlank()) return null
        val fileName = letter.audio ?: "${letter.id}.mp3"
        return "audio/${script.audioDir}/$fileName"
    }
}