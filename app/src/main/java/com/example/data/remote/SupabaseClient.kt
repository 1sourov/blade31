package com.example.data.remote

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.AppPreferences
import com.example.data.model.AuthSession
import com.example.data.model.RefreshTokenRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

object JwtTokenUtils {
    private val expRegex = Regex("\"exp\"\\s*:\\s*(\\d+)")
    private val publicReadPaths = setOf(
        "/rest/v1/videos",
        "/rest/v1/hashtags",
        "/rest/v1/sounds",
        "/rest/v1/advertisements",
        "/rest/v1/live_streams",
        "/rest/v1/app_settings",
        "/rest/v1/comments",
        "/rest/v1/users",
        "/rest/v1/likes"
    )

    fun parseJwtExpEpochSeconds(jwt: String): Long? {
        val parts = jwt.trim().split('.')
        if (parts.size < 2) return null
        val payloadJson = runCatching {
            val padded = when (parts[1].length % 4) {
                2 -> parts[1] + "=="
                3 -> parts[1] + "="
                else -> parts[1]
            }
            String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
        }.getOrNull() ?: return null
        return expRegex.find(payloadJson)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    fun isJwtExpiredOrExpiringSoon(
        jwt: String,
        nowEpochSec: Long = System.currentTimeMillis() / 1000L,
        leewaySeconds: Long = 60L
    ): Boolean {
        if (jwt.isBlank()) return false
        val exp = parseJwtExpEpochSeconds(jwt) ?: return false
        return (exp - leewaySeconds) <= nowEpochSec
    }

    fun isPublicReadEndpoint(method: String, encodedPath: String): Boolean {
        if (!method.equals("GET", ignoreCase = true)) return false
        return encodedPath in publicReadPaths
    }

    fun isPermanentRefreshFailure(httpCode: Int, errorBody: String): Boolean {
        if (httpCode == 429 || httpCode >= 500) return false
        if (httpCode in 400..403) {
            val lower = errorBody.lowercase()
            return lower.isBlank() ||
                lower.contains("invalid_grant") ||
                lower.contains("refresh_token") ||
                lower.contains("already used") ||
                lower.contains("invalid") ||
                lower.contains("expired") ||
                lower.contains("revoked") ||
                lower.contains("not_found")
        }
        return false
    }
}

sealed class RefreshOutcome {
    data class Refreshed(val accessToken: String) : RefreshOutcome()
    data object SessionInvalid : RefreshOutcome()
    data object TransientFailure : RefreshOutcome()
}

class SupabaseClient(private val preferences: AppPreferences) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val authSessionAdapter by lazy { moshi.adapter(AuthSession::class.java) }
    private val refreshReqAdapter by lazy { moshi.adapter(RefreshTokenRequest::class.java) }
    private val refreshLock = Any()

    // Dedicated HTTP client without authInterceptor for token refresh calls (prevents recursion/deadlock)
    private val refreshHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .retryOnConnectionFailure(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val path = original.url.encodedPath
        val isPublicAuthCall = path.endsWith("/auth/v1/signup") ||
            path.endsWith("/auth/v1/token") ||
            path.endsWith("/auth/v1/otp") ||
            path.endsWith("/auth/v1/verify") ||
            path.endsWith("/auth/v1/recover")
        val isSignOutCall = path.endsWith("/auth/v1/logout")
        val alreadyRetried = original.header("X-Blade-Auth-Retried") == "1"
        val isPublicRead = JwtTokenUtils.isPublicReadEndpoint(original.method, path)

        val session = runBlocking { preferences.getSessionSnapshot() }
        var usedUserToken: String? = null
        val token = when {
            isPublicAuthCall -> SupabaseConfig.anonKey
            session.accessToken.isBlank() -> SupabaseConfig.anonKey
            JwtTokenUtils.isJwtExpiredOrExpiringSoon(session.accessToken) -> {
                when (val outcome = ensureFreshTokenBlocking(forceRefresh = false)) {
                    is RefreshOutcome.Refreshed -> {
                        usedUserToken = outcome.accessToken
                        outcome.accessToken
                    }
                    is RefreshOutcome.SessionInvalid -> SupabaseConfig.anonKey
                    is RefreshOutcome.TransientFailure -> {
                        if (isPublicRead) {
                            SupabaseConfig.anonKey
                        } else {
                            usedUserToken = session.accessToken
                            session.accessToken
                        }
                    }
                }
            }
            else -> {
                usedUserToken = session.accessToken
                session.accessToken
            }
        }

        fun buildReq(bearer: String, markRetried: Boolean = false): Request {
            val builder = original.newBuilder()
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $bearer")
            if (original.header("Content-Type") == null && original.body?.contentType() == null) {
                builder.header("Content-Type", "application/json")
            }
            if (markRetried) {
                builder.header("X-Blade-Auth-Retried", "1")
            }
            return builder.build()
        }

        val response = chain.proceed(buildReq(token))

        if (response.code == 401 && !isPublicAuthCall && !isSignOutCall && !alreadyRetried && usedUserToken != null) {
            when (val outcome = ensureFreshTokenBlocking(forceRefresh = true, failedAccessToken = usedUserToken)) {
                is RefreshOutcome.Refreshed -> {
                    response.close()
                    return@Interceptor chain.proceed(buildReq(outcome.accessToken, markRetried = true))
                }
                is RefreshOutcome.SessionInvalid, is RefreshOutcome.TransientFailure -> {
                    if (isPublicRead) {
                        response.close()
                        return@Interceptor chain.proceed(buildReq(SupabaseConfig.anonKey, markRetried = true))
                    }
                }
            }
        }

        response
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .retryOnConnectionFailure(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun baseUrlOrPlaceholder(): String {
        val configured = SupabaseConfig.url
        val candidate = if (configured.startsWith("http://") || configured.startsWith("https://")) {
            "$configured/"
        } else {
            "https://your-project.supabase.co/"
        }
        return okhttp3.HttpUrl.run { candidate.toHttpUrlOrNull() }?.toString()
            ?: "https://your-project.supabase.co/"
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrlOrPlaceholder())
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val authApi: SupabaseAuthApi by lazy { retrofit.create(SupabaseAuthApi::class.java) }
    val restApi: SupabaseRestApi by lazy { retrofit.create(SupabaseRestApi::class.java) }
    val realtimeClient: SupabaseRealtimeClient by lazy { SupabaseRealtimeClient(okHttpClient) }

    fun ensureFreshTokenBlocking(
        forceRefresh: Boolean = false,
        failedAccessToken: String? = null
    ): RefreshOutcome {
        if (!SupabaseConfig.isConfigured) return RefreshOutcome.TransientFailure
        synchronized(refreshLock) {
            val current = runBlocking { preferences.getSessionSnapshot() }
            if (!current.isLoggedIn) {
                return RefreshOutcome.SessionInvalid
            }

            if (!forceRefresh && !JwtTokenUtils.isJwtExpiredOrExpiringSoon(current.accessToken)) {
                return RefreshOutcome.Refreshed(current.accessToken)
            }

            if (
                forceRefresh &&
                !failedAccessToken.isNullOrBlank() &&
                current.accessToken.isNotBlank() &&
                current.accessToken != failedAccessToken &&
                !JwtTokenUtils.isJwtExpiredOrExpiringSoon(current.accessToken)
            ) {
                return RefreshOutcome.Refreshed(current.accessToken)
            }

            if (current.refreshToken.isBlank()) {
                return if (JwtTokenUtils.isJwtExpiredOrExpiringSoon(current.accessToken, leewaySeconds = 0L)) {
                    runBlocking { preferences.clearSession() }
                    RefreshOutcome.SessionInvalid
                } else if (!forceRefresh) {
                    RefreshOutcome.Refreshed(current.accessToken)
                } else {
                    runBlocking { preferences.clearSession() }
                    RefreshOutcome.SessionInvalid
                }
            }

            val refreshUrl = "${baseUrlOrPlaceholder()}auth/v1/token?grant_type=refresh_token"
            val payloadJson = refreshReqAdapter.toJson(RefreshTokenRequest(current.refreshToken))
            val jsonMediaType = "application/json".toMediaType()

            for (attempt in 0..1) {
                var resp: Response? = null
                try {
                    val req = Request.Builder()
                        .url(refreshUrl)
                        .header("apikey", SupabaseConfig.anonKey)
                        .header("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                        .header("Content-Type", "application/json")
                        .post(payloadJson.toRequestBody(jsonMediaType))
                        .build()
                    resp = refreshHttpClient.newCall(req).execute()
                    val code = resp.code
                    val rawBody = resp.body?.string().orEmpty()
                    resp.close()
                    resp = null

                    if (code in 200..299 && rawBody.isNotBlank()) {
                        val refreshed = runCatching { authSessionAdapter.fromJson(rawBody) }.getOrNull()
                        if (refreshed != null && refreshed.accessToken.isNotBlank()) {
                            runBlocking {
                                preferences.saveSession(
                                    accessToken = refreshed.accessToken,
                                    refreshToken = refreshed.refreshToken.ifBlank { current.refreshToken },
                                    userId = refreshed.user?.id?.takeIf { it.isNotBlank() } ?: current.userId,
                                    email = refreshed.user?.email?.takeIf { it.isNotBlank() } ?: current.email
                                )
                            }
                            return RefreshOutcome.Refreshed(refreshed.accessToken)
                        }
                    }

                    if (JwtTokenUtils.isPermanentRefreshFailure(code, rawBody)) {
                        runBlocking { preferences.clearSession() }
                        return RefreshOutcome.SessionInvalid
                    }

                    if ((code == 429 || code >= 500) && attempt == 0) {
                        Thread.sleep(250L)
                        continue
                    }
                    return RefreshOutcome.TransientFailure
                } catch (_: IOException) {
                    runCatching { resp?.close() }
                    if (attempt == 0) {
                        runCatching { Thread.sleep(250L) }
                        continue
                    }
                    return RefreshOutcome.TransientFailure
                } catch (_: Exception) {
                    runCatching { resp?.close() }
                    return RefreshOutcome.TransientFailure
                }
            }
            return RefreshOutcome.TransientFailure
        }
    }

    suspend fun ensureFreshAccessToken(
        forceRefresh: Boolean = false,
        failedAccessToken: String? = null
    ): String? = withContext(Dispatchers.IO) {
        when (val outcome = ensureFreshTokenBlocking(forceRefresh, failedAccessToken)) {
            is RefreshOutcome.Refreshed -> outcome.accessToken
            is RefreshOutcome.SessionInvalid, is RefreshOutcome.TransientFailure -> null
        }
    }

    private suspend fun tryRefreshAccessToken(failedToken: String? = null): String? =
        ensureFreshAccessToken(forceRefresh = true, failedAccessToken = failedToken)

    private fun resolveContentLength(contentResolver: ContentResolver, uri: Uri): Long {
        if (uri.scheme == "file") {
            val f = uri.path?.let { java.io.File(it) }
            if (f != null && f.exists() && f.length() > 0L) return f.length()
        }
        val afdLen = runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
        if (afdLen > 0L) return afdLen

        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }

    suspend fun uploadToStorage(
        contentResolver: ContentResolver,
        fileUri: Uri,
        bucket: String,
        objectPath: String,
        mimeType: String,
        onProgress: (Float) -> Unit,
        allowAnonFallback: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured in Secrets."))
        }
        val session = preferences.getSessionSnapshot()
        if (!session.isLoggedIn && !allowAnonFallback) {
            return@withContext Result.failure(IllegalStateException("Please sign in to upload media."))
        }
        val freshOrCurrent = if (session.isLoggedIn) {
            ensureFreshAccessToken(forceRefresh = false) ?: session.accessToken
        } else {
            session.accessToken
        }
        var authToken = freshOrCurrent.ifBlank { SupabaseConfig.anonKey }
        var tempCopy: java.io.File? = null

        try {
            val resolvedLength = resolveContentLength(contentResolver, fileUri)

            val uploadSourceUri: Uri
            val totalBytes: Long
            if (resolvedLength > 0L) {
                uploadSourceUri = fileUri
                totalBytes = resolvedLength
            } else {
                // Unknown-length picker URI: spool to temp file so OkHttp sends explicit Content-Length
                val copied = java.io.File.createTempFile("blade_upload_", ".bin")
                tempCopy = copied
                contentResolver.openInputStream(fileUri)?.use { input ->
                    copied.outputStream().use { out -> input.copyTo(out) }
                } ?: return@withContext Result.failure(IOException("Unable to read selected file"))
                if (copied.length() <= 0L) {
                    return@withContext Result.failure(IOException("Selected media file is empty."))
                }
                uploadSourceUri = Uri.fromFile(copied)
                totalBytes = copied.length()
            }

            val mediaType = mimeType.toMediaTypeOrNull()

            fun buildUploadBody() = object : RequestBody() {
                override fun contentType(): MediaType? = mediaType
                override fun contentLength(): Long = totalBytes
                override fun writeTo(sink: BufferedSink) {
                    val input = contentResolver.openInputStream(uploadSourceUri)
                        ?: throw IOException("Unable to read selected file")
                    input.use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        var uploaded = 0L
                        var lastReportedPct = -1
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            uploaded += read
                            if (totalBytes > 0) {
                                val pct = ((uploaded * 100L) / totalBytes).toInt()
                                if (pct != lastReportedPct || uploaded >= totalBytes) {
                                    lastReportedPct = pct
                                    onProgress((uploaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.96f))
                                }
                            }
                        }
                    }
                }
            }

            val uploadUrl = "${SupabaseConfig.url}/storage/v1/object/$bucket/$objectPath"
            fun executeUpload(token: String): okhttp3.Response {
                val request = Request.Builder()
                    .url(uploadUrl)
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", mimeType)
                    .header("x-upsert", "true")
                    .post(buildUploadBody())
                    .build()
                return okHttpClient.newCall(request).execute()
            }

            var response: okhttp3.Response? = null
            var lastNetworkErr: IOException? = null
            for (attempt in 0..1) {
                if (!currentCoroutineContext().isActive) break
                try {
                    val candidate = executeUpload(authToken)
                    if (candidate.code in listOf(502, 503, 504) && attempt == 0) {
                        candidate.close()
                        delay(700L)
                        continue
                    }
                    response = candidate
                    lastNetworkErr = null
                    break
                } catch (io: IOException) {
                    lastNetworkErr = io
                    if (attempt == 0 && currentCoroutineContext().isActive) {
                        delay(700L)
                    }
                }
            }

            if (response == null) {
                return@withContext Result.failure(lastNetworkErr ?: IOException("Network upload interrupted. Please retry."))
            }

            if ((response.code == 401 || response.code == 403) && session.refreshToken.isNotBlank()) {
                val refreshed = tryRefreshAccessToken(authToken)
                if (!refreshed.isNullOrBlank()) {
                    response.close()
                    authToken = refreshed
                    response = executeUpload(authToken)
                }
            }
            if (response.code == 404) {
                response.close()
                // Attempt to create missing public bucket then retry once
                val createBucketReq = Request.Builder()
                    .url("${SupabaseConfig.url}/storage/v1/bucket")
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $authToken")
                    .post("""{"id":"$bucket","name":"$bucket","public":true}""".toRequestBody("application/json".toMediaType()))
                    .build()
                okHttpClient.newCall(createBucketReq).execute().close()
                response = executeUpload(authToken)
            }

            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "HTTP ${response.code}"
                val code = response.code
                response.close()
                val hint = if (code == 403) {
                    "Storage upload blocked (HTTP 403). Run the updated supabase/schema.sql in Supabase SQL Editor. Details: $errBody"
                } else {
                    "Storage upload failed ($code): $errBody"
                }
                return@withContext Result.failure(IllegalStateException(hint))
            }
            response.close()
            onProgress(1f)
            val publicUrl = "${SupabaseConfig.url}/storage/v1/object/public/$bucket/$objectPath"
            Result.success(publicUrl)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { tempCopy?.delete() }
        }
    }

    suspend fun deleteStorageObject(publicUrl: String) = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext
        val prefix = "${SupabaseConfig.url}/storage/v1/object/public/"
        if (!publicUrl.startsWith(prefix)) return@withContext
        val remainder = publicUrl.removePrefix(prefix)
        val slash = remainder.indexOf('/')
        if (slash <= 0 || slash >= remainder.lastIndex) return@withContext
        val bucket = remainder.substring(0, slash)
        val path = remainder.substring(slash + 1)
        val session = preferences.getSessionSnapshot()
        val token = session.accessToken.ifBlank { SupabaseConfig.anonKey }
        runCatching {
            val req = Request.Builder()
                .url("${SupabaseConfig.url}/storage/v1/object/$bucket/$path")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            okHttpClient.newCall(req).execute().close()
        }
    }
}
