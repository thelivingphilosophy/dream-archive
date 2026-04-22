package com.conndreams.recorder

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Thin Drive v3 REST wrapper.
 *
 * Auth model: GoogleSignIn with the narrow `drive.file` scope — only files created by
 * this app are visible, so the user cannot accidentally expose their whole Drive.
 * Access tokens come from GoogleAuthUtil, which handles silent refresh.
 */
class DriveClient(private val context: Context) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    fun buildSignInClient(): GoogleSignInClient {
        val opts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, opts)
    }

    fun currentAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    /**
     * Blocking: fetch a bearer access token for Drive API calls.
     * Throws [UserRecoverableAuthException] if the user must re-consent — caller should surface that.
     */
    @Throws(Exception::class)
    suspend fun getAccessToken(account: Account): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(context, account, "oauth2:$SCOPE_DRIVE_FILE")
    }

    suspend fun ensureFolder(accessToken: String, folderName: String): String = withContext(Dispatchers.IO) {
        findFolder(accessToken, folderName) ?: createFolder(accessToken, folderName)
    }

    private fun findFolder(accessToken: String, folderName: String): String? {
        val escapedName = folderName.replace("'", "\\'")
        val q = "mimeType='application/vnd.google-apps.folder' and name='$escapedName' and 'root' in parents and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(q, "UTF-8")}&fields=files(id,name)&spaces=drive"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()
        http.newCall(req).execute().use { resp ->
            // No throwIfNotFound here: `files.list` returns 200 + empty array when nothing matches.
            // A 404 on this endpoint is an auth/scope glitch, not a missing-parent signal.
            if (!resp.isSuccessful) throw RuntimeException("find folder failed: ${resp.code} ${resp.message}")
            val body = resp.body?.string().orEmpty()
            val files = JsonParser.parseString(body).asJsonObject.getAsJsonArray("files")
            return if (files != null && files.size() > 0) files[0].asJsonObject["id"].asString else null
        }
    }

    private fun createFolder(accessToken: String, folderName: String): String {
        val meta = """{"name":${quote(folderName)},"mimeType":"application/vnd.google-apps.folder","parents":["root"]}"""
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(meta.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        http.newCall(req).execute().use { resp ->
            // Same reasoning as findFolder — 404 on create is never "parent missing".
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("create folder failed: ${resp.code} $body")
            return JsonParser.parseString(body).asJsonObject["id"].asString
        }
    }

    /**
     * Resumable upload of [file] into [folderId]. Returns the created file's ID.
     * Filenames are pre-uniqued by the recorder (ms-precision timestamp), so we don't
     * retry on name collision — and Drive allows duplicate names regardless.
     */
    suspend fun uploadFile(accessToken: String, folderId: String, file: File): String = withContext(Dispatchers.IO) {
        val metadata = """{"name":${quote(file.name)},"parents":["$folderId"]}"""
        val initReq = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
            .header("Authorization", "Bearer $accessToken")
            .header("X-Upload-Content-Type", "audio/mp4")
            .header("X-Upload-Content-Length", file.length().toString())
            .post(metadata.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        val uploadUrl: String = http.newCall(initReq).execute().use { resp ->
            throwIfNotFound(resp)
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw RuntimeException("resumable init failed: ${resp.code} $body")
            }
            resp.header("Location") ?: throw RuntimeException("no Location header on resumable init")
        }

        val putReq = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", "Bearer $accessToken")
            .put(file.asRequestBody())
            .build()
        http.newCall(putReq).execute().use { resp ->
            // PUT 404 means the resumable session expired, not "parent folder gone".
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("upload PUT failed: ${resp.code} $body")
            return@withContext JsonParser.parseString(body).asJsonObject["id"].asString
        }
    }

    /** Thrown when Drive reports 404 — the resource (usually the target folder) is gone. */
    class NotFoundException(message: String) : RuntimeException(message)

    private fun throwIfNotFound(resp: Response) {
        if (resp.code == 404) throw NotFoundException("drive 404: ${resp.request.url}")
    }

    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun File.asRequestBody(): RequestBody = object : RequestBody() {
        override fun contentType() = "audio/mp4".toMediaTypeOrNull()
        override fun contentLength() = this@asRequestBody.length()
        override fun writeTo(sink: okio.BufferedSink) {
            this@asRequestBody.inputStream().use { src ->
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = src.read(buf)
                    if (n <= 0) break
                    sink.write(buf, 0, n)
                }
            }
        }
    }

    companion object {
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    }
}
