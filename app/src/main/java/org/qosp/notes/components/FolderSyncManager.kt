package org.qosp.notes.components

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.qosp.notes.data.model.FolderEntity
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.FolderRepository
import org.qosp.notes.data.repo.NoteRepository
import java.io.File
import java.time.Instant

/**
 * Keeps the Room database in sync with the real device filesystem.
 *
 * All public methods run on [ioDispatcher] (defaults to [Dispatchers.IO]).
 * Every operation is idempotent — calling it multiple times on the same state
 * produces the same DB result.
 */
class FolderSyncManager(
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val tag = FolderSyncManager::class.java.simpleName

    // --------------------------------------------------------------------------
    // Filesystem → DB sync
    // --------------------------------------------------------------------------

    /**
     * Walks [rootPath] recursively and upserts [FolderEntity] rows for every
     * sub-directory and [Note] rows for every `.md` file found.
     * Folders and notes that exist in the DB but whose path was removed from the
     * filesystem are removed (folders cascade-deleted; notes soft-deleted).
     */
    suspend fun syncFromFilesystem(rootPath: String) = withContext(ioDispatcher) {
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) {
            Log.w(tag, "syncFromFilesystem: root does not exist or is not a directory: $rootPath")
            return@withContext
        }

        val now = Instant.now().epochSecond

        // -- Walk the tree and collect live paths ---------------------------------
        val liveDirectoryPaths = mutableSetOf<String>()
        val liveFilePaths = mutableSetOf<String>()

        root.walkTopDown()
            .onEnter { it.canRead() }
            .forEach { file ->
                when {
                    file.isDirectory && file.absolutePath != rootPath ->
                        liveDirectoryPaths += file.absolutePath
                    file.isFile && file.extension.equals("md", ignoreCase = true) ->
                        liveFilePaths += file.absolutePath
                }
            }

        // -- Upsert folders -------------------------------------------------------
        // Process directories sorted by path length (parents before children)
        val sortedDirs = liveDirectoryPaths.sortedBy { it.length }
        for (dirPath in sortedDirs) {
            val dir = File(dirPath)
            val parentPath = dir.parentFile?.absolutePath
            val parentEntity = if (parentPath != null && parentPath != rootPath) {
                folderRepository.getFolderByPath(parentPath)
            } else null

            val existing = folderRepository.getFolderByPath(dirPath)
            if (existing == null) {
                folderRepository.upsertFolder(
                    FolderEntity(
                        name = dir.name,
                        parentId = parentEntity?.id,
                        absolutePath = dirPath,
                        createdAt = now,
                        modifiedAt = now,
                    )
                )
            } else if (existing.name != dir.name) {
                // Name could have changed on disk (external rename)
                folderRepository.updateFolder(existing.copy(name = dir.name, modifiedAt = now))
            }
        }

        // -- Upsert notes ---------------------------------------------------------
        for (filePath in liveFilePaths) {
            val file = File(filePath)
            val parentPath = file.parentFile?.absolutePath
            val folderEntity = parentPath?.let { folderRepository.getFolderByPath(it) }
            upsertNoteFromFile(file, folderEntity?.id, now)
        }

        // -- Remove orphaned DB records -------------------------------------------
        val allFolders = folderRepository.getAllFolders().first()
        allFolders
            .filter { it.absolutePath !in liveDirectoryPaths }
            .forEach { orphan ->
                Log.d(tag, "syncFromFilesystem: removing orphaned folder '${orphan.absolutePath}'")
                folderRepository.deleteFolder(orphan) // cascades to child folders + sets folderId NULL on notes via FK
            }

        // Soft-delete notes whose file is gone
        val allNotes = noteRepository.getAll().first()
        allNotes
            .filter { note ->
                val path = note.filePath
                path != null && path !in liveFilePaths && File(path).parentFile?.absolutePath?.startsWith(rootPath) == true
            }
            .forEach { note ->
                Log.d(tag, "syncFromFilesystem: soft-deleting orphaned note '${note.filePath}'")
                noteRepository.moveNotesToBin(note, sync = false)
            }

        Log.i(tag, "syncFromFilesystem: done. folders=${liveDirectoryPaths.size}, notes=${liveFilePaths.size}")
    }

    // --------------------------------------------------------------------------
    // Create / Rename / Delete folder
    // --------------------------------------------------------------------------

    /**
     * Creates a real directory on the filesystem and inserts a [FolderEntity] into the DB.
     * @return the new [FolderEntity] with its generated DB id.
     */
    suspend fun createFolder(name: String, parentId: Long?, parentAbsolutePath: String): FolderEntity =
        withContext(ioDispatcher) {
            val parentDir = File(parentAbsolutePath)
            val newDir = File(parentDir, name)
            if (!newDir.exists()) newDir.mkdirs()

            val now = Instant.now().epochSecond
            val entity = FolderEntity(
                name = name,
                parentId = parentId,
                absolutePath = newDir.absolutePath,
                createdAt = now,
                modifiedAt = now,
            )
            val id = folderRepository.insertFolder(entity)
            entity.copy(id = id)
        }

    /**
     * Renames a folder on the filesystem and updates its DB record plus all child paths.
     */
    suspend fun renameFolder(folder: FolderEntity, newName: String) = withContext(ioDispatcher) {
        val oldDir = File(folder.absolutePath)
        val newDir = File(oldDir.parentFile, newName)
        val renamed = oldDir.renameTo(newDir)
        if (!renamed) {
            Log.e(tag, "renameFolder: failed to rename '${folder.absolutePath}' → '${newDir.absolutePath}'")
            return@withContext
        }
        val now = Instant.now().epochSecond
        // Update this folder's record
        folderRepository.updateFolder(
            folder.copy(name = newName, absolutePath = newDir.absolutePath, modifiedAt = now)
        )
        // Fix all child folder paths (they now start with the new parent path)
        updateChildPaths(oldDir.absolutePath, newDir.absolutePath, now)
    }

    /**
     * Deletes a folder (and all its contents) from the filesystem and the DB.
     * The DB cascade rule (`ON DELETE CASCADE`) removes child folder rows; FK
     * `SET NULL` on notes means note rows are kept but lose their folderId.
     */
    suspend fun deleteFolder(folder: FolderEntity) = withContext(ioDispatcher) {
        File(folder.absolutePath).deleteRecursively()
        folderRepository.deleteFolder(folder)
    }

    /**
     * Moves a note's `.md` file to the folder identified by [targetFolderId]
     * (or to [targetAbsolutePath] when the folder path is already known).
     * Updates the note DB record with the new folderId and filePath.
     */
    suspend fun moveNote(note: Note, targetFolderId: Long?, targetAbsolutePath: String?) =
        withContext(ioDispatcher) {
            val sourceFile = note.filePath?.let { File(it) }
            if (sourceFile == null || !sourceFile.exists()) {
                // Note has no file yet; just update the DB record
                noteRepository.updateNotes(
                    note.copy(folderId = targetFolderId, filePath = null),
                    sync = false
                )
                return@withContext
            }

            val destDir = targetAbsolutePath?.let { File(it) } ?: run {
                Log.w(tag, "moveNote: no target path provided, only updating DB")
                noteRepository.updateNotes(
                    note.copy(folderId = targetFolderId),
                    sync = false
                )
                return@withContext
            }

            destDir.mkdirs()
            val destFile = File(destDir, sourceFile.name)
            val moved = sourceFile.renameTo(destFile)
            val newPath = if (moved) destFile.absolutePath else note.filePath

            noteRepository.updateNotes(
                note.copy(folderId = targetFolderId, filePath = newPath),
                sync = false
            )
        }

    // --------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------

    private suspend fun upsertNoteFromFile(file: File, folderId: Long?, now: Long) {
        val filePath = file.absolutePath
        val allNotes = noteRepository.getAll().first()
        val existing = allNotes.firstOrNull { it.filePath == filePath }
        if (existing == null) {
            // New file; create a note from the file content
            val title = file.nameWithoutExtension
            val content = runCatching { file.readText() }.getOrDefault("")
            noteRepository.insertNote(
                Note(
                    title = title,
                    content = content,
                    folderId = folderId,
                    filePath = filePath,
                    creationDate = now,
                    modifiedDate = now,
                    isLocalOnly = true,
                ),
                sync = false
            )
        } else if (existing.folderId != folderId) {
            // Folder assignment changed (e.g. file was moved externally)
            noteRepository.updateNotes(existing.copy(folderId = folderId), sync = false)
        }
    }

    private suspend fun updateChildPaths(oldParentPath: String, newParentPath: String, now: Long) {
        val allFolders = folderRepository.getAllFolders().first()
        allFolders
            .filter { it.absolutePath.startsWith(oldParentPath + File.separator) }
            .forEach { child ->
                val updatedPath = child.absolutePath.replaceFirst(oldParentPath, newParentPath)
                folderRepository.updateFolder(child.copy(absolutePath = updatedPath, modifiedAt = now))
            }
    }
}
