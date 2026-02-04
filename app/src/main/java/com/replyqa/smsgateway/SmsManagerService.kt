package com.replyqa.smsgateway

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SmsManagerService(private val context: Context) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val SMS_HISTORY_KEY = "sms_history"
        private const val MAX_HISTORY_SIZE = 10
    }

    fun sendSMS(config: AppConfig, phoneNo: String?, msg: String?): Boolean {
        if (!config.enabled) {
            Log.w("SmsGateway", "SMS Gateway is disabled")
            return false
        }
        
        if (phoneNo.isNullOrEmpty() || msg.isNullOrEmpty()) {
            Log.w("SmsGateway", "Phone number or message is empty")
            return false
        }
        
        if (!phoneNo.startsWith(config.country)) {
            Log.w("SmsGateway", "Phone number doesn't match country code: ${config.country}")
            return false
        }

        // Check rate limits
        if (!checkRateLimits(config.smsLimits)) {
            Log.w("SmsGateway", "Rate limit exceeded")
            addToHistory(SmsHistoryItem(phoneNo, msg, System.currentTimeMillis(), "Rate Limited"))
            return false
        }

        // Send webhook before SMS and check response - based on webhookValidation setting
        if (config.webhook.isNotEmpty() && config.webhookValidation) {
            val webhookSuccess = sendWebhookSync(config.webhook, "before_sms", phoneNo, msg)
            if (!webhookSuccess) {
                Log.w("SmsGateway", "Before SMS webhook failed or returned non-OK status, skipping SMS (validation enabled)")
                addToHistory(SmsHistoryItem(phoneNo, msg, System.currentTimeMillis(), "Webhook Failed"))
                return false
            }
        } else if (config.webhook.isNotEmpty()) {
            // For disabled validation, send webhook but don't check response (fire and forget)
            sendWebhook(config.webhook, "before_sms", phoneNo, msg)
        }

        return try {
            Log.d("SmsGateway", "Sending SMS to $phoneNo: $msg")
            val smsManager: SmsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(msg)
            smsManager.sendMultipartTextMessage(phoneNo, null, parts, null, null)
            
            // Add to history
            addToHistory(SmsHistoryItem(phoneNo, msg, System.currentTimeMillis(), "Sent"))
            
            // Send webhook after successful SMS
            if (config.webhook.isNotEmpty()) {
                sendWebhook(config.webhook, "after_sms_success", phoneNo, msg)
            }
            
            Log.d("SmsGateway", "SMS sent successfully")
            true
        } catch (ex: Exception) {
            Log.e("SmsGateway", "Failed to send SMS: ${ex.message}", ex)
            
            // Add to history
            addToHistory(SmsHistoryItem(phoneNo, msg, System.currentTimeMillis(), "Failed"))
            
            // Send webhook after failed SMS
            if (config.webhook.isNotEmpty()) {
                sendWebhook(config.webhook, "after_sms_error", phoneNo, msg, ex.message)
            }
            
            false
        }
    }

    private fun checkRateLimits(limits: SmsLimits): Boolean {
        val history = getSmsHistory()
        val now = System.currentTimeMillis()
        
        // Filter successful SMS only for rate limiting
        val sentSms = history.filter { it.status == "Sent" }
        
        // Check 15 minute limit
        val fifteenMinAgo = now - (15 * 60 * 1000)
        val recentFifteenMin = sentSms.count { it.timestamp >= fifteenMinAgo }
        if (recentFifteenMin >= limits.fifteenMinLimit) {
            Log.w("SmsGateway", "15-minute limit exceeded: $recentFifteenMin >= ${limits.fifteenMinLimit}")
            return false
        }
        
        // Check 1 hour limit
        val oneHourAgo = now - (60 * 60 * 1000)
        val recentOneHour = sentSms.count { it.timestamp >= oneHourAgo }
        if (recentOneHour >= limits.oneHourLimit) {
            Log.w("SmsGateway", "1-hour limit exceeded: $recentOneHour >= ${limits.oneHourLimit}")
            return false
        }
        
        // Check daily limit
        val oneDayAgo = now - (24 * 60 * 60 * 1000)
        val recentOneDay = sentSms.count { it.timestamp >= oneDayAgo }
        if (recentOneDay >= limits.dailyLimit) {
            Log.w("SmsGateway", "Daily limit exceeded: $recentOneDay >= ${limits.dailyLimit}")
            return false
        }
        
        return true
    }

    private fun sendWebhook(webhookUrl: String, event: String, phoneNo: String, message: String, error: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = mutableMapOf<String, Any>(
                    "event" to event,
                    "phone_number" to phoneNo,
                    "message" to message,
                    "timestamp" to System.currentTimeMillis()
                )
                
                // Add OneSignal ID to webhook payload
                val oneSignalId = ApplicationClass.getOneSignalUserId()
                if (!oneSignalId.isNullOrEmpty()) {
                    payload["onesignal_id"] = oneSignalId
                }
                
                error?.let { payload["error"] = it }
                
                val jsonPayload = gson.toJson(payload)
                val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
                
                val requestBuilder = Request.Builder()
                    .url(webhookUrl)
                    .post(requestBody)
                
                // Add authorization header from config
                val config = AppConfig.retrieve(context)
                if (config.authorization.isNotEmpty()) {
                    requestBuilder.header("Authorization", config.authorization)
                }
                
                val request = requestBuilder.build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("SmsGateway", "Webhook sent successfully: $event")
                    } else {
                        Log.w("SmsGateway", "Webhook failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsGateway", "Webhook error: ${e.message}", e)
            }
        }
    }

    private fun sendWebhookSync(webhookUrl: String, event: String, phoneNo: String, message: String): Boolean {
        return try {
            val payload = mutableMapOf<String, Any>(
                "event" to event,
                "phone_number" to phoneNo,
                "message" to message,
                "timestamp" to System.currentTimeMillis()
            )
            
            // Add OneSignal ID to webhook payload
            val oneSignalId = ApplicationClass.getOneSignalUserId()
            if (!oneSignalId.isNullOrEmpty()) {
                payload["onesignal_id"] = oneSignalId
            }
            
            val jsonPayload = gson.toJson(payload)
            val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
            
            val requestBuilder = Request.Builder()
                .url(webhookUrl)
                .post(requestBody)
            
            // Add authorization header from config
            val config = AppConfig.retrieve(context)
            if (config.authorization.isNotEmpty()) {
                requestBuilder.header("Authorization", config.authorization)
            }
            
            val request = requestBuilder.build()
            
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        Log.d("SmsGateway", "Synchronous webhook sent successfully: $event")
                        true
                    }
                    else -> {
                        Log.w("SmsGateway", "Synchronous webhook failed: ${response.code}")
                        false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsGateway", "Synchronous webhook error: ${e.message}", e)
            false
        }
    }

    private fun addToHistory(smsItem: SmsHistoryItem) {
        val history = getSmsHistory().toMutableList()
        history.add(0, smsItem) // Add to beginning
        
        // Keep only the last 10 items
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }
        
        saveSmsHistory(history)
    }

    fun getSmsHistory(): List<SmsHistoryItem> {
        val sharedPref = context.getSharedPreferences("sms_gateway", Context.MODE_PRIVATE)
        val historyJson = sharedPref.getString(SMS_HISTORY_KEY, "[]")
        val type = object : TypeToken<List<SmsHistoryItem>>() {}.type
        return try {
            gson.fromJson(historyJson, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("SmsGateway", "Error parsing SMS history", e)
            emptyList()
        }
    }

    private fun saveSmsHistory(history: List<SmsHistoryItem>) {
        val sharedPref = context.getSharedPreferences("sms_gateway", Context.MODE_PRIVATE)
        val historyJson = gson.toJson(history)
        with(sharedPref.edit()) {
            putString(SMS_HISTORY_KEY, historyJson)
            apply()
        }
    }

    fun clearHistory() {
        val sharedPref = context.getSharedPreferences("sms_gateway", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            remove(SMS_HISTORY_KEY)
            apply()
        }
    }
}