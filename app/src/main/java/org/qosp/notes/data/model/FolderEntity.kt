package org.qosp.notes.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("parentId"),
        Index("absolutePath", unique = true)
    ]
)
@Serializable
@Parcelize
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Null means root level (no parent folder). */
    val parentId: Long?,
    /** Real absolute path of this directory on the filesystem. */
    val absolutePath: String,
    val createdAt: Long,
    val modifiedAt: Long,
) : Parcelable
