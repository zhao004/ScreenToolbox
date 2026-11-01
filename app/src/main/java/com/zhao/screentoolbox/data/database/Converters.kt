package com.zhao.screentoolbox.data.database

import androidx.room.TypeConverter
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.core.model.WatermarkMode

class Converters {
    @TypeConverter
    fun overlayTypeToString(value: OverlayType): String = value.name

    @TypeConverter
    fun stringToOverlayType(value: String): OverlayType = OverlayType.valueOf(value)

    @TypeConverter
    fun watermarkModeToString(value: WatermarkMode): String = value.name

    @TypeConverter
    fun stringToWatermarkMode(value: String): WatermarkMode = WatermarkMode.valueOf(value)
}
