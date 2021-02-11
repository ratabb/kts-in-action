#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib:1.4.30")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.4.2")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.9.0")
@file:DependsOn("com.squareup.okhttp3:logging-interceptor:4.9.0")
@file:DependsOn("com.squareup.okhttp3:okhttp-dnsoverhttps:4.9.0")
@file:DependsOn("com.squareup.retrofit2:retrofit:2.9.0")
@file:DependsOn("com.squareup.retrofit2:converter-moshi:2.9.0")
@file:DependsOn("com.squareup.moshi:moshi:1.11.0")
@file:DependsOn("com.squareup.moshi:moshi-kotlin:1.11.0")
@file:CompilerOptions("-jvm-target", "1.8")

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

interface ImgFlipApi {
    @GET("get_memes")
    suspend fun getMemes(): Response<Data>
}

object ApiProvider {
    private const val CLOUDFLARE_DOH_URL = "https://cloudflare-dns.com/dns-query"
    private const val GOOGLE_DOH_URL = "https://dns.google/dns-query"
    private const val QUAD9_DOH_URL = "https://dns.quad9.net/dns-query"
    private const val API_BASE_URL = "https://api.imgflip.com/"
    
    private val dnsDoH = object : Dns {
        private val multipleDns = mapOf(
            CLOUDFLARE_DOH_URL to arrayOf("1.1.1.1", "1.0.0.1"),
            GOOGLE_DOH_URL to arrayOf("8.8.8.8", "8.8.4.4"),
            QUAD9_DOH_URL to arrayOf("9.9.9.9", "9.9.9.11")
        ).map { (url, host) ->
            DnsOverHttps.Builder()
                .client(OkHttpClient.Builder().build())
                .post(false)
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(host.map(InetAddress::getByName))
                .build()
        } + Dns.SYSTEM //

        override fun lookup(hostname: String): List<InetAddress> {
            val unknowns = ArrayList<UnknownHostException>(4)
            multipleDns.forEach { dns ->
                try {
                    val result = dns.lookup(hostname)
                    if (result.isNotEmpty()) return result //
                } catch (e: UnknownHostException) {
                    unknowns.plusAssign(e)
                }
            }
            val hostException = UnknownHostException(hostname)
            if (unknowns.isNotEmpty()) {
                unknowns.forEachIndexed { i, e ->
                    hostException.apply {
                       if (i == 0) initCause(e) else addSuppressed(e)
                    }
                }
            }
            throw hostException //
        }
    }

    private val httpLog: HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(httpLog)
        .dns(dnsDoH)
        .build()
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val retrofit: Retrofit = Retrofit.Builder().baseUrl(API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
    val api: ImgFlipApi = retrofit.create(ImgFlipApi::class.java)
}

@JsonClass(generateAdapter = true)
data class Meme(val id: String, val name: String, val url: String, val width: Int, val height: Int)

@JsonClass(generateAdapter = true)
data class MemeList(val memes: List<Meme>)

@JsonClass(generateAdapter = true)
data class Data(val data: MemeList)

/** */
runBlocking(Dispatchers.IO) {
    try {
        val response = ApiProvider.api.getMemes()
        val memeList = response.body()
        if (response.isSuccessful && memeList != null) {
            println("Success") //
            memeList.data.memes.forEach {
                println("meme name =${it.name}")
            }
            exitProcess(0)
        } else {
            println("Fail!!: ${response.message()}")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        exitProcess(1)
    }
    awaitCancellation()
}
