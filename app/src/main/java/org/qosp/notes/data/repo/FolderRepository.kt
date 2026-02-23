package org.qosp.notes.data.repo

import kotlinx.coroutines.flow.Flow
import org.qosp.notes.data.model.FolderEntity

interface FolderRepository {
    fun getFoldersByParent(parentId: Long?): Flow<List<FolderEntity>>
    fun getFolderById(id: Long): Flow<FolderEntity?>
    fun getAllFolders(): Flow<List<FolderEntity>>
    suspend fun insertFolder(folder: FolderEntity): Long
    suspend fun updateFolder(folder: FolderEntity)
    suspend fun deleteFolder(folder: FolderEntity)
    suspend fun getFolderByPath(path: String): FolderEntity?
    suspend fun upsertFolder(folder: FolderEntity): Long
}
