package com.ultraplayer.app

data class Cue(val start: Long, val end: Long, val text: String)

object Subs {
    private fun ts(ms: Long, sep: Char): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        val x = ms % 1000
        return "%02d:%02d:%02d%c%03d".format(h, m, s, sep, x)
    }

    private fun assTs(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        return "%d:%02d:%02d.%02d".format(h, m, s, (ms % 1000) / 10)
    }

    fun toSrt(cues: List<Cue>): String = buildString {
        cues.forEachIndexed { i, c ->
            append(i + 1).append('\n')
            append(ts(c.start, ',')).append(" --> ").append(ts(c.end, ',')).append('\n')
            append(c.text).append("\n\n")
        }
    }

    fun toVtt(cues: List<Cue>): String = buildString {
        append("WEBVTT\n\n")
        cues.forEach { c ->
            append(ts(c.start, '.')).append(" --> ").append(ts(c.end, '.')).append('\n')
            append(c.text).append("\n\n")
        }
    }

    fun toAss(cues: List<Cue>): String = buildString {
        append("[Script Info]\nScriptType: v4.00+\nPlayResX: 1920\nPlayResY: 1080\n\n")
        append("[V4+ Styles]\n")
        append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        append("Style: Default,Arial,56,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,1,2,30,30,40,1\n\n")
        append("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        cues.forEach { c ->
            append("Dialogue: 0,").append(assTs(c.start)).append(',').append(assTs(c.end))
                .append(",Default,,0,0,0,,").append(c.text.replace("\n", "\\N")).append('\n')
        }
    }

    fun toTxt(cues: List<Cue>): String = cues.joinToString("\n") { it.text.replace("\n", " ") }

    private val timeRe = Regex(
        "(?:(\\d+):)?(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(?:(\\d+):)?(\\d{2}):(\\d{2})[,.](\\d{1,3})"
    )

    private fun toMs(h: String, m: String, s: String, ms: String): Long =
        (h.ifEmpty { "0" }.toLong() * 3600 + m.toLong() * 60 + s.toLong()) * 1000 + ms.padEnd(3, '0').toLong()

    /** Parses SRT and VTT. */
    fun parse(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        val blocks = text.replace("\r", "").replace("\uFEFF", "").trim().split(Regex("\n\\s*\n"))
        for (b in blocks) {
            val lines = b.split("\n")
            val idx = lines.indexOfFirst { timeRe.containsMatchIn(it) }
            if (idx < 0) continue
            val g = timeRe.find(lines[idx])!!.groupValues
            val body = lines.drop(idx + 1).joinToString("\n") { it.replace(Regex("<[^>]+>"), "") }.trim()
            if (body.isEmpty()) continue
            out += Cue(toMs(g[1], g[2], g[3], g[4]), toMs(g[5], g[6], g[7], g[8]), body)
        }
        return out
    }
}
