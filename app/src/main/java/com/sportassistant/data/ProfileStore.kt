package com.sportassistant.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Profile(
    val onboarded: Boolean = false,
    val age: Int = 42,
    val heightCm: Int = 175,
    val weightKg: Int = 80,
    val sitHours: Int = 8,
    /** 0 = low, 1 = medium, 2 = high. */
    val activity: Int = 0,
    val complaints: Set<Complaint> = emptySet(),
    val remMove: Boolean = true,
    val remWater: Boolean = true,
    val remTeeth: Boolean = true,
    val sex: Sex = Sex.UNSPECIFIED,
    /** The user's own Gemini API key; empty disables photo analysis. Stored in the app-private DataStore. */
    val geminiKey: String = "",
    /** Rep counting strictness: 0 strict, 1 normal, 2 easy (see pose.Sensitivity). */
    val repSensitivity: Int = 1,
)

private val Context.dataStore by preferencesDataStore("profile")

class ProfileStore(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val age = intPreferencesKey("age")
        val height = intPreferencesKey("height_cm")
        val weight = intPreferencesKey("weight_kg")
        val sit = intPreferencesKey("sit_hours")
        val activity = intPreferencesKey("activity")
        val complaints = stringSetPreferencesKey("complaints")
        val remMove = booleanPreferencesKey("rem_move")
        val remWater = booleanPreferencesKey("rem_water")
        val remTeeth = booleanPreferencesKey("rem_teeth")
        val sex = stringPreferencesKey("sex")
        val geminiKey = stringPreferencesKey("gemini_key")
        val repSensitivity = intPreferencesKey("rep_sensitivity")
    }

    val profile: Flow<Profile> = context.dataStore.data.map { it.toProfile() }

    suspend fun save(p: Profile) {
        context.dataStore.edit {
            it[K.onboarded] = p.onboarded
            it[K.age] = p.age
            it[K.height] = p.heightCm
            it[K.weight] = p.weightKg
            it[K.sit] = p.sitHours
            it[K.activity] = p.activity
            it[K.complaints] = p.complaints.map { c -> c.name }.toSet()
            it[K.remMove] = p.remMove
            it[K.remWater] = p.remWater
            it[K.remTeeth] = p.remTeeth
            it[K.sex] = p.sex.name
            it[K.geminiKey] = p.geminiKey
            it[K.repSensitivity] = p.repSensitivity
        }
    }

    private fun Preferences.toProfile(): Profile {
        val d = Profile()
        return Profile(
            onboarded = this[K.onboarded] ?: d.onboarded,
            age = this[K.age] ?: d.age,
            heightCm = this[K.height] ?: d.heightCm,
            weightKg = this[K.weight] ?: d.weightKg,
            sitHours = this[K.sit] ?: d.sitHours,
            activity = this[K.activity] ?: d.activity,
            complaints = (this[K.complaints] ?: emptySet())
                .mapNotNull { n -> Complaint.entries.firstOrNull { it.name == n } }
                .toSet(),
            remMove = this[K.remMove] ?: d.remMove,
            remWater = this[K.remWater] ?: d.remWater,
            remTeeth = this[K.remTeeth] ?: d.remTeeth,
            sex = Sex.entries.firstOrNull { it.name == this[K.sex] } ?: d.sex,
            geminiKey = this[K.geminiKey] ?: d.geminiKey,
            repSensitivity = (this[K.repSensitivity] ?: d.repSensitivity).coerceIn(0, 2),
        )
    }
}
