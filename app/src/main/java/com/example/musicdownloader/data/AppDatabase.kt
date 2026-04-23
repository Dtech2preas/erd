package com.example.musicdownloader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Song::class,
        PlayHistory::class,
        FavoriteSong::class,
        Playlist::class,
        PlaylistEntry::class,
        StreamCache::class,
        StreamSong::class,
        LyricsEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun streamCacheDao(): StreamCacheDao
    abstract fun streamSongDao(): StreamSongDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration() // Simple migration strategy for this overhaul
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the 'album' column with a default value
                database.execSQL("ALTER TABLE songs ADD COLUMN album TEXT NOT NULL DEFAULT 'Unknown Album'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stream_cache` (
                        `videoId` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL,
                        `expireTime` INTEGER NOT NULL,
                        `cachedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stream_songs` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `duration` TEXT NOT NULL,
                        `thumbnailUrl` TEXT NOT NULL,
                        `isManual` INTEGER NOT NULL DEFAULT 0,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Remove ForeignKey to Song to allow StreamSongs in Playlists
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_entries_new` (
                        `playlistId` INTEGER NOT NULL,
                        `songId` TEXT NOT NULL,
                        PRIMARY KEY(`playlistId`, `songId`),
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                database.execSQL("""
                    INSERT INTO `playlist_entries_new` (`playlistId`, `songId`)
                    SELECT `playlistId`, `songId` FROM `playlist_entries`
                """.trimIndent())

                database.execSQL("DROP TABLE `playlist_entries`")
                database.execSQL("ALTER TABLE `playlist_entries_new` RENAME TO `playlist_entries`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_entries_playlistId` ON `playlist_entries` (`playlistId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_entries_songId` ON `playlist_entries` (`songId`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `lyrics` (
                        `id` TEXT NOT NULL,
                        `lyrics` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }
    }
}
