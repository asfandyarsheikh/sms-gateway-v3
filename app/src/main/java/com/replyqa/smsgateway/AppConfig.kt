package com.replyqa.smsgateway

import android.content.Context

class AppConfig(
    var enabled: Boolean,
    var onesignal: String,
    var api: String,
    var country: String,
    var authorization: String,
    var webhook: String = "",
    var fetchUrl: String = "",
    var operatingMode: String = "onesignal", // "onesignal" or "periodic"
    var smsLimits: SmsLimits = SmsLimits(),
    var webhookValidation: Boolean = false // New switch for webhook validation
) {
    companion object {
        fun retrieve(c: Context): AppConfig {
            val dcountry = c.getString(R.string.default_country)
            val dapi = c.getString(R.string.default_api)
            val donesignal = c.getString(R.string.default_onesignal)
            val dauth = c.getString(R.string.default_auth)
            val denab = true

            val sharedPref = c.getSharedPreferences("app_config", Context.MODE_PRIVATE)
            val country = sharedPref.getString("country", dcountry) ?: dcountry
            val api = sharedPref.getString("api", dapi) ?: dapi
            val onesignal = sharedPref.getString("onesignal", donesignal) ?: donesignal
            val auth = sharedPref.getString("auth", dauth) ?: dauth
            val webhook = sharedPref.getString("webhook", "") ?: ""
            val fetchUrl = sharedPref.getString("fetchUrl", "") ?: ""
            val operatingMode = sharedPref.getString("operatingMode", "onesignal") ?: "onesignal"
            val webhookValidation = sharedPref.getBoolean("webhookValidation", false)
            val enab = sharedPref.getBoolean("enabled", denab)
            
            // Load SMS limits
            val limit15min = sharedPref.getInt("limit15min", 10)
            val limit1hour = sharedPref.getInt("limit1hour", 50)
            val limitDaily = sharedPref.getInt("limitDaily", 200)
            val smsLimits = SmsLimits(limit15min, limit1hour, limitDaily)
            
            return AppConfig(enab, onesignal, api, country, auth, webhook, fetchUrl, operatingMode, smsLimits, webhookValidation)
        }

        fun save(config: AppConfig, c: Context) {
            val sharedPref = c.getSharedPreferences("app_config", Context.MODE_PRIVATE) ?: return
            with(sharedPref.edit()) {
                putString("country", config.country)
                putString("api", config.api)
                putString("onesignal", config.onesignal)
                putString("auth", config.authorization)
                putString("webhook", config.webhook)
                putString("fetchUrl", config.fetchUrl)
                putString("operatingMode", config.operatingMode)
                putBoolean("webhookValidation", config.webhookValidation)
                putBoolean("enabled", config.enabled)
                
                // Save SMS limits
                putInt("limit15min", config.smsLimits.fifteenMinLimit)
                putInt("limit1hour", config.smsLimits.oneHourLimit)
                putInt("limitDaily", config.smsLimits.dailyLimit)
                
                apply()
            }
        }
    }
}