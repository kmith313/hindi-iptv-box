package com.desi.iptvtv

/**
 * One TV channel. [streamUrls] can hold more than one mirror link for the same
 * channel name (the public playlist often lists 2-3 alternate servers) so playback
 * can silently retry the next mirror if the first one is down.
 */
data class Channel(
    val name: String,
    val streamUrls: MutableList<String> = mutableListOf(),
    val userAgents: MutableList<String?> = mutableListOf(),
    val logoUrl: String? = null,
    val category: String? = null
)
