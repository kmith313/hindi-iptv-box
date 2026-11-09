package com.desi.iptvtv

import java.util.Locale

/**
 * Minimal M3U/M3U8 (EXTM3U) playlist parser.
 *
 * Understands the three line types used by the iptv-org playlists:
 *   #EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Channel Name (quality)
 *   #EXTVLCOPT:http-user-agent=...          (optional, applies to the stream URL right after it)
 *   https://.../stream.m3u8
 *
 * Entries that share the same channel name are merged into one [Channel] with
 * multiple [Channel.streamUrls] mirrors, so playback can fall back automatically.
 */
object M3UParser {

    private val LOGO_REGEX = Regex("""tvg-logo="([^"]*)"""")
    private val GROUP_REGEX = Regex("""group-title="([^"]*)"""")
    private val UA_REGEX = Regex("""http-user-agent=(.+)""")

    fun parse(content: String): List<Channel> {
        val byName = LinkedHashMap<String, Channel>()

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingUa: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val commaIndex = line.indexOf(',')
                    pendingName = if (commaIndex != -1) line.substring(commaIndex + 1).trim() else null
                    pendingLogo = LOGO_REGEX.find(line)?.groupValues?.get(1)
                    pendingGroup = GROUP_REGEX.find(line)?.groupValues?.get(1)
                    pendingUa = null
                }
                line.startsWith("#EXTVLCOPT") -> {
                    pendingUa = UA_REGEX.find(line)?.groupValues?.get(1)?.trim()
                }
                line.startsWith("#") || line.isEmpty() -> {
                    // comment / blank line, ignore
                }
                else -> {
                    // stream URL line
                    val name = pendingName?.takeIf { it.isNotBlank() }
                    if (name != null) {
                        val cleanName = cleanChannelName(name)
                        val existing = byName[cleanName]
                        if (existing != null) {
                            existing.streamUrls.add(line)
                            existing.userAgents.add(pendingUa)
                        } else {
                            byName[cleanName] = Channel(
                                name = cleanName,
                                streamUrls = mutableListOf(line),
                                userAgents = mutableListOf(pendingUa),
                                logoUrl = pendingLogo,
                                category = pendingGroup
                            )
                        }
                    }
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = null
                    pendingUa = null
                }
            }
        }

        return byName.values
            .filter { it.streamUrls.isNotEmpty() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    // Strips the trailing "(1080p)" / "(576p)" quality tag that iptv-org appends,
    // so e.g. "Aaj Tak (1080p)" and "Aaj Tak (576p)" collapse into one channel entry.
    private fun cleanChannelName(name: String): String {
        return name.replace(Regex("""\s*\(\d+p\)\s*$"""), "").trim()
    }
}
