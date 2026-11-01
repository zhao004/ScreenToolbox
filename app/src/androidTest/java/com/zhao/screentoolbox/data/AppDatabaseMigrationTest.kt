package com.zhao.screentoolbox.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhao.screentoolbox.data.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2AddsTouchAlphaAndPreservesLegacyRegionAppearance() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO overlay_configs
                    (id, name, type, enabled, sortOrder, createdAt, updatedAt)
                VALUES
                    ('burn', '旧防烧屏', 'BURN_MASK', 1, 0, 1, 1),
                    ('touch', '旧防误触', 'TOUCH_BLOCK', 1, 1, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO burn_mask_configs
                    (configId, colorArgb, alpha, cornerRadiusDp)
                VALUES ('burn', 4278190080, 0.2, 12.0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO touch_block_configs
                    (configId, boundaryVisible)
                VALUES ('touch', 1)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        ).use { database ->
            assertEquals(2, database.countRows("overlay_configs"))
            assertEquals(1f, database.queryFloat("SELECT alpha FROM burn_mask_configs"))
            assertEquals(0f, database.queryFloat("SELECT alpha FROM touch_block_configs"))
            assertEquals(1, database.queryInt("SELECT boundaryVisible FROM touch_block_configs"))
        }
    }

    private fun SupportSQLiteDatabase.countRows(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.queryFloat(query: String): Float =
        query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getFloat(0)
        }

    private fun SupportSQLiteDatabase.queryInt(query: String): Int =
        query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DATABASE = "migration-1-2-test"
    }
}
