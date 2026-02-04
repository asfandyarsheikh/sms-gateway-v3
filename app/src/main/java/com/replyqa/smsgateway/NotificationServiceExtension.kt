package com.replyqa.smsgateway

import android.content.Context
import android.util.Log
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

class NotificationServiceExtension : INotificationServiceExtension {
    
    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        Log.d("SmsGateway", "Notification received - processing silently")
        
        val notification = event.notification
        val phoneNumber = notification.title
        val message = notification.body
        
        Log.d("SmsGateway", "Phone: $phoneNumber, Message: $message")
        
        // Get context from the notification event
        val context = event.notification.androidNotificationId.let { 
            // Try to get context through the ApplicationClass instance
            ApplicationClass.getApplicationContext()
        }
        
        if (context != null) {
            // Use the SMS manager service
            val smsManagerService = SmsManagerService(context)
            val config = AppConfig.retrieve(context)
            smsManagerService.sendSMS(config, phoneNumber, message)
            Log.d("SmsGateway", "SMS sent with rate limiting")
        } else {
            Log.e("SmsGateway", "Could not get application context")
        }
        
        // Prevent notification from being displayed (silent processing)
        event.preventDefault()
        
        Log.d("SmsGateway", "Notification processed silently")
    }
}