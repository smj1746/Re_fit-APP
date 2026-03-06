package com.example.myapplication

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * SquatDetector 상태를 ExerciseCounter 상태로 매핑
 */
private fun mapSquatStateToCounterState(squatState: SquatDetector.Companion.SquatState): ExerciseCounter.Companion.State {
    return when (squatState) {
        SquatDetector.Companion.SquatState.STANDING -> ExerciseCounter.Companion.State.IDLE
        SquatDetector.Companion.SquatState.DESCENDING -> ExerciseCounter.Companion.State.DOWN_PHASE
        SquatDetector.Companion.SquatState.BOTTOM -> ExerciseCounter.Companion.State.DOWN_PHASE
        SquatDetector.Companion.SquatState.ASCENDING -> ExerciseCounter.Companion.State.UP_PHASE
    }
}

/**
 * 실시간 카메라 프리뷰 + ML Kit Pose Detection 화면
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    poseClassifier: PoseClassifier,
    exerciseCounter: ExerciseCounter,
    onExitWorkout: (Int, Long) -> Unit, // 나가기 시 콜백 (카운트, 경과시간)
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            cameraPermissionState.status.isGranted -> {
                CameraPreviewWithPoseDetection(
                    poseClassifier = poseClassifier,
                    exerciseCounter = exerciseCounter,
                    onExitWorkout = onExitWorkout
                )
            }
            else -> {
                PermissionDeniedScreen(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewWithPoseDetection(
    poseClassifier: PoseClassifier,
    exerciseCounter: ExerciseCounter,
    onExitWorkout: (Int, Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var detectedPose by remember { mutableStateOf<Pose?>(null) }
    var imageWidth by remember { mutableStateOf(1f) }
    var imageHeight by remember { mutableStateOf(1f) }
    var counterResult by remember {
        mutableStateOf(
            ExerciseCounter.CounterResult(
                count = 0,
                state = ExerciseCounter.Companion.State.IDLE,
                isGoodForm = true,
                feedback = ""
            )
        )
    }

    // 새로운 키포인트 기반 스쿼트 감지기
    val squatDetector = remember { SquatDetector() }
    var squatResult by remember {
        mutableStateOf<SquatDetector.SquatResult?>(null)
    }

    // 카메라 상태
    var cameraInitialized by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // 타이머 초기화 및 시작
    val workoutTimer = remember { WorkoutTimer() }
    val formattedTime by workoutTimer.formattedTime.collectAsState()

    LaunchedEffect(Unit) {
        workoutTimer.start()
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val poseDetector = remember { createPoseDetector() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            poseDetector.close()
            workoutTimer.cleanup()
            squatDetector.reset()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 카메라 프리뷰
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    // PreviewView 설정
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER

                    Log.d("CameraScreen", "PreviewView 생성 완료")

                    setupCamera(
                        context = ctx,
                        lifecycleOwner = lifecycleOwner,
                        previewView = this,
                        poseDetector = poseDetector,
                        cameraExecutor = cameraExecutor,
                        onCameraInitialized = {
                            cameraInitialized = true
                            cameraError = null
                            Log.d("CameraScreen", "카메라 초기화 성공")
                        },
                        onCameraError = { error ->
                            cameraError = error
                            Log.e("CameraScreen", "카메라 오류: $error")
                        },
                        onPoseDetected = { pose, width, height ->
                            detectedPose = pose
                            imageWidth = width
                            imageHeight = height

                            // 키포인트 기반 스쿼트 감지 (실시간)
                            val currentExerciseType = exerciseCounter.getExerciseType()
                            if (currentExerciseType == ExerciseCounter.Companion.ExerciseType.SQUAT) {
                                // 스쿼트일 때는 키포인트 기반 감지 사용
                                val detectedSquatResult = squatDetector.detectSquat(pose)
                                squatResult = detectedSquatResult

                                // ExerciseCounter 결과를 SquatDetector 결과로 동기화
                                counterResult = ExerciseCounter.CounterResult(
                                    count = detectedSquatResult.count,
                                    state = mapSquatStateToCounterState(detectedSquatResult.state),
                                    isGoodForm = detectedSquatResult.isGoodForm,
                                    feedback = detectedSquatResult.feedback
                                )
                            } else {
                                // 푸시업, 플랭크는 기존 TFLite 모델 사용
                                val keypoints = PoseGraphic.extractKeypoints(pose)
                                val classificationResult = poseClassifier.addKeypoints(keypoints)

                                if (classificationResult != null) {
                                    val poseInfo = poseClassifier.getCurrentPose()
                                    if (poseInfo != null) {
                                        counterResult = exerciseCounter.processPose(
                                            poseInfo.first,
                                            poseInfo.second
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 포즈 오버레이 (스켈레톤 시각화)
        PoseOverlay(
            pose = detectedPose,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            modifier = Modifier.fillMaxSize()
        )

        // UI 오버레이 (카운터, 피드백)
        ExerciseInfoOverlay(
            counterResult = counterResult,
            squatResult = squatResult,
            formattedTime = formattedTime,
            exerciseType = exerciseCounter.getExerciseType(),
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 나가기 버튼 (우측 상단)
        ExitButton(
            onClick = {
                workoutTimer.stop()
                onExitWorkout(counterResult.count, workoutTimer.getElapsedSeconds())
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        // 카메라 초기화 로딩 표시
        if (!cameraInitialized && cameraError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = "카메라 초기화 중...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        }

        // 카메라 오류 표시
        if (cameraError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        text = "카메라 오류",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = cameraError ?: "알 수 없는 오류",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Button(
                        onClick = {
                            // 카메라 재초기화 시도
                            cameraInitialized = false
                            cameraError = null
                        }
                    ) {
                        Text("다시 시도")
                    }
                }
            }
        }
    }
}

/**
 * CameraX 설정 및 Pose Detection 연동
 */
private fun setupCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    poseDetector: PoseDetector,
    cameraExecutor: ExecutorService,
    onCameraInitialized: () -> Unit,
    onCameraError: (String) -> Unit,
    onPoseDetected: (Pose, Float, Float) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()

            Log.d("CameraScreen", "CameraProvider 획득 성공")

            // Preview 설정 (해상도 명시)
            val preview = Preview.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            Log.d("CameraScreen", "Preview 설정 완료 (640x480)")

            // ImageAnalysis 설정 (Pose Detection)
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy, poseDetector, onPoseDetected)
                    }
                }

            Log.d("CameraScreen", "ImageAnalysis 설정 완료")

            // 카메라 선택 (전면 카메라 우선, 없으면 후면 카메라)
            val cameraSelector = try {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } catch (e: Exception) {
                Log.w("CameraScreen", "전면 카메라 없음, 후면 카메라 사용", e)
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            Log.d("CameraScreen", "카메라 선택: ${if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "전면" else "후면"}")

            // 기존 바인딩 해제
            cameraProvider.unbindAll()

            // 카메라 바인딩
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            Log.d("CameraScreen", "카메라 바인딩 성공")
            Log.d("CameraScreen", "카메라 정보: ${camera.cameraInfo}")

            // 초기화 완료 콜백
            onCameraInitialized()

        } catch (e: Exception) {
            val errorMessage = "카메라 초기화 실패: ${e.message}"
            Log.e("CameraScreen", errorMessage, e)
            onCameraError(errorMessage)
        }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * ImageProxy를 ML Kit InputImage로 변환 후 Pose Detection 수행
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    poseDetector: PoseDetector,
    onPoseDetected: (Pose, Float, Float) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        poseDetector.process(image)
            .addOnSuccessListener { pose ->
                val validKeypoints = PoseGraphic.countValidKeypoints(pose)

                if (PoseGraphic.isPoseValid(pose)) {
                    onPoseDetected(
                        pose,
                        image.width.toFloat(),
                        image.height.toFloat()
                    )
                    Log.d("CameraScreen", "포즈 감지 성공 (유효 키포인트: $validKeypoints / 17)")
                } else {
                    Log.d("CameraScreen", "포즈 유효성 검사 실패 (유효 키포인트: $validKeypoints / 17)")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CameraScreen", "포즈 감지 실패: ${e.message}", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        Log.w("CameraScreen", "MediaImage가 null입니다")
        imageProxy.close()
    }
}

/**
 * ML Kit Pose Detector 생성
 */
private fun createPoseDetector(): PoseDetector {
    val options = AccuratePoseDetectorOptions.Builder()
        .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
        .build()
    return PoseDetection.getClient(options)
}

/**
 * 운동 정보 오버레이 (카운터, 상태, 피드백, 타이머)
 */
@Composable
private fun ExerciseInfoOverlay(
    counterResult: ExerciseCounter.CounterResult,
    squatResult: SquatDetector.SquatResult?,
    formattedTime: String,
    exerciseType: ExerciseCounter.Companion.ExerciseType,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 타이머
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                text = "⏱️",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF4CAF50)
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.3f), thickness = 2.dp)

        Spacer(modifier = Modifier.height(16.dp))

        // 반복 카운트 (대형 표시)
        Text(
            text = "${counterResult.count}",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White
        )

        Text(
            text = "회",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 현재 상태
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when (counterResult.state) {
                    ExerciseCounter.Companion.State.DOWN_PHASE -> Color(0xFF2196F3)
                    ExerciseCounter.Companion.State.UP_PHASE -> Color(0xFF4CAF50)
                    else -> Color(0xFF9E9E9E)
                }.copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when (exerciseType) {
                    ExerciseCounter.Companion.ExerciseType.SQUAT ->
                        when (counterResult.state) {
                            ExerciseCounter.Companion.State.IDLE -> "준비"
                            ExerciseCounter.Companion.State.DOWN_PHASE -> "하강 중 ⬇️"
                            ExerciseCounter.Companion.State.UP_PHASE -> "상승 중 ⬆️"
                            else -> "대기"
                        }
                    else -> counterResult.state.toString()
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 자세 피드백
        Text(
            text = counterResult.feedback,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = if (counterResult.isGoodForm) Color(0xFF4CAF50) else Color(0xFFFF5722),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // 스쿼트 세부 정보 (선택적)
        if (exerciseType == ExerciseCounter.Companion.ExerciseType.SQUAT && squatResult != null) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "왼쪽 무릎",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${squatResult.leftKneeAngle.toInt()}°",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "오른쪽 무릎",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${squatResult.rightKneeAngle.toInt()}°",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 나가기 버튼 (우측 상단)
 */
@Composable
private fun ExitButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .background(
                color = Color(0xFFFF5722),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "나가기",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * 권한 거부 화면
 */
@Composable
private fun PermissionDeniedScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "카메라 권한이 필요합니다",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Button(onClick = onRequestPermission) {
                Text("권한 요청")
            }
        }
    }
}
