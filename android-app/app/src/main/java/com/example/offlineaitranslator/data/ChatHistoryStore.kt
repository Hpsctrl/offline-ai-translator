package com.example.offlineaitranslator.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tiny JSON-backed store for translation history.
 *
 * Persists to [file] (typically `context.filesDir/chat_history.json`) using
 * `org.json`. Missing, empty or malformed files are treated as empty history;
 * write failures are ignored so the app never crashes because of storage.
 */
class ChatHistoryStore(private val file: File) {

    data class ChatEntry(
        val id: String,
        val source: String,
        val input: String,
        val output: String,
        val targetLang: String,
        val pinned: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        const val FILE_NAME: String = "chat_history.json"
    }

    /** Loads all entries, or an empty list when the file is absent/corrupt. */
    @Synchronized
    fun load(): List<ChatEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val raw = file.readText(Charsets.UTF_8)
            if (raw.isBlank()) return emptyList()
            val array = JSONArray(raw)
            val entries = mutableListOf<ChatEntry>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                if (id.isEmpty()) continue
                entries.add(
                    ChatEntry(
                        id = id,
                        source = obj.optString("source", ""),
                        input = obj.optString("input", ""),
                        output = obj.optString("output", ""),
                        targetLang = obj.optString("targetLang", ""),
                        pinned = obj.optBoolean("pinned", false),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            entries
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** Appends [entry] and persists the whole history. */
    @Synchronized
    fun add(entry: ChatEntry) {
        persist(load() + entry)
    }

    /** Removes every entry and persists an empty history. */
    @Synchronized
    fun clear() {
        persist(emptyList())
    }

    /** Flips the pinned flag of the entry with [id], if it exists. */
    @Synchronized
    fun togglePin(id: String) {
        persist(
            load().map { entry ->
                if (entry.id == id) entry.copy(pinned = !entry.pinned) else entry
            }
        )
    }

    private fun persist(entries: List<ChatEntry>) {
        try {
            val array = JSONArray()
            for (entry in entries) {
                val obj = JSONObject()
                obj.put("id", entry.id)
                obj.put("source", entry.source)
                obj.put("input", entry.input)
                obj.put("output", entry.output)
                obj.put("targetLang", entry.targetLang)
                obj.put("pinned", entry.pinned)
                obj.put("timestamp", entry.timestamp)
                array.put(obj)
            }
            file.parentFile?.mkdirs()
            file.writeText(array.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            // Best-effort persistence: keep in-memory state usable on failure.
        }
    }
}

/** Convenience alias so callers may also use `ChatEntry` directly. */
typealias ChatEntry = ChatHistoryStore.ChatEntry
