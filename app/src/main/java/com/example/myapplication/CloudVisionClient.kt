package com.example.myapplication

import android.graphics.Bitmap
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class CloudVisionClient(
    private val apiKey: String,
    enableLogging: Boolean = false
) {
    private val gson = Gson()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (enableLogging) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
            connectTimeout(20, TimeUnit.SECONDS)
            readTimeout(40, TimeUnit.SECONDS)
            writeTimeout(40, TimeUnit.SECONDS)
        }.build()

    suspend fun annotate(
        bitmap: Bitmap,
        requestTypes: List<Feature.Type> = listOf(
            Feature.Type.LABEL_DETECTION,
            Feature.Type.OBJECT_LOCALIZATION,
            Feature.Type.TEXT_DETECTION
        ),
        maxResults: Int = 10
    ): AnnotateResponse {
        val base64 = bitmap.toBase64Png()
        val url = "https://vision.googleapis.com/v1/images:annotate?key=$apiKey"

        val features = requestTypes.map { Feature(it.value, maxResults) }
        val req = AnnotateRequest(RequestImage(base64), features)
        val payload = RequestEnvelope(listOf(req))

        val json = gson.toJson(payload)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpReq = Request.Builder().url(url).post(body).build()

        client.newCall(httpReq).execute().use { resp ->
            if (!resp.isSuccessful) error("Cloud Vision HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty()
            return gson.fromJson(text, AnnotateResponse::class.java)
        }
    }
}

/** ---- Data Models ---- */
data class RequestEnvelope(@SerializedName("requests") val requests: List<AnnotateRequest>)
data class AnnotateRequest(
    @SerializedName("image") val image: RequestImage,
    @SerializedName("features") val features: List<Feature>
)
data class RequestImage(@SerializedName("content") val content: String)
data class Feature(
    @SerializedName("type") val type: String,
    @SerializedName("maxResults") val maxResults: Int = 10
) {
    enum class Type(val value: String) {
        LABEL_DETECTION("LABEL_DETECTION"),
        TEXT_DETECTION("TEXT_DETECTION"),
        OBJECT_LOCALIZATION("OBJECT_LOCALIZATION")
    }
}
data class AnnotateResponse(@SerializedName("responses") val responses: List<AnnotateResult> = emptyList())
data class AnnotateResult(
    @SerializedName("labelAnnotations") val labels: List<EntityAnnotation>? = null,
    @SerializedName("textAnnotations") val texts: List<EntityAnnotation>? = null,
    @SerializedName("localizedObjectAnnotations") val objects: List<LocalizedObject>? = null
)
data class EntityAnnotation(
    @SerializedName("description") val description: String? = null,
    @SerializedName("score") val score: Float? = null
)
data class LocalizedObject(
    @SerializedName("name") val name: String? = null,
    @SerializedName("score") val score: Float? = null,
    @SerializedName("boundingPoly") val boundingPoly: BoundingPoly? = null
)
data class BoundingPoly(@SerializedName("normalizedVertices") val vertices: List<Vertex> = emptyList())
data class Vertex(@SerializedName("x") val x: Float? = null, @SerializedName("y") val y: Float? = null)

/** ---- Utils ---- */
private fun Bitmap.toBase64Png(): String {
    val baos = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 100, baos)
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}
