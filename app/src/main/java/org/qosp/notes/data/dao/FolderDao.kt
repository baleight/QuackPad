package org.qosp.notes.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.qosp.notes.data.model.FolderEntity

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: FolderEntity): Long

    /** Upsert by replacing on conflict — used during filesystem sync. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity): Long

    @Update
    suspend fun update(vararg folders: FolderEntity)

    @Delete
    suspend fun delete(vararg folders: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    fun getById(id: Long): Flow<FolderEntity?>

    /**
     * Returns all folders whose parentId matches [parentId].
     * Pass `null` to get root-level folders.
     */
    @Query("SELECT * FROM folders WHERE parentId IS :parentId ORDER BY name ASC")
    fun getByParent(parentId: Long?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAll(): Flow<List<FolderEntity>>

    /** Look up a folder by its absolute filesystem path — used for idempotent upsert during sync. */
    @Query("SELECT * FROM folders WHERE absolutePath = :path LIMIT 1")
    suspend fun getByPath(path: String): FolderEntity?
}
