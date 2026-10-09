package com.source.player.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3: adds the `source` column (`local` | `freebox`) to the library tables and to
 * `playlists`, so Freebox-indexed rows can live next to MediaStore rows and each scanner only
 * ever touches its own (playlists are kept per library mode). Existing rows are all
 * MediaStore-backed, hence the `'local'` default. Also adds `songs.remoteMtime` (remote file
 * mtime, used by the incremental Freebox scan). DDL mirrors the exported schema `3.json` exactly.
 */
val MIGRATION_2_3 =
        object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                        for (table in listOf("songs", "albums", "artists", "genres", "playlists")) {
                                db.execSQL(
                                        "ALTER TABLE `$table` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'local'"
                                )
                                db.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_${table}_source` ON `$table` (`source`)"
                                )
                        }
                        db.execSQL(
                                "ALTER TABLE `songs` ADD COLUMN `remoteMtime` INTEGER NOT NULL DEFAULT 0"
                        )
                }
        }
