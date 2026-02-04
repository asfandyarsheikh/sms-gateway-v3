package com.replyqa.smsgateway

import java.text.SimpleDateFormat
import java.util.*

data class SmsHistoryItem(
    val phoneNumber: String,
    val message: String,
    val timestamp: Long,
    val status: String // "Sent", "Failed", "Rate Limited"
)

data class SmsLimits(
    val fifteenMinLimit: Int = 10,
    val oneHourLimit: Int = 50,
    val dailyLimit: Int = 200
)

data class SmsRequest(
    val to: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)