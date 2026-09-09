package com.credo.soundgroove.data.db

import com.credo.soundgroove.SoundGrooveDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseRecoveryClassifierTest {

    @Test
    fun detects_recoverable_sqlite_messages() {
        assertTrue(
            SoundGrooveDatabase.looksLikeSqliteFailureMessage(
                "file is encrypted or is not a database / corrupt"
            )
        )
        assertTrue(
            SoundGrooveDatabase.looksLikeSqliteFailureMessage(
                "database or disk is full"
            )
        )
        assertTrue(
            SoundGrooveDatabase.isRecoverableDbFailure(
                RuntimeException("unable to open database file")
            )
        )
        assertTrue(
            SoundGrooveDatabase.isRecoverableDbFailure(
                RuntimeException(RuntimeException("SQLite_Corrupt: disk image is malformed"))
            )
        )
        assertFalse(
            SoundGrooveDatabase.isRecoverableDbFailure(
                IllegalStateException("A migration from 3 to 6 was required but not found")
            )
        )
        assertFalse(
            SoundGrooveDatabase.looksLikeSqliteFailureMessage("migration missing")
        )
    }
}