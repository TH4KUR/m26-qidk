package com.megathon.incident.data

import android.content.Context
import com.megathon.incident.scoring.SessionLogCodec
import com.megathon.incident.scoring.SessionLogRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The append-only log of saved reports: `filesDir/logs/sessions.jsonl`, one JSON line per report (spec: offline, on the
 * device only). A line holds the time, the report id, the stage records and the audio score numbers; it never holds a
 * transcript, a name or a place. Lines are only ever added: nothing here edits or deletes a past line.
 */
class SessionLogStore(context: Context) {
    private val dir = File(context.filesDir, "logs")
    private val file = File(dir, "sessions.jsonl")
    private val lock = Mutex()

    suspend fun append(record: SessionLogRecord) {
        lock.withLock {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                file.appendText(SessionLogCodec.encode(record) + "\n")
            }
        }
    }

    /** Every line that reads, oldest first. A missing file is an empty log; a damaged line is skipped. */
    suspend fun readAll(): List<SessionLogRecord> = lock.withLock {
        withContext(Dispatchers.IO) {
            if (file.isFile) SessionLogCodec.parse(file.readText()) else emptyList()
        }
    }
}
