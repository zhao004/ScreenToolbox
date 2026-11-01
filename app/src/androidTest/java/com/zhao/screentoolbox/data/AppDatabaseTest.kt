package com.zhao.screentoolbox.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhao.screentoolbox.core.model.WatermarkMode
import com.zhao.screentoolbox.data.database.AppDatabase
import com.zhao.screentoolbox.data.database.AppRuleConfigCrossRef
import com.zhao.screentoolbox.data.database.AppRuleEntity
import com.zhao.screentoolbox.data.database.OverlayConfigEntity
import com.zhao.screentoolbox.data.database.OverlayRegionEntity
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.data.database.WatermarkStyleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun overlayAndRuleRelationsArePersistedAndCascade() = runBlocking(Dispatchers.IO) {
        val dao = database.overlayDao()
        dao.upsertConfig(
            OverlayConfigEntity(
                id = "watermark",
                name = "水印",
                type = OverlayType.WATERMARK,
                enabled = true,
                sortOrder = 1,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        dao.upsertRegion(
            OverlayRegionEntity("watermark", 0f, 0f, 1f, 1f, 1080, 2400, 0),
        )
        dao.upsertWatermarkStyle(
            WatermarkStyleEntity(
                configId = "watermark",
                text = "ScreenToolbox",
                textSizeSp = 24f,
                fontWeight = 500,
                colorArgb = 0xFFFFFFFF,
                alpha = 0.3f,
                rotationDegrees = -20f,
                mode = WatermarkMode.TILED,
                tileSpacingDp = 96f,
                positionXRatio = 0.5f,
                positionYRatio = 0.5f,
            ),
        )
        val ruleId = database.appRuleDao().upsertRule(
            AppRuleEntity(
                packageName = "target.app",
                displayName = "Target",
                enabled = true,
                burnMaskEnabled = true,
                touchBlockEnabled = true,
                watermarkEnabled = true,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        database.appRuleDao().insertCrossRefs(listOf(AppRuleConfigCrossRef(ruleId, "watermark")))

        assertEquals(1, dao.observeAll().first().size)
        assertEquals(1, database.appRuleDao().observeAll().first().single().configs.size)

        dao.delete("watermark")
        assertEquals(0, database.appRuleDao().observeAll().first().single().configs.size)
    }
}
