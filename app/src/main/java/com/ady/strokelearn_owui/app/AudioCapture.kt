package com.ady.strokelearn_owui.app

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * Recording and importing per-letter audio.
 *
 * Assets are read-only, so user audio goes to filesDir/audio/<audioDir>/<id>.m4a.
 * [AudioPlayer] checks there first, then falls back to the bundled assets, so a
 * recording overrides a shipped file without replacing it.
 */
class AudioCapture(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null

    fun fileFor(script: Script, letter: Letter): File {
        val dir = File(context.filesDir, "audio/${script.audioDir.ifBlank { script.id }}")
        dir.mkdirs()
        return File(dir, "${letter.id}.m4a")
    }

    fun start(script: Script, letter: Letter): Boolean {
        stop()
        val file = fileFor(script, letter)
        return runCatching {
            val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
            else @Suppress("DEPRECATION") MediaRecorder()
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            target = file
        }.isSuccess
    }

    /** Stops recording and returns the file, or null if nothing usable was captured. */
    fun stop(): File? {
        val rec = recorder ?: return null
        runCatching { rec.stop() }
        runCatching { rec.release() }
        recorder = null
        val file = target
        target = null
        return file?.takeIf { it.exists() && it.length() > 0 }
    }

    val isRecording get() = recorder != null

    /** Copies a file the user picked into the script's audio folder. */
    fun import(script: Script, letter: Letter, uri: Uri): Result<File> = runCatching {
        val file = fileFor(script, letter)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: error("Could not read the file")
        file
    }

    fun delete(script: Script, letter: Letter) {
        fileFor(script, letter).delete()
    }

    fun hasUserAudio(script: Script, letter: Letter): Boolean = fileFor(script, letter).exists()
}