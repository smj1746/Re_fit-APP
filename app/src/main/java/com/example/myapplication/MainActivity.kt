package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DetectionWebViewScreen() }   // Compose 화면으로 교체
    }
}

@Composable
fun DetectionWebViewScreen() {
    val context = LocalContext.current

    // WebView를 remember로 잡아두기 (evaluateJavascript 호출 위해 참조가 필요)
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // 시스템 포토 피커(권한 없이 동작)
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            // 선택된 이미지로 객체감지 실행 후, 결과를 WebView로 전달
            runObjectDetection(context, webViewRef.value, uri)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()

                // JS 브리지: HTML에서 Android.pickImage(), Android.saveSnapshot() 호출 가능
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun pickImage() {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    @JavascriptInterface
                    fun saveSnapshot(@Suppress("unused") base64Png: String) {
                        // 과제용이면 생략 가능 (원하면 MediaStore 저장 코드 나중에 붙여도 됨)
                    }
                }, "Android")

                // 우리가 assets에 넣은 데모 HTML 로드
                loadUrl("file:///android_asset/web/index.html")

                // 참조 저장
                webViewRef.value = this
            }
        },
        update = { /* 필요 시 상태 업데이트 */ }
    )
}

/** 선택한 이미지에 대해 ML Kit 객체 감지 실행 후, HTML의 window.onObjectDetection(...) 호출 */
private fun runObjectDetection(context: android.content.Context, webView: WebView?, uri: Uri) {
    if (webView == null) return

    // 1) 비트맵과 크기 준비 (WebView에 data URL로 전달하기 위해)
    val bmp = context.contentResolver.openInputStream(uri).use { ins ->
        BitmapFactory.decodeStream(ins)
    } ?: return

    val image = InputImage.fromFilePath(context, uri)

    // 2) ML Kit 객체감지(정적 이미지)
    val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
        .enableMultipleObjects()
        .enableClassification() // 라벨/신뢰도
        .build()

    val detector = ObjectDetection.getClient(options)
    detector.process(image)
        .addOnSuccessListener { objects ->
            val dataUrl = bitmapToDataUrl(bmp)
            // 결과 JSON 만들기
            val objectsJson = buildString {
                append("[")
                objects.forEachIndexed { idx, o ->
                    val bb = o.boundingBox
                    val label = o.labels.firstOrNull()?.text ?: "Object"
                    val conf = o.labels.firstOrNull()?.confidence
                    if (idx > 0) append(",")
                    append("{\"label\":\"")
                    append(escapeJson(label))
                    append("\",\"confidence\":")
                    append(conf?.toString() ?: "null")
                    append(",\"box\":{\"left\":${bb.left},\"top\":${bb.top},\"right\":${bb.right},\"bottom\":${bb.bottom}}}")
                }
                append("]")
            }
            // 3) HTML 콜백 호출 → WebView가 박스 오버레이 그림
            val js = "window.onObjectDetection('${escapeJs(dataUrl)}', $objectsJson, ${bmp.width}, ${bmp.height});"
            webView.evaluateJavascript(js, null)
        }
        .addOnFailureListener {
            webView.evaluateJavascript(
                "console.error('Detection failed: ${it.message}');", null
            )
        }
}

private fun bitmapToDataUrl(bmp: Bitmap): String {
    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    return "data:image/png;base64,$b64"
}
private fun escapeJs(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")
private fun escapeJson(s: String) = s.replace("\"", "\\\"")
