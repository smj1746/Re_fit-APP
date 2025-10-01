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
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    lateinit var poseClassifier: PoseClassifier
    lateinit var exerciseCounter: ExerciseCounter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 포즈 분류기 및 카운터 초기화
        poseClassifier = PoseClassifier(this)
        exerciseCounter = ExerciseCounter()
        
        setContent { DetectionWebViewScreen() }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        poseClassifier.close()
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
            // 선택된 이미지로 포즈 감지 실행 후, 결과를 WebView로 전달
            runPoseDetection(context, webViewRef.value, uri)
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

                // JS 브리지: HTML에서 Android 함수들 호출 가능
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
                    @JavascriptInterface
                    fun setExerciseType(exerciseType: String) {
                        // ExerciseType enum을 직접 사용하지 않고 문자열로 처리
                        val exercise = when (exerciseType.lowercase()) {
                            "squat" -> "SQUAT"
                            "pushup" -> "PUSHUP"
                            "plank" -> "PLANK"
                            else -> "SQUAT"
                        }
                        // exerciseCounter의 setExerciseType 메서드가 문자열을 받도록 수정 필요
                        // (context as MainActivity).exerciseCounter.setExerciseType(exercise)
                    }
                    @JavascriptInterface
                    fun resetCounter() {
                        (context as MainActivity).exerciseCounter.reset()
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

/** 선택한 이미지에 대해 ML Kit 포즈 감지 실행 후, HTML의 window.onPoseDetection(...) 호출 */
private fun runPoseDetection(context: android.content.Context, webView: WebView?, uri: Uri) {
    if (webView == null) return

    // 1) 비트맵과 크기 준비 (WebView에 data URL로 전달하기 위해)
    val bmp = context.contentResolver.openInputStream(uri).use { ins ->
        BitmapFactory.decodeStream(ins)
    } ?: return

    val image = InputImage.fromFilePath(context, uri)

    // 2) ML Kit 포즈 감지
    val options = AccuratePoseDetectorOptions.Builder()
        .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
        .build()

    val detector = PoseDetection.getClient(options)
    detector.process(image)
        .addOnSuccessListener { pose ->
            val dataUrl = bitmapToDataUrl(bmp)
            
            // 3) 포즈 랜드마크를 MoveNet 형식으로 변환
            val keypoints = convertPoseToKeypoints(pose)
            
            // 4) 포즈 분류 실행
            val mainActivity = context as MainActivity
            val classificationResult = mainActivity.poseClassifier.addKeypoints(keypoints)
            
            // 5) 운동 카운팅
            val counterResult = if (classificationResult != null) {
                val poseInfo = mainActivity.poseClassifier.getCurrentPose()
                if (poseInfo != null) {
                    mainActivity.exerciseCounter.processPose(poseInfo.first, poseInfo.second)
                } else {
                    // 간단한 CounterResult 생성 (State enum 사용하지 않음)
                    createSimpleCounterResult(0, "IDLE", true, "")
                }
            } else {
                createSimpleCounterResult(0, "IDLE", true, "")
            }
            
            // 6) 결과 JSON 생성
            val poseJson = buildPoseJson(pose, keypoints, classificationResult, counterResult)
            
            // 7) HTML 콜백 호출
            val js = "window.onPoseDetection('${escapeJs(dataUrl)}', $poseJson, ${bmp.width}, ${bmp.height});"
            webView.evaluateJavascript(js, null)
        }
        .addOnFailureListener {
            webView.evaluateJavascript(
                "console.error('Pose detection failed: ${it.message}');", null
            )
        }
}

/** ML Kit 포즈를 MoveNet 형식 키포인트로 변환 */
private fun convertPoseToKeypoints(pose: com.google.mlkit.vision.pose.Pose): Array<FloatArray> {
    val keypoints = Array(17) { FloatArray(3) }
    
    // COCO 17 키포인트 순서에 맞게 변환
    val landmarkMap = mapOf(
        0 to PoseLandmark.NOSE,
        1 to PoseLandmark.LEFT_EYE,
        2 to PoseLandmark.RIGHT_EYE,
        3 to PoseLandmark.LEFT_EAR,
        4 to PoseLandmark.RIGHT_EAR,
        5 to PoseLandmark.LEFT_SHOULDER,
        6 to PoseLandmark.RIGHT_SHOULDER,
        7 to PoseLandmark.LEFT_ELBOW,
        8 to PoseLandmark.RIGHT_ELBOW,
        9 to PoseLandmark.LEFT_WRIST,
        10 to PoseLandmark.RIGHT_WRIST,
        11 to PoseLandmark.LEFT_HIP,
        12 to PoseLandmark.RIGHT_HIP,
        13 to PoseLandmark.LEFT_KNEE,
        14 to PoseLandmark.RIGHT_KNEE,
        15 to PoseLandmark.LEFT_ANKLE,
        16 to PoseLandmark.RIGHT_ANKLE
    )
    
    landmarkMap.forEach { (index, landmark) ->
        val landmarkPoint = pose.getPoseLandmark(landmark)
        if (landmarkPoint != null) {
            keypoints[index][0] = landmarkPoint.position.x
            keypoints[index][1] = landmarkPoint.position.y
            keypoints[index][2] = landmarkPoint.inFrameLikelihood
        } else {
            keypoints[index][0] = 0f
            keypoints[index][1] = 0f
            keypoints[index][2] = 0f
        }
    }
    
    return keypoints
}

/** 포즈 감지 결과를 JSON으로 변환 */
private fun buildPoseJson(
    pose: com.google.mlkit.vision.pose.Pose,
    keypoints: Array<FloatArray>,
    classificationResult: FloatArray?,
    counterResult: Any
): String {
    return buildString {
        append("{")
        append("\"keypoints\":[")
        keypoints.forEachIndexed { idx, kp ->
            if (idx > 0) append(",")
            append("{\"x\":${kp[0]},\"y\":${kp[1]},\"confidence\":${kp[2]}}")
        }
        append("],")
        
        if (classificationResult != null) {
            val classIndex = classificationResult[0].toInt()
            val confidence = classificationResult[1]
            append("\"classification\":{\"class\":\"${PoseClassifier.LABELS[classIndex]}\",\"confidence\":$confidence},")
        } else {
            append("\"classification\":null,")
        }
        
        append("\"counter\":{")
        append("\"count\":${(counterResult as Any).javaClass.getDeclaredField("count").get(counterResult)},")
        append("\"state\":\"${(counterResult as Any).javaClass.getDeclaredField("state").get(counterResult)}\",")
        append("\"isGoodForm\":${(counterResult as Any).javaClass.getDeclaredField("isGoodForm").get(counterResult)},")
        append("\"feedback\":\"${escapeJson((counterResult as Any).javaClass.getDeclaredField("feedback").get(counterResult) as String)}\"")
        append("}")
        append("}")
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

// 간단한 CounterResult 생성 함수
private fun createSimpleCounterResult(count: Int, state: String, isGoodForm: Boolean, feedback: String): Any {
    return object {
        val count = count
        val state = state
        val isGoodForm = isGoodForm
        val feedback = feedback
    }
}
