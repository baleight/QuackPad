package org.qosp.notes.data.repo

import kotlinx.coroutines.flow.Flow
import org.qosp.notes.data.dao.FolderDao
import org.qosp.notes.data.model.FolderEntity

class FolderRepositoryImpl(private val folderDao: FolderDao) : FolderRepository {

    override fun getFoldersByParent(parentId: Long?): Flow<List<FolderEntity>> =
        folderDao.getByParent(parentId)

    override fun getFolderById(id: Long): Flow<FolderEntity?> =
        folderDao.getById(id)

    override fun getAllFolders(): Flow<List<FolderEntity>> =
        folderDao.getAll()

    override suspend fun insertFolder(folder: FolderEntity): Long =
        folderDao.insert(folder)

    override suspend fun updateFolder(folder: FolderEntity) =
        folderDao.update(folder)

    override suspend fun deleteFolder(folder: FolderEntity) =
        folderDao.delete(folder)

    override suspend fun getFolderByPath(path: String): FolderEntity? =
        folderDao.getByPath(path)

    override suspend fun upsertFolder(folder: FolderEntity): Long =
        folderDao.upsert(folder)
}
