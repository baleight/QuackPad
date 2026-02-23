package org.qosp.notes.data

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.qosp.notes.data.model.FolderEntity
import org.qosp.notes.data.model.Note

class BackupTest {

    @Test
    fun `serialize and deserialize backup with folders`() {
        val note = Note(title = "Test Note")
        val folder = FolderEntity(
            id = 1L,
            name = "Work",
            parentId = null,
            absolutePath = "/storage/emulated/0/QuackPad/Notes/Work",
            createdAt = 1_000_000L,
            modifiedAt = 1_000_000L,
        )
        val backup = Backup(
            notes = setOf(note),
            folders = setOf(folder)
        )
        assertNotNull(backup.serialize())
    }
}