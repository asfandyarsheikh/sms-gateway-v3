import com.replyqa.smsgateway.Api
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitClient private constructor() {
    private val myApi: Api
    fun getMyApi(): Api {
        return myApi
    }

    var client = OkHttpClient.Builder().addInterceptor { chain ->
        val originalRequest = chain.request()
        val newRequestBuilder = originalRequest.newBuilder()
        
        // Get authorization header from the request if it exists (passed from caller)
        // This maintains compatibility with existing code that passes auth headers
        val existingAuth = originalRequest.header("Authorization")
        if (existingAuth == null) {
            // If no authorization header exists, we could add a default one here if needed
            // For now, we'll let the individual API calls handle their own auth headers
        }
        
        val newRequest = newRequestBuilder.build()
        chain.proceed(newRequest)
    }.build()

    companion object {
        @get:Synchronized
        var instance: RetrofitClient? = null
            get() {
                if (field == null) {
                    field = RetrofitClient()
                }
                return field
            }
            private set
    }

    init {
        val retrofit: Retrofit = Retrofit.Builder().baseUrl(Api.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        myApi = retrofit.create(Api::class.java)
    }
}