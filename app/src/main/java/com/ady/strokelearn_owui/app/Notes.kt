package com.ady.strokelearn_owui.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.ady.strokelearn_owui.app.NoteStore.Companion.defaultNotes
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String = "",
    val url: String? = null,
    /** Which script this note belongs to; null means it shows in every script. */
    val scriptId: String? = null
) {
    val isBookmark get() = !url.isNullOrBlank()
}

/**
 * Notes and bookmarks, stored as JSON in private storage.
 * Seeded once with [defaultNotes] so the list is never empty on first run.
 */
class NoteStore(context: Context) {

    private val prefs = context.getSharedPreferences("strokelearn", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Note.serializer())

    val notes = mutableStateListOf<Note>()

    init {
        val stored = prefs.getString(KEY, null)
        val loaded = stored?.let {
            runCatching { json.decodeFromString(serializer, it) }.getOrNull()
        }
        notes.addAll(loaded ?: defaultNotes())
        if (loaded == null) save()
    }

    /** Notes for one script, plus the global ones. Global first. */
    fun forScript(scriptId: String?): List<Note> =
        notes.filter { it.scriptId == null || it.scriptId == scriptId }
            .sortedBy { it.scriptId != null }
    fun upsert(note: Note) {
        val index = notes.indexOfFirst { it.id == note.id }
        if (index >= 0) notes[index] = note else notes.add(note)
        save()
    }

    fun delete(id: String) {
        notes.removeAll { it.id == id }
        save()
    }

    private fun save() {
        prefs.edit().putString(KEY, json.encodeToString(serializer, notes.toList())).apply()
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        /** Edit these to change what ships with the app. */
        fun defaultNotes(): List<Note> = listOf(
            Note(
                id = "guide_how_to_use",
                title = "How to use StrokeLearn",
                body = """
                    StrokeLearn teaches a writing system the way you actually
                    learn one: by drawing it, stroke by stroke, in the right
                    order.

                    THE LIBRARY

                    The home screen shows every letter in the script. Tap one to
                    practise it on its own. The small coloured dot on each tile
                    tracks your progress:

                      hollow  — not seen yet
                      amber   — learning
                      green   — learned

                    The number beside the dot counts how many times you have
                    drawn that letter in a session.

                    SESSIONS

                    The Session button is where most of the learning happens.
                    Three kinds:

                    Training — the letter appears faintly behind the canvas and
                    one stroke at a time is shown in grey. Trace it. Good for
                    letters you are meeting for the first time. You choose how
                    many letters and how many repetitions of each.

                    Test — no guide. You are given the romanisation and write
                    the letter from memory. Only letters you have already
                    trained on come up. A box with diagonals shows roughly where
                    on the canvas the letter sits, and how big it should be.

                    Final exam — every letter in the script, no guides.

                    After a test or exam you can mark each letter as learning or
                    learned. Letters marked learned are left out of future
                    sessions, so the pool narrows to what you still find hard.

                    SCORING

                    Your drawing is compared to the stored strokes point by
                    point, which means three things matter: the shape, the
                    direction you drew it in, and the number of strokes. Drawing
                    the right shape with the wrong stroke order will score
                    poorly, and that is intentional.

                    After you finish a letter, your strokes are redrawn with the
                    parts that drifted furthest from the target shown in red.
                    Small wobbles stay dark — only real deviation lights up.

                    SOUND

                    Where a recording exists, a speaker icon appears in the top
                    bar. In training the sound plays as soon as the letter
                    appears; in a test it waits until you have answered, so it
                    does not give the answer away. Autoplay can be turned off in
                    Settings.

                    SETTINGS WORTH KNOWING

                    Text size — scales everything, useful on a small screen.
                    Theme — light, dark, or follow the system.
                    Guide glyph — turn off the faint letter in training if you
                    want a harder practice.
                    Progress backup — your progress survives app updates, but
                    not an uninstall. Back it up before reinstalling or moving
                    to a new device.

                    NOTES

                    This screen. Tap a note to read it, or a linked note to open
                    it in your browser. The + button adds your own: leave the
                    link field empty for a plain text note, or paste a URL to
                    make a bookmark.

                    FEEDBACK

                    Settings → About → Send feedback opens your mail app with
                    the app version and device details already filled in. Please
                    use it — especially if the scoring feels unfair, since those
                    thresholds are guesses that need real use to tune.
                """.trimIndent()
            ),
            Note(
                id = "guide_make_a_script",
                title = "Making your own script",
                body = """
                    StrokeLearn is not tied to Hindi. Anything you can draw by
                    hand can become a script: Greek, Cyrillic, Georgian, Hebrew,
                    the Korean jamo, or a set of mathematical symbols. This note
                    explains how to build one.

                    The work happens in dataset mode — the brush icon in the top
                    bar of the home screen.

                    WHAT A SCRIPT IS

                    A script is a single JSON file. It holds a name, a list of
                    groups (vowels and consonants, for instance), and inside
                    each group the letters. Every letter carries its character,
                    a romanisation, an optional pronunciation hint and example
                    word, and the drawn strokes.

                    You do not have to write that file by hand. Draw the letters
                    in the app and export, and the app writes it for you.

                    STEP 1 — START FROM A SKELETON

                    The app can only trace letters it already knows about, so
                    the characters themselves have to exist first. Two ways:

                    Use the + button on the home screen to add letters one at a
                    time. Fill in the character, its romanisation, and an order
                    number, then draw the strokes straight away.

                    Or write a skeleton JSON on a computer, with every letter
                    listed but no strokes, and load it in dataset mode. This is
                    much faster for a full alphabet. A minimal entry looks like:

                      { "id": "01_a", "order": 1, "name": "A",
                        "romanisation": "a" }

                    Letters with no strokes show "no strokes" in red on the home
                    screen, which is your to-do list.

                    STEP 2 — TRACE

                    Open dataset mode and choose "Continue tracing". It walks
                    through every letter in order.

                    The character appears faintly behind the canvas in your
                    system font. Trace over it, lifting your finger between
                    strokes. Draw them in the order the letter is actually
                    written, and in the direction it is written — the app
                    compares direction and stroke count, not just the shape, so
                    getting this right is the whole point.

                    Undo removes the last stroke, Clear starts the letter again.
                    "Save & next" moves on. "Previous" goes back if you want to
                    redo one.

                    A stylus or a mouse on an emulator gives far better results
                    than a fingertip on a phone. Exported strokes are smoothed
                    automatically, so small wobbles are not worth redoing.

                    STEP 3 — EXPORT

                    The share icon in dataset mode, or Settings → Script data →
                    Export script, saves the JSON wherever you choose. Do this
                    at the end of every session. Your work lives in the app's
                    private storage, which is erased if the app is uninstalled.

                    STEP 4 — EDIT AND RELOAD

                    The exported file is plain text and readable. On a computer
                    you can add pronunciation hints, example words, IPA, or
                    rearrange the groups, then load it back with dataset mode →
                    "Load JSON". Loading replaces the stored copy of that script,
                    so the newer file wins.

                    ORDER NUMBERS AND GAPS

                    Each letter has an order number that sets its place in the
                    grid. Numbers may be skipped: a gap leaves an empty cell,
                    which is a simple way to group related letters visually —
                    the Devanagari consonant rows, say, or the Greek vowels.

                    The order number also forms part of the letter's identity,
                    so each one must be unique. The app refuses to save a
                    duplicate.

                    ADDING SOUND

                    Audio is bundled with the app rather than stored by it, so
                    it needs a rebuild. Name each file after the letter's id —
                    01_a.mp3, 02_aa.mp3 — and put them in a folder under
                    assets/audio/ named to match the script's audioDir. The app
                    finds them by convention; no JSON changes needed.

                    SHARING A SCRIPT

                    An exported JSON is self-contained and can be sent to
                    anyone. They load it through dataset mode and your script
                    appears in their script list alongside the bundled ones.

                    If you build something good, consider sharing it back to the
                    project — a well-traced alphabet is more work than the app
                    itself.
                """.trimIndent()
            ),
            Note(
                id = "ref_pronunciation",
                title = "Hindi pronunciation guide",
                body = "Charts with English approximations for every letter, " +
                        "with audio. University of Texas at Austin, CC BY 4.0.",
                url = "https://hindi.la.utexas.edu/resources/pronouncing-the-hindi-alphabet/",
                scriptId = "hindi"
            ),
            Note(
                id = "ref_script",
                title = "The Hindi script",
                body = "In-depth introduction to the writing system, including " +
                        "nasalization and conjuncts.",
                url = "https://hindi.la.utexas.edu/resources/the-hindi-script/",
                scriptId = "hindi"
            ),
            Note(
                id = "ref_quizlets",
                title = "Script recognition practice",
                body = "Quizlet decks for recognising letters at several levels.",
                url = "https://hindi.la.utexas.edu/resources/hindi-script-recognition-quizlets/",
                scriptId = "hindi"
            ),
            Note(
                id = "intro_punjabi",
                title = "About Gurmukhi",
                scriptId = "punjabi",
                body = """
                    Gurmukhi — "from the mouth of the Guru" — was standardised in
                    the 16th century by Guru Angad, the second Sikh Guru, to
                    write Punjabi. Its 35 letters are called the paintee.

                    If you know Devanagari, much will feel familiar: the same
                    horizontal top line, a similar five-row consonant layout.
                    But one difference matters a great deal.

                    Punjabi is tonal, and Hindi is not. Five letters —
                    ਘ ਝ ਢ ਧ ਭ — are not aspirates despite how they are usually
                    transliterated. They are tone letters: they lower the pitch
                    of the vowel that follows. ਘਰ is not "ghar" with a breathy g
                    but "kàr" with a falling tone. Learners coming from Hindi
                    reliably get this wrong, so it is worth hearing before you
                    practise writing.

                    The three letters at the start — ੳ ਅ ੲ — carry no sound of
                    their own. They are stands for vowels, used when a word
                    begins with one.

                    Around 125 million people speak Punjabi. In Pakistan it is
                    usually written in the Shahmukhi script instead, a Perso-
                    Arabic alphabet, so the same language has two written forms
                    that share no letters at all.
                """.trimIndent()
            ),
            Note(
                id = "intro_mongolian",
                title = "About the Mongolian script",
                scriptId = "mongolian",
                body = """
                    Traditional Mongolian is one of the very few scripts in the
                    world written vertically, in columns running top to bottom,
                    with the columns advancing left to right. It descends from
                    Old Uyghur, which descends from Aramaic by way of Sogdian —
                    the same distant ancestry as Hebrew and Arabic.

                    It was adapted for Mongolian around 1204, reportedly on the
                    order of Genghis Khan after his forces captured an Uyghur
                    scribe. It has been in use ever since.

                    TWO THINGS TO KNOW BEFORE YOU START

                    This script set teaches isolated letter forms. In real
                    writing, letters join into a continuous vertical stem and
                    take different shapes depending on whether they are at the
                    start, the middle, or the end of a word — a little like
                    Arabic. Learning the isolated forms is the usual first step,
                    but it is a first step.

                    The app draws on a square canvas, which cannot express the
                    vertical flow. Think of each glyph here as a letter in
                    isolation rather than as it would appear on a page.

                    Several letter pairs are written identically and
                    distinguished only by vowel harmony and context — a and e
                    share a shape, as do o and u. Mongolian readers resolve them
                    from the word.

                    In Mongolia itself, Cyrillic has been the everyday script
                    since the 1940s, though the traditional script is taught in
                    schools and used ceremonially. In Inner Mongolia, in China,
                    it remains the standard script for around five million
                    speakers.
                """.trimIndent()
            ),
            Note(
                id = "intro_geez",
                title = "About Ge'ez and Amharic",
                scriptId = "geez",
                body = """
                    The Ge'ez script — also called Ethiopic, or fidäl — is one of
                    the oldest writing systems still in daily use, with roots in
                    the Sabaean alphabet of South Arabia and inscriptions going
                    back to the fourth century. It writes Amharic and Tigrinya
                    today, and Ge'ez itself survives as the liturgical language
                    of the Ethiopian Orthodox Church.

                    It is an abugida, which means something specific. Each letter
                    is a consonant carrying a built-in vowel, and the vowel is
                    changed by modifying the letter's shape — a stroke added, a
                    loop extended, the whole glyph tilted. Every consonant has
                    seven such forms, called orders. The script therefore has 33
                    base letters but well over 200 glyphs.

                    This set gives you the first order, ግዕዝ, which is how the
                    script is taught. Once these are solid, the vowel orders are
                    learned as a pattern applied across all of them rather than
                    as 200 separate shapes.

                    A sound worth listening for: the ejectives — ቀ ጠ ጨ ጰ ጸ. These
                    are made by closing the throat and releasing the consonant
                    with a sharp pop rather than a breath. English has nothing
                    like them, and they are the sound most learners find hardest.

                    Some pairs of letters are now pronounced identically — ሀ, ሐ
                    and ኀ all sound alike in modern Amharic — but all survive in
                    spelling, much as English keeps silent letters.

                    Ge'ez is written left to right, unlike its South Arabian
                    ancestors and unlike Arabic and Hebrew.
                """.trimIndent()
            ),
            Note(
                id = "intro_sinhala",
                title = "About Sinhala",
                scriptId = "sinhala",
                body = """
                    Sinhala is written in one of the roundest scripts in the
                    world. Almost every letter is built from curves and loops,
                    with hardly a straight line anywhere — a shape the script
                    acquired from being written on dried palm leaves, where a
                    straight stroke would split the leaf along its fibres.

                    It descends from Brahmi, like Devanagari and Tamil, and has
                    been used in Sri Lanka for over two thousand years. Around 16
                    million people speak Sinhala, almost all of them on the one
                    island.

                    TWO FEATURES WORTH KNOWING

                    Sinhala has a sound Hindi does not: ඇ, the vowel in English
                    "cat". It appears constantly, and is one of the first things
                    that makes spoken Sinhala sound unlike its neighbours.

                    More distinctive still are the prenasalised consonants —
                    ඟ ඦ ඬ ඳ ඹ. These are single sounds with a faint nasal onset,
                    not two letters run together: a brief hum before the
                    consonant, as in the middle of "finger" said quickly. Very
                    few scripts have dedicated letters for these, and they are
                    the clearest mark of Sinhala's independence from the other
                    Indic scripts.

                    The consonants are arranged, as in Devanagari, by where in
                    the mouth they are made: throat, palate, roof, teeth, lips.
                    Several letters appear only in Sanskrit and Pali loanwords
                    and are rare in everyday Sinhala — the aspirates ඛ ඝ ඡ ඣ in
                    particular.

                    Like Ge'ez and Devanagari, Sinhala is an abugida: each
                    consonant carries a built-in vowel, changed by marks added
                    around the letter.
                """.trimIndent()
            ),
            Note(
                id = "intro_shavian",
                title = "About Shavian",
                scriptId = "shavian",
                body = """
                    Shavian is the odd one out here: it is a constructed
                    alphabet, barely seventy years old, and it writes English.

                    George Bernard Shaw spent his life irritated by English
                    spelling — by "ghoti" being pronounceable as "fish", by the
                    Latin alphabet needing 26 letters and endless digraphs to
                    represent some 40 English sounds. In his will he left money
                    for a competition to design a replacement. Ronald Kingsley
                    Read won it, and the alphabet was published in 1962.

                    The design is the interesting part.

                    Shavian has 48 letters, one per English sound, with no
                    digraphs and no silent letters. The consonants come in pairs:
                    a Tall letter for the voiceless sound, a Deep letter for its
                    voiced partner, and the Deep letter is the Tall one rotated
                    180 degrees. 𐑐 is p; turn it over and 𐑚 is b. 𐑑 is t, 𐑛 is d.
                    𐑓 is f, 𐑝 is v. The relationship between the sounds is
                    visible in the shapes.

                    Vowels are Short — small letters sitting on the line — or
                    Long, which extend above or below it. Some single letters
                    cover whole sounds that English spells with several, like
                    𐑸 for "are" and 𐑿 for "yew".

                    There is no capital and lowercase distinction. Proper names
                    take a leading dot instead.

                    Almost nobody writes Shavian, which is rather the appeal.
                    There is a small, cheerful community, an edition of Androcles
                    and the Lion printed in it as Shaw's will required, and the
                    curious experience of writing your own language in letters
                    that nobody around you can read.

                    Shavian is in Unicode and in the public domain. Reading it
                    fluently takes a few weeks.
                """.trimIndent()
            ),
        )

        private const val KEY = "notes"
    }
}