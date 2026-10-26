package dev.fusion.media

/**
 * MIDI note numbers, scientific pitch names and equal-temperament
 * frequencies.
 *
 * Fixed points: MIDI notes are 0..127 (MIDI 1.0 spec); A4 = note 69 =
 * 440 Hz (ISO 16:1975 concert pitch); 12-TET gives
 * f(n) = 440 * 2^((n-69)/12). Naming uses the "C4 = 60" middle-C
 * convention (declared — Yamaha's C3=60 labeling is NOT used), so note 0
 * is C-1 and note 127 is G9.
 *
 * Declared narrowings: 12-TET only — no stretched tuning, no alternate
 * concert pitches; names use sharps canonically, flats accepted on parse.
 */
object MidiNote {
    const val SCOPE = "MIDI 0..127, C4=60 naming, 12-TET at A4=440 only; no alternate tunings."
    private val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_TO_SHARP = mapOf("DB" to 1, "EB" to 3, "GB" to 6, "AB" to 8, "BB" to 10)

    fun name(note: Int): String {
        require(note in 0..127) { "MIDI note $note outside 0..127" }
        return SHARP_NAMES[note % 12] + (note / 12 - 1)
    }

    fun parse(s: String): Int {
        require(s.length >= 2) { "'$s' too short for a pitch name" }
        val upper = s.uppercase()
        var idx = 1
        if (idx < upper.length && (upper[idx] == '#' || upper[idx] == 'B') && upper.length > 2) idx++
        val pitchPart = upper.substring(0, idx)
        val octavePart = upper.substring(idx)
        val pc = when {
            pitchPart.length == 1 -> SHARP_NAMES.indexOf(pitchPart).also { require(it >= 0) { "unknown pitch '$s'" } }
            pitchPart.endsWith("#") -> SHARP_NAMES.indexOf(pitchPart).also { require(it >= 0) { "unknown pitch '$s'" } }
            else -> FLAT_TO_SHARP[pitchPart] ?: throw IllegalArgumentException("unknown pitch '$s'")
        }
        val octave = octavePart.toIntOrNull() ?: throw IllegalArgumentException("bad octave in '$s'")
        val note = (octave + 1) * 12 + pc
        require(note in 0..127) { "'$s' maps to $note, outside MIDI 0..127" }
        return note
    }

    fun frequency(note: Int): Double {
        require(note in 0..127) { "MIDI note $note outside 0..127" }
        return 440.0 * Math.pow(2.0, (note - 69) / 12.0)
    }

    /** Nearest note for a frequency plus signed cents deviation toward it. */
    fun fromFrequency(hz: Double): Pair<Int, Double> {
        require(hz > 0.0 && hz.isFinite()) { "frequency $hz" }
        val exact = 69.0 + 12.0 * (Math.log(hz / 440.0) / Math.log(2.0))
        val note = Math.round(exact).toInt()
        require(note in 0..127) { "frequency $hz outside MIDI range" }
        val cents = (exact - note) * 100.0
        return note to cents
    }
}
