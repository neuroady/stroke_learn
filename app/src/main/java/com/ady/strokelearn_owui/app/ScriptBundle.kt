package com.ady.strokelearn_owui.app

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Packs a script and its recorded audio into one zip:
 *
 *   script.json
 *   audio/<letterId>.m4a
 *
 * Plain JSON is still used when there are no recordings, so the common case
 * stays a file you can open in a text editor.
 */
class ScriptBundle(private val context: Context) {

    private fun audioDir(script: Script) =
        File(context.filesDir, "audio/${script.audioDir.ifBlank { script.id }}")

    fun recordings(script: Script): List<File> =
        audioDir(script).listFiles()?.filter { it.isFile && it.length() > 0 }.orEmpty()

    fun hasRecordings(script: Script) = recordings(script).isNotEmpty()

    fun writeZip(script: Script, json: String, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("script.json"))
            zip.write(json.toByteArray())
            zip.closeEntry()

            recordings(script).forEach { file ->
                zip.putNextEntry(ZipEntry("audio/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Reads either a zip bundle or a bare JSON file. Detection is by content —
     * a zip always starts with "PK" — because file pickers report MIME types
     * unreliably.
     */
    fun readBundle(input: InputStream): Result<ImportedBundle> = runCatching {
        val bytes = input.readBytes()
        val isZip = bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

        if (!isZip) return@runCatching ImportedBundle(bytes.decodeToString(), emptyMap())

        var json: String? = null
        val audio = mutableMapOf<String, ByteArray>()

        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name.endsWith("script.json") -> json = zip.readBytes().decodeToString()
                    name.startsWith("audio/") && !entry.isDirectory ->
                        audio[name.removePrefix("audio/")] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        ImportedBundle(json ?: error("No script.json in the archive"), audio)
    }

    /**
     * Writes imported audio into the script's folder.
     *
     * Files are named after letter ids. If the script has been renumbered since
     * the bundle was made, some will not match any letter — those are still
     * written, but reported so the user knows they are inert.
     */
    fun saveAudio(script: Script, files: Map<String, ByteArray>): AudioImportResult {
        if (files.isEmpty()) return AudioImportResult(0, emptyList())

        val dir = audioDir(script).apply { mkdirs() }
        val letterIds = script.allLetters.map { it.id }.toSet()
        val orphans = mutableListOf<String>()

        files.forEach { (name, bytes) ->
            File(dir, name).writeBytes(bytes)
            if (name.substringBeforeLast('.') !in letterIds) orphans += name
        }
        return AudioImportResult(files.size, orphans)
    }
}

data class ImportedBundle(val json: String, val audio: Map<String, ByteArray>)
data class AudioImportResult(val saved: Int, val orphans: List<String>)