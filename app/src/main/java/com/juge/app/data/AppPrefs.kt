package com.juge.app.data

import android.content.Context

/**
 * 本地偏好分区。
 *
 * 原先所有键都塞在 app_settings 一个文件里，导致「隐私同意状态不参与备份」只能整包排除，
 * 顺带把用户自定义颜色也一起排掉了。现在拆成两个文件：
 * - consent_prefs：隐私同意（含同意版本）、遗留迁移标记 —— 敏感，不随系统备份
 * - user_prefs：用户自定义字体色/背景色 —— 属于用户资产，随备份恢复
 *
 * 首次访问会把旧 app_settings 里的键搬迁过来：老用户不会丢颜色，也不会被重新弹隐私协议。
 */
object AppPrefs {

    private const val LEGACY_PREFS = "app_settings"
    private const val CONSENT_PREFS = "consent_prefs"
    private const val USER_PREFS = "user_prefs"

    /** 当前隐私政策版本。政策文案有实质变更时更新此值，App 会重新向用户展示并征求同意。 */
    const val CURRENT_PRIVACY_VERSION = "2026-10-01"

    private const val KEY_PRIVACY_VERSION = "privacy_accepted_version"
    private const val KEY_LEGACY_MIGRATION_DONE = "legacy_migration_done"
    private const val KEY_MIGRATED = "legacy_split_done"

    // 旧 app_settings 中属于「用户资产」的键，迁移到 user_prefs
    private val USER_KEYS = listOf(
        "user_font_color_presets",
        "user_background_color_presets",
        "hidden_font_color_presets",
        "hidden_background_color_presets"
    )

    fun consent(context: Context) =
        context.applicationContext.getSharedPreferences(CONSENT_PREFS, Context.MODE_PRIVATE)

    fun user(context: Context) =
        context.applicationContext.getSharedPreferences(USER_PREFS, Context.MODE_PRIVATE)

    /** 把旧 app_settings 的键搬迁到新分区，幂等。 */
    fun migrateLegacyIfNeeded(context: Context) {
        val app = context.applicationContext
        val consent = consent(app)
        if (consent.getBoolean(KEY_MIGRATED, false)) return
        val legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

        val consentEdit = consent.edit()
        // 老用户已同意过隐私政策：沿用其同意状态并标记为当前版本，不因拆分而重复打扰
        if (legacy.getBoolean("privacy_accepted", false)) {
            consentEdit.putString(KEY_PRIVACY_VERSION, CURRENT_PRIVACY_VERSION)
        }
        if (legacy.contains(KEY_LEGACY_MIGRATION_DONE)) {
            consentEdit.putBoolean(KEY_LEGACY_MIGRATION_DONE, legacy.getBoolean(KEY_LEGACY_MIGRATION_DONE, false))
        }
        consentEdit.putBoolean(KEY_MIGRATED, true).apply()

        val userEdit = user(app).edit()
        USER_KEYS.forEach { key ->
            legacy.getString(key, null)?.let { userEdit.putString(key, it) }
        }
        userEdit.apply()
    }

    /** 用户是否已同意「当前版本」的隐私政策。 */
    fun isPrivacyAccepted(context: Context): Boolean {
        migrateLegacyIfNeeded(context)
        return consent(context).getString(KEY_PRIVACY_VERSION, null) == CURRENT_PRIVACY_VERSION
    }

    fun setPrivacyAccepted(context: Context) {
        consent(context).edit().putString(KEY_PRIVACY_VERSION, CURRENT_PRIVACY_VERSION).apply()
    }

    fun isLegacyMigrationDone(context: Context): Boolean {
        migrateLegacyIfNeeded(context)
        return consent(context).getBoolean(KEY_LEGACY_MIGRATION_DONE, false)
    }

    fun setLegacyMigrationDone(context: Context) {
        consent(context).edit().putBoolean(KEY_LEGACY_MIGRATION_DONE, true).apply()
    }
}