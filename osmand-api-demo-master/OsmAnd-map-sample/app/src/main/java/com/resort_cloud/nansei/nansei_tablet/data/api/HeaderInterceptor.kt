package com.resort_cloud.nansei.nansei_tablet.data.api

import android.os.Build
import com.resort_cloud.nansei.nansei_tablet.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class HeaderInterceptor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val builder = original.newBuilder()
            .header("DEVICE_INFO", getDeviceInfo())
            .header("Device-Type", "android")
            .header("LANGUAGE", "ja")
            .header("APP_VERSION", BuildConfig.VERSION_NAME)


        return chain.proceed(builder.build())
    }

    fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }
}