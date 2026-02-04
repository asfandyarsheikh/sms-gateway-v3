package com.replyqa.smsgateway

import RetrofitClient
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class SmsListener : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                val config = AppConfig.retrieve(context)
                val messageBody = smsMessage.messageBody
                val from = smsMessage.originatingAddress
                postsms(config, messageBody, from)
            }
        }
    }

    private fun postsms(config: AppConfig, msg: String, from: String?) {
        if (!config.enabled) {
            return
        }
        if (from?.indexOf(config.country) != 0) {
            return
        }
        val call: Call<JsonObject?>? =
            RetrofitClient.instance?.getMyApi()
                ?.postsms(config.api, config.authorization, SmsData(msg, from));
        call?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>?, response: Response<JsonObject?>) {
            }

            override fun onFailure(call: Call<JsonObject?>?, t: Throwable?) {}
        })
    }
}