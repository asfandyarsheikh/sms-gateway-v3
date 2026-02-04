package com.replyqa.smsgateway

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url


interface Api {
    @POST()
    fun postsms(
        @Url url: String,
        @Header("Authorization") token: String,
        @Body body: SmsData?
    ): Call<JsonObject?>?

    companion object {
        const val BASE_URL = "https://d44d085f-97ee-43a2-bd87-3c2b99f09367.free.beeceptor.com/"
    }
}