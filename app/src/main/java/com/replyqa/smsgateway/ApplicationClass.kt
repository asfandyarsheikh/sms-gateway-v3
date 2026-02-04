package com.replyqa.smsgateway

import android.app.Application
import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class ApplicationClass : Application() {
    
    companion object {
        private var appInstance: ApplicationClass? = null
        
        fun reinitializeOneSignal(appId: String) {
            if (appId.isNotEmpty() && appInstance != null) {
                Log.d("OneSignal", "Re-initializing with App ID: $appId")
                
                // OneSignal 5.x initialization
                OneSignal.Debug.logLevel = LogLevel.VERBOSE
                OneSignal.initWithContext(appInstance!!, appId)
                
                Log.d("OneSignal", "Re-initialization completed")
            }
        }
        
        fun getOneSignalUserId(): String? {
            return try {
                // OneSignal 5.x API - direct access to onesignalId
                val onesignalId = OneSignal.User.onesignalId
                Log.d("OneSignal", "Retrieved OneSignal ID: $onesignalId")
                onesignalId
            } catch (e: Exception) {
                Log.e("OneSignal", "Error getting OneSignal ID: ${e.message}", e)
                null
            }
        }
        
        fun isOneSignalInitialized(): Boolean {
            return try {
                OneSignal.User.onesignalId != null
            } catch (e: Exception) {
                false
            }
        }
        
        fun getApplicationContext(): ApplicationClass? {
            return appInstance
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        appInstance = this

        val config = AppConfig.retrieve(this)

        if (config.onesignal.isNotEmpty()) {
            Log.d("OneSignal", "Initializing with App ID: ${config.onesignal}")
            
            // OneSignal 5.x initialization
            OneSignal.Debug.logLevel = LogLevel.VERBOSE
            OneSignal.initWithContext(this, config.onesignal)
            
            Log.d("OneSignal", "OneSignal initialized")
        } else {
            Log.w("OneSignal", "No App ID configured")
        }
    }
}