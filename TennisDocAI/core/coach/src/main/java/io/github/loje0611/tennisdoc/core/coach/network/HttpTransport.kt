package io.github.loje0611.tennisdoc.core.coach.network

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class HttpResponse(
    val statusCode: Int,
    val body: String
)

interface HttpTransport {
    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        bodyJson: String,
        timeoutMs: Long = 10_000L
    ): HttpResponse
}

class DefaultHttpTransport : HttpTransport {
    override suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        bodyJson: String,
        timeoutMs: Long
    ): HttpResponse {
        val conn = java.net.URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs.toInt()
            conn.readTimeout = timeoutMs.toInt()
            conn.doOutput = true

            for ((k, v) in headers) {
                conn.setRequestProperty(k, v)
            }
            conn.setRequestProperty("Content-Type", "application/json")

            val output = bodyJson.toByteArray(StandardCharsets.UTF_8)
            conn.outputStream.use { os ->
                os.write(output)
                os.flush()
            }

            val statusCode = conn.responseCode
            val stream: InputStream = if (statusCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream ?: conn.inputStream
            }

            val body = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            return HttpResponse(statusCode, body)
        } finally {
            conn.disconnect()
        }
    }
}
