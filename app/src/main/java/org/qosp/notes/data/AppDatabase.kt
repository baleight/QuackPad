package org.qosp.notes.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.qosp.notes.data.dao.FolderDao
import org.qosp.notes.data.dao.IdMappingDao
import org.qosp.notes.data.dao.NoteDao
import org.qosp.notes.data.dao.NoteTagDao
import org.qosp.notes.data.dao.ReminderDao
import org.qosp.notes.data.dao.TagDao
import org.qosp.notes.data.model.FolderEntity
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.NoteEntity
import org.qosp.notes.data.model.NoteTagJoin
import org.qosp.notes.data.model.Reminder
import org.qosp.notes.data.model.Tag

@Database(
    entities = [
        NoteEntity::class,
        NoteTagJoin::class,
        FolderEntity::class,
        Tag::class,
        Reminder::class,
        IdMapping::class,
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract val noteDao: NoteDao
    abstract val folderDao: FolderDao
    abstract val noteTagDao: NoteTagDao
    abstract val tagDao: TagDao
    abstract val reminderDao: ReminderDao
    abstract val idMappingDao: IdMappingDao

    companion object {
        const val DB_NAME = "notes_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE notes ADD COLUMN isCompactPreview INTEGER NOT NULL DEFAULT (0)")
                }
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE notes ADD COLUMN screenAlwaysOn INTEGER NOT NULL DEFAULT (0)")
                }
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE cloud_ids ADD COLUMN storageUri TEXT")
                }
            }
        }
        
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // version 5 might have just been schema changes handled by fallback, or no-op
                // Adding a dummy one if it didn't exist, just so Room binds the graph
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    // Drop old notebooks table (data is intentionally lost per spec)
                    execSQL("DROP TABLE IF EXISTS notebooks")

                    // Create the new folders table
                    execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `folders` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `parentId` INTEGER,
                            `absolutePath` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `modifiedAt` INTEGER NOT NULL,
                            FOREIGN KEY(`parentId`) REFERENCES `folders`(`id`) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)"
                    )
                    execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_absolutePath` ON `folders` (`absolutePath`)"
                    )

                    // Add folder reference + filesystem path columns to notes
                    execSQL("ALTER TABLE notes ADD COLUMN `folderId` INTEGER")
                    execSQL("ALTER TABLE notes ADD COLUMN `filePath` TEXT")
                    // Create the index on folderId that Room expects
                    execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)"
                    )
                    // All existing notes start at root (folderId = NULL)
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE notes ADD COLUMN eventDate INTEGER")
                    execSQL("ALTER TABLE notes ADD COLUMN priority TEXT NOT NULL DEFAULT 'NONE'")
                }
            }
        }

        val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    // Drop old notebooks table (data is intentionally lost per spec)
                    execSQL("DROP TABLE IF EXISTS notebooks")

                    // Create the new folders table
                    execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `folders` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `parentId` INTEGER,
                            `absolutePath` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `modifiedAt` INTEGER NOT NULL,
                            FOREIGN KEY(`parentId`) REFERENCES `folders`(`id`) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_absolutePath` ON `folders` (`absolutePath`)")

                    // Add folder reference + filesystem path columns to notes
                    execSQL("ALTER TABLE notes ADD COLUMN `folderId` INTEGER")
                    execSQL("ALTER TABLE notes ADD COLUMN `filePath` TEXT")
                    execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")
                    
                    execSQL("ALTER TABLE notes ADD COLUMN eventDate INTEGER")
                    execSQL("ALTER TABLE notes ADD COLUMN priority TEXT NOT NULL DEFAULT 'NONE'")
                }
            }
        }
    }
}
