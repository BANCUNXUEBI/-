package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ocr.OcrOptions
import com.example.ocr.OcrProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val providerKey = stringPreferencesKey("ocr_provider")
    private val docOrientKey = booleanPreferencesKey("use_doc_orientation")
    private val docUnwarpKey = booleanPreferencesKey("use_doc_unwarping")
    private val tokenKey = stringPreferencesKey("paddle_ocr_token")
    private val unitPriceKey = androidx.datastore.preferences.core.floatPreferencesKey("unit_price")
    private val devModeKey = booleanPreferencesKey("dev_mode")
    private val showGuidanceKey = booleanPreferencesKey("show_guidance")

    val currentProvider: Flow<OcrProviderType> = context.dataStore.data.map { prefs ->
        val name = prefs[providerKey] ?: OcrProviderType.PADDLE_OCR.name
        try {
            OcrProviderType.valueOf(name)
        } catch (e: Exception) {
            OcrProviderType.PADDLE_OCR
        }
    }

    val currentOptions: Flow<OcrOptions> = context.dataStore.data.map { prefs ->
        OcrOptions(
            useDocOrientationClassify = prefs[docOrientKey] ?: true,
            useDocUnwarping = prefs[docUnwarpKey] ?: true,
            useChartRecognition = false
        )
    }

    val paddleOcrToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[tokenKey]
    }
    
    val unitPriceFlow: Flow<Float> = context.dataStore.data.map { it[unitPriceKey] ?: 0.9f }
    val devModeFlow: Flow<Boolean> = context.dataStore.data.map { it[devModeKey] ?: false }
    val showGuidanceFlow: Flow<Boolean> = context.dataStore.data.map { it[showGuidanceKey] ?: true }

    suspend fun setUnitPrice(price: Float) {
        context.dataStore.edit { it[unitPriceKey] = price }
    }

    suspend fun setDevMode(enabled: Boolean) {
        context.dataStore.edit { it[devModeKey] = enabled }
    }

    suspend fun setShowGuidance(show: Boolean) {
        context.dataStore.edit { it[showGuidanceKey] = show }
    }

    suspend fun getPaddleOcrToken(): String? {
        val fromSettings = context.dataStore.data.first()[tokenKey]
        if (!fromSettings.isNullOrBlank()) {
            return fromSettings
        }
        val fromEnv = com.example.BuildConfig.PADDLEOCR_TOKEN
        if (fromEnv.isNotBlank() && !fromEnv.startsWith("MY_")) {
            return fromEnv
        }
        return null
    }

    suspend fun setProvider(type: OcrProviderType) {
        context.dataStore.edit { prefs ->
            prefs[providerKey] = type.name
        }
    }

    suspend fun setOptions(useDocOrientation: Boolean, useDocUnwarping: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[docOrientKey] = useDocOrientation
            prefs[docUnwarpKey] = useDocUnwarping
        }
    }

    suspend fun setPaddleOcrToken(token: String?) {
        context.dataStore.edit { prefs ->
            if (token != null) {
                prefs[tokenKey] = token
            } else {
                prefs.remove(tokenKey)
            }
        }
    }
}
