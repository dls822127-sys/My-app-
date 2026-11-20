package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.anantDataStore by preferencesDataStore(name = "anant_drift_offline_prefs")

data class SavedProgress(
    val bestScore: Long = 0L,
    val totalStars: Int = 0,
    val totalDistanceMeters: Long = 0L,
    val unlockedSkins: Set<String> = setOf("nova_dart"),
    val selectedSkinId: String = "nova_dart",
    val magnetLevel: Int = 1,
    val boostLevel: Int = 1,
    val multiplierLevel: Int = 1,
    val soundEnabled: Boolean = true,
    val autoPilotEnabled: Boolean = true,
    val carColorId: String = "cyan",
    val cameraViewName: String = "CHASE",
    val vehicleTypeName: String = "PATROL"
)

class GameStorage(private val context: Context) {

    private object Keys {
        val BEST_SCORE = longPreferencesKey("best_score")
        val TOTAL_STARS = intPreferencesKey("total_stars")
        val TOTAL_DISTANCE = longPreferencesKey("total_distance")
        val UNLOCKED_SKINS = stringSetPreferencesKey("unlocked_skins")
        val SELECTED_SKIN = stringPreferencesKey("selected_skin")
        val MAGNET_LEVEL = intPreferencesKey("magnet_level")
        val BOOST_LEVEL = intPreferencesKey("boost_level")
        val MULTIPLIER_LEVEL = intPreferencesKey("multiplier_level")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val AUTOPILOT_ENABLED = booleanPreferencesKey("autopilot_enabled")
        val CAR_COLOR_ID = stringPreferencesKey("car_color_id")
        val CAMERA_VIEW = stringPreferencesKey("camera_view")
        val VEHICLE_TYPE = stringPreferencesKey("vehicle_type")
    }

    val progressFlow: Flow<SavedProgress> = context.anantDataStore.data.map { prefs ->
        SavedProgress(
            bestScore = prefs[Keys.BEST_SCORE] ?: 0L,
            totalStars = prefs[Keys.TOTAL_STARS] ?: 0,
            totalDistanceMeters = prefs[Keys.TOTAL_DISTANCE] ?: 0L,
            unlockedSkins = prefs[Keys.UNLOCKED_SKINS] ?: setOf("nova_dart"),
            selectedSkinId = prefs[Keys.SELECTED_SKIN] ?: "nova_dart",
            magnetLevel = prefs[Keys.MAGNET_LEVEL] ?: 1,
            boostLevel = prefs[Keys.BOOST_LEVEL] ?: 1,
            multiplierLevel = prefs[Keys.MULTIPLIER_LEVEL] ?: 1,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            autoPilotEnabled = prefs[Keys.AUTOPILOT_ENABLED] ?: true,
            carColorId = prefs[Keys.CAR_COLOR_ID] ?: "cyan",
            cameraViewName = prefs[Keys.CAMERA_VIEW] ?: "CHASE",
            vehicleTypeName = prefs[Keys.VEHICLE_TYPE] ?: "PATROL"
        )
    }

    suspend fun saveRunDelta(
        currentScore: Long,
        starsDelta: Int,
        distanceDeltaMeters: Long
    ) {
        if (currentScore <= 0L && starsDelta <= 0 && distanceDeltaMeters <= 0L) return
        context.anantDataStore.edit { prefs ->
            val prevBest = prefs[Keys.BEST_SCORE] ?: 0L
            if (currentScore > prevBest) {
                prefs[Keys.BEST_SCORE] = currentScore
            }
            if (starsDelta > 0) {
                prefs[Keys.TOTAL_STARS] = (prefs[Keys.TOTAL_STARS] ?: 0) + starsDelta
            }
            if (distanceDeltaMeters > 0L) {
                prefs[Keys.TOTAL_DISTANCE] = (prefs[Keys.TOTAL_DISTANCE] ?: 0L) + distanceDeltaMeters
            }
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.anantDataStore.edit { prefs ->
            prefs[Keys.SOUND_ENABLED] = enabled
        }
    }

    suspend fun setAutoPilotEnabled(enabled: Boolean) {
        context.anantDataStore.edit { prefs ->
            prefs[Keys.AUTOPILOT_ENABLED] = enabled
        }
    }

    suspend fun setCarColorId(colorId: String) {
        context.anantDataStore.edit { prefs ->
            prefs[Keys.CAR_COLOR_ID] = colorId
        }
    }

    suspend fun setCameraViewName(viewName: String) {
        context.anantDataStore.edit { prefs ->
            prefs[Keys.CAMERA_VIEW] = viewName
        }
    }

    suspend fun setVehicleTypeName(typeName: String) {
        context.anantDataStore.edit { prefs ->
            prefs[Keys.VEHICLE_TYPE] = typeName
        }
    }

    suspend fun unlockAndSelectSkin(skinId: String, cost: Int): Boolean {
        var success = false
        context.anantDataStore.edit { prefs ->
            val currentStars = prefs[Keys.TOTAL_STARS] ?: 0
            val unlocked = (prefs[Keys.UNLOCKED_SKINS] ?: setOf("nova_dart")).toMutableSet()
            if (skinId in unlocked) {
                prefs[Keys.SELECTED_SKIN] = skinId
                success = true
            } else if (currentStars >= cost) {
                prefs[Keys.TOTAL_STARS] = currentStars - cost
                unlocked.add(skinId)
                prefs[Keys.UNLOCKED_SKINS] = unlocked
                prefs[Keys.SELECTED_SKIN] = skinId
                success = true
            }
        }
        return success
    }

    suspend fun upgradeStat(statKey: String, cost: Int, maxLevel: Int = 5): Boolean {
        var success = false
        context.anantDataStore.edit { prefs ->
            val key = when (statKey) {
                "magnet" -> Keys.MAGNET_LEVEL
                "boost" -> Keys.BOOST_LEVEL
                "multiplier" -> Keys.MULTIPLIER_LEVEL
                else -> return@edit
            }
            val currentLevel = prefs[key] ?: 1
            val currentStars = prefs[Keys.TOTAL_STARS] ?: 0
            if (currentLevel < maxLevel && currentStars >= cost) {
                prefs[Keys.TOTAL_STARS] = currentStars - cost
                prefs[key] = currentLevel + 1
                success = true
            }
        }
        return success
    }
}
