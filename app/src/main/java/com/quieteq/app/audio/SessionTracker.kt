package com.quieteq.app.audio

import com.quieteq.app.core.AttachmentMode

/** Session broadcasts can be duplicated; each package/session pair is tracked once. */
internal class SessionTracker {
    private val owners = mutableMapOf<Int, MutableSet<String>>()
    val sessions: Set<Int> get() = owners.keys.toSet()
    fun open(id: Int, owner: String) {
        if (id <= 0 || (id !in owners && owners.size >= 64)) return
        owners.getOrPut(id) { mutableSetOf() }.add(owner)
    }
    fun close(id: Int, owner: String) {
        owners[id]?.let { it.remove(owner); if (it.isEmpty()) owners.remove(id) }
    }
    fun clear() = owners.clear()
    fun desired(mode: AttachmentMode): Set<Int> = when (mode) {
        AttachmentMode.GLOBAL -> setOf(0)
        AttachmentMode.PLAYERS_ONLY -> sessions
        AttachmentMode.AUTO -> sessions.ifEmpty { setOf(0) }
    }
}
