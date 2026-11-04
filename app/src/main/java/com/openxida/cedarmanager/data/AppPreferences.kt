package com.openxida.cedarmanager.data

import android.content.Context
import com.openxida.cedarmanager.domain.AppSettings
import com.openxida.cedarmanager.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("cedar_settings", Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    fun setThemeMode(value: ThemeMode) = update { copy(themeMode = value) }
    fun setHaptics(value: Boolean) = update { copy(haptics = value) }
    fun setConfirmSystemWrites(value: Boolean) = update { copy(confirmSystemWrites = value) }
    fun setRootFileAccess(value: Boolean) = update { copy(rootFileAccess = value) }
    fun setTerminalFontSize(value: Int) = update { copy(terminalFontSize = value.coerceIn(10, 24)) }
    fun setSafTreeUri(value: String?) = update { copy(safTreeUri = value) }
    fun setAnimationSpeed(value: Float) = update {
        copy(
            animationSpeed = (value.coerceIn(
                AppSettings.MIN_ANIMATION_SPEED,
                AppSettings.MAX_ANIMATION_SPEED,
            ) * 100f).roundToInt() / 100f,
        )
    }

    fun setTiltStrength(value: Float) = update {
        copy(
            tiltStrength = (value.coerceIn(
                AppSettings.MIN_TILT_STRENGTH,
                AppSettings.MAX_TILT_STRENGTH,
            ) * 100f).roundToInt() / 100f,
        )
    }

    private fun update(block: AppSettings.() -> AppSettings) {
        val next = mutableSettings.value.block()
        mutableSettings.value = next
        preferences.edit()
            .putString("theme_mode", next.themeMode.name)
            .putBoolean("haptics", next.haptics)
            .putBoolean("confirm_system_writes", next.confirmSystemWrites)
            .putBoolean("root_file_access", next.rootFileAccess)
            .putInt("terminal_font_size", next.terminalFontSize)
            .putString("saf_tree_uri", next.safTreeUri)
            .putFloat("animation_speed", next.animationSpeed)
            .putFloat("tilt_strength", next.tiltStrength)
            .apply()
    }

    private fun readSettings(): AppSettings = AppSettings(
        themeMode = runCatching {
            ThemeMode.valueOf(preferences.getString("theme_mode", ThemeMode.SYSTEM.name).orEmpty())
        }.getOrDefault(ThemeMode.SYSTEM),
        haptics = preferences.getBoolean("haptics", true),
        confirmSystemWrites = preferences.getBoolean("confirm_system_writes", true),
        rootFileAccess = preferences.getBoolean("root_file_access", preferences.getBoolean("root_file_reads", false)),
        terminalFontSize = preferences.getInt("terminal_font_size", 13),
        safTreeUri = preferences.getString("saf_tree_uri", null),
        animationSpeed = preferences.getFloat("animation_speed", 1f).coerceIn(
            AppSettings.MIN_ANIMATION_SPEED,
            AppSettings.MAX_ANIMATION_SPEED,
        ),
        tiltStrength = preferences.getFloat("tilt_strength", 1f).coerceIn(
            AppSettings.MIN_TILT_STRENGTH,
            AppSettings.MAX_TILT_STRENGTH,
        ),
    )
}
