package com.source.player.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3: adds the `source` column (`local` | `smb`) to the library tables so
 * network (SMB/NAS) rows can live next to MediaStore rows and each scanner only
 * ever touches its own. Existing rows are all MediaStore-backed, hence the
 * `'local'` default. DDL mirrors the exported schema `3.json` exactly.
 */
val MIGRATION_2_3 =
        object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                        for (table in listOf("songs", "albums", "artists", "genres")) {
                                db.execSQL(
                                        "ALTER TABLE `$table` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'local'"
                                )
                                db.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_${table}_source` ON `$table` (`source`)"
                                )
                        }
                }
        }
