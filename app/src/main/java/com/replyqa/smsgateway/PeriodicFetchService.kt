package com.replyqa.smsgateway

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PeriodicFetchService : Service() {
    
    private var fetchJob: Job? = null
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private lateinit var smsManagerService: SmsManagerService
    
    override fun onCreate() {
        super.onCreate()
        smsManagerService = SmsManagerService(this)
        Log.d("PeriodicFetch", "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = AppConfig.retrieve(this)
        
        when {
            config.operatingMode == "periodic" && config.fetchUrl.isNotEmpty() -> {
                startPeriodicFetch(config)
            }
            else -> {
                stopPeriodicFetch()
            }
        }
        
        return START_STICKY
    }
    
    private fun startPeriodicFetch(config: AppConfig) {
        stopPeriodicFetch() // Stop any existing job
        
        fetchJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d("PeriodicFetch", "Starting periodic fetch every 10 seconds")
            
            while (isActive) {
                try {
                    fetchAndProcessSms(config)
                } catch (e: Exception) {
                    Log.e("PeriodicFetch", "Error in periodic fetch: ${e.message}", e)
                }
                
                delay(10_000) // 10 seconds
            }
        }
    }
    
    private suspend fun fetchAndProcessSms(config: AppConfig) {
        try {
            val requestBuilder = Request.Builder()
            requestBuilder.url(config.fetchUrl)
            
            // Add authorization header from config
            if (config.authorization.isNotEmpty()) {
                requestBuilder.header("Authorization", config.authorization)
            }
            
            val request = requestBuilder.build()
            
            // Execute request and process response
            val response = client.newCall(request).execute()
            response.use {
                when {
                    it.isSuccessful -> {
                        val responseBody = it.body?.string()
                        responseBody?.takeIf { body -> body.isNotEmpty() }?.let { body ->
                            processFetchedData(body, config)
                        }
                    }
                    else -> Log.w("PeriodicFetch", "Fetch failed with code: ${it.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("PeriodicFetch", "Network error during fetch: ${e.message}", e)
        }
    }
    
    private fun processFetchedData(responseBody: String, config: AppConfig) {
        try {
            val smsRequest = gson.fromJson(responseBody, SmsRequest::class.java)
            when {
                smsRequest.to.isNotEmpty() && smsRequest.message.isNotEmpty() -> {
                    Log.d("PeriodicFetch", "Processing SMS request: ${smsRequest.to}")
                    
                    // Use the SMS manager service to send with rate limiting and webhooks
                    // This will now check webhook response and skip sending if webhook fails
                    val smsSent = smsManagerService.sendSMS(config, smsRequest.to, smsRequest.message)
                    
                    when {
                        smsSent -> {
                            Log.d("PeriodicFetch", "SMS sent successfully, sending acknowledgment")
                            sendAcknowledgment(config, smsRequest)
                        }
                        else -> {
                            Log.w("PeriodicFetch", "SMS sending failed or skipped due to webhook failure, continuing fetch cycle")
                            // Don't send acknowledgment if SMS wasn't sent
                            // The fetch will continue and retry on next cycle
                        }
                    }
                }
                else -> {
                    Log.d("PeriodicFetch", "Invalid SMS request data")
                }
            }
        } catch (e: JsonSyntaxException) {
            Log.d("PeriodicFetch", "No valid SMS request found in response")
        } catch (e: Exception) {
            Log.e("PeriodicFetch", "Error processing fetched data: ${e.message}", e)
        }
    }
    
    private fun sendAcknowledgment(config: AppConfig, smsRequest: SmsRequest) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("PeriodicFetch", "SMS processed: ${smsRequest.to}")
                // Future enhancement: Send DELETE/PUT request to mark SMS as processed
            } catch (e: Exception) {
                Log.e("PeriodicFetch", "Error sending acknowledgment: ${e.message}", e)
            }
        }
    }
    
    private fun stopPeriodicFetch() {
        fetchJob?.cancel()
        fetchJob = null
        Log.d("PeriodicFetch", "Periodic fetch stopped")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicFetch()
        Log.d("PeriodicFetch", "Service destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
