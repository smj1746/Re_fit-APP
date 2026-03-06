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
 * 운동 타입 정의
 */
enum class ExerciseType {
    SQUAT,   // 스쿼트
    PUSHUP,  // 푸시업
    PLANK    // 플랭크
}

/**
 * SquatDetector 상태를 ExerciseCounter 상태로 매핑
 */
private fun mapSquatStateToCounterState(squatState: SquatState): ExerciseCounter.Companion.State {
    return when (squatState) {
        SquatState.STANDING -> ExerciseCounter.Companion.State.IDLE
        SquatState.DESCENDING -> ExerciseCounter.Companion.State.DOWN_PHASE
        SquatState.BOTTOM -> ExerciseCounter.Companion.State.DOWN_PHASE
        SquatState.ASCENDING -> ExerciseCounter.Companion.State.UP_PHASE
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

    // 운동 타입 선택
    var selectedExerciseType by remember { mutableStateOf(ExerciseType.SQUAT) }

    // 운동 감지기들
    val squatDetector = remember { SquatDetector() }
    val pushUpDetector = remember { PushUpDetector() }
    val plankDetector = remember { PlankDetector() }

    // 감지 결과
    var squatResult by remember { mutableStateOf<SquatDetector.SquatResult?>(null) }
    var pushUpResult by remember { mutableStateOf<PushUpDetector.PushUpResult?>(null) }
    var plankResult by remember { mutableStateOf<PlankDetector.PlankResult?>(null) }

    // 카메라 상태
    var cameraInitialized by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // 타이머 초기화 및 시작
    val workoutTimer = remember { WorkoutTimer() }
    val formattedTime by workoutTimer.formattedTime.collectAsState()
    var isPaused by remember { mutableStateOf(false) }

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
            pushUpDetector.reset()
            plankDetector.reset()
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

                            // 선택된 운동 타입에 따라 다른 감지기 사용
                            when (selectedExerciseType) {
                                ExerciseType.SQUAT -> {
                                    // 스쿼트: 키포인트 기반 감지
                                    val detectedSquatResult = squatDetector.detectSquat(pose)
                                    squatResult = detectedSquatResult

                                    counterResult = ExerciseCounter.CounterResult(
                                        count = detectedSquatResult.count,
                                        state = mapSquatStateToCounterState(detectedSquatResult.state),
                                        isGoodForm = detectedSquatResult.isGoodForm,
                                        feedback = detectedSquatResult.feedback
                                    )
                                }
                                ExerciseType.PUSHUP -> {
                                    // 푸시업: 키포인트 기반 감지
                                    val detectedPushUpResult = pushUpDetector.detectPushUp(pose)
                                    pushUpResult = detectedPushUpResult

                                    counterResult = ExerciseCounter.CounterResult(
                                        count = detectedPushUpResult.count,
                                        state = when (detectedPushUpResult.state) {
                                            PushUpState.UP -> ExerciseCounter.Companion.State.IDLE
                                            PushUpState.DESCENDING -> ExerciseCounter.Companion.State.DOWN_PHASE
                                            PushUpState.DOWN -> ExerciseCounter.Companion.State.DOWN_PHASE
                                            PushUpState.ASCENDING -> ExerciseCounter.Companion.State.UP_PHASE
                                        },
                                        isGoodForm = detectedPushUpResult.isGoodForm,
                                        feedback = detectedPushUpResult.feedback
                                    )
                                }
                                ExerciseType.PLANK -> {
                                    // 플랭크: 키포인트 기반 감지
                                    val detectedPlankResult = plankDetector.detectPlank(pose)
                                    plankResult = detectedPlankResult

                                    counterResult = ExerciseCounter.CounterResult(
                                        count = 0,  // 플랭크는 카운트가 아닌 시간 측정
                                        state = when (detectedPlankResult.state) {
                                            PlankState.IN_POSITION -> ExerciseCounter.Companion.State.DOWN_PHASE
                                            PlankState.NOT_IN_POSITION,
                                            PlankState.HIPS_TOO_HIGH,
                                            PlankState.HIPS_TOO_LOW -> ExerciseCounter.Companion.State.IDLE
                                        },
                                        isGoodForm = detectedPlankResult.isGoodForm,
                                        feedback = detectedPlankResult.feedback
                                    )
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

        // 운동 제어 버튼 (하단)
        WorkoutControlButtons(
            isPaused = isPaused,
            onPauseResume = {
                if (isPaused) {
                    workoutTimer.resume()
                    isPaused = false
                } else {
                    workoutTimer.pause()
                    isPaused = true
                }
            },
            onComplete = {
                // 운동 완료
                workoutTimer.stop()
                val finalCount = when (selectedExerciseType) {
                    ExerciseType.SQUAT -> squatResult?.count ?: 0
                    ExerciseType.PUSHUP -> pushUpResult?.count ?: 0
                    ExerciseType.PLANK -> 0  // 플랭크는 카운트가 아닌 시간
                }
                onExitWorkout(finalCount, workoutTimer.getElapsedSeconds())
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        // UI 오버레이 (카운터, 피드백) - 운동 타입에 따라 다르게 표시
        when (selectedExerciseType) {
            ExerciseType.SQUAT -> {
                ExerciseInfoOverlay(
                    counterResult = counterResult,
                    squatResult = squatResult,
                    formattedTime = formattedTime,
                    exerciseType = selectedExerciseType,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(end = 72.dp)  // 나가기 버튼 공간 확보
                )
            }
            ExerciseType.PUSHUP -> {
                PushUpInfoOverlay(
                    pushUpResult = pushUpResult,
                    formattedTime = formattedTime,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(end = 72.dp)  // 나가기 버튼 공간 확보
                )
            }
            ExerciseType.PLANK -> {
                PlankInfoOverlay(
                    plankResult = plankResult,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(end = 72.dp)  // 나가기 버튼 공간 확보
                )
            }
        }

        // 나가기 버튼 (우측 상단)
        ExitButton(
            onClick = {
                workoutTimer.stop()
                val finalCount = when (selectedExerciseType) {
                    ExerciseType.SQUAT -> squatResult?.count ?: 0
                    ExerciseType.PUSHUP -> pushUpResult?.count ?: 0
                    ExerciseType.PLANK -> 0  // 플랭크는 카운트가 아닌 시간
                }
                onExitWorkout(finalCount, workoutTimer.getElapsedSeconds())
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
 * 스쿼트 운동 정보 오버레이
 */
@Composable
private fun ExerciseInfoOverlay(
    counterResult: ExerciseCounter.CounterResult,
    squatResult: SquatDetector.SquatResult?,
    formattedTime: String,
    exerciseType: ExerciseType,
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
                text = when (counterResult.state) {
                    ExerciseCounter.Companion.State.IDLE -> "준비"
                    ExerciseCounter.Companion.State.DOWN_PHASE -> "하강 중 ⬇️"
                    ExerciseCounter.Companion.State.UP_PHASE -> "상승 중 ⬆️"
                    else -> "대기"
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
        if (exerciseType == ExerciseType.SQUAT && squatResult != null) {
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

/**
 * 푸시업 정보 오버레이
 */
@Composable
private fun PushUpInfoOverlay(
    pushUpResult: PushUpDetector.PushUpResult?,
    formattedTime: String,
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
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(text = "⏱️", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF4CAF50)
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.3f), thickness = 2.dp)
        Spacer(modifier = Modifier.height(16.dp))

        // 카운트
        Text(
            text = "${pushUpResult?.count ?: 0}",
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

        // 상태
        if (pushUpResult != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (pushUpResult.state) {
                        PushUpState.DOWN -> Color(0xFF2196F3)
                        PushUpState.UP -> Color(0xFF4CAF50)
                        PushUpState.DESCENDING, PushUpState.ASCENDING -> Color(0xFF9E9E9E)
                    }.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when (pushUpResult.state) {
                        PushUpState.UP -> "준비 ✅"
                        PushUpState.DESCENDING -> "하강 중 ⬇️"
                        PushUpState.DOWN -> "최하단 💪"
                        PushUpState.ASCENDING -> "상승 중 ⬆️"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 피드백
            Text(
                text = pushUpResult.feedback,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (pushUpResult.isGoodForm) Color(0xFF4CAF50) else Color(0xFFFF5722),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 팔 각도
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "왼팔",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${pushUpResult.leftElbowAngle.toInt()}°",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "오른팔",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${pushUpResult.rightElbowAngle.toInt()}°",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        } else {
            Text(
                text = "푸시업 자세를 취하세요",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 플랭크 정보 오버레이
 */
@Composable
private fun PlankInfoOverlay(
    plankResult: PlankDetector.PlankResult?,
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
        // 타이머 (대형 표시)
        Text(
            text = formatPlankTime(plankResult?.duration ?: 0L),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 상태
        if (plankResult != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (plankResult.state) {
                        PlankState.IN_POSITION -> Color(0xFF4CAF50)
                        PlankState.HIPS_TOO_HIGH, PlankState.HIPS_TOO_LOW -> Color(0xFFFF9800)
                        PlankState.NOT_IN_POSITION -> Color(0xFF9E9E9E)
                    }.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when (plankResult.state) {
                        PlankState.IN_POSITION -> "완벽한 자세! 💯"
                        PlankState.HIPS_TOO_HIGH -> "엉덩이 ↓"
                        PlankState.HIPS_TOO_LOW -> "엉덩이 ↑"
                        PlankState.NOT_IN_POSITION -> "자세 준비"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 피드백
            Text(
                text = plankResult.feedback,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (plankResult.isGoodForm) Color(0xFF4CAF50) else Color(0xFFFF5722),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 몸 각도
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "몸 정렬도",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = "${plankResult.bodyAngle.toInt()}°",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.3f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // 최고 기록 / 총 시간
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "최고 기록",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatPlankTime(plankResult.bestDuration),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFFD700)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "총 시간",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatPlankTime(plankResult.totalDuration),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        } else {
            Text(
                text = "플랭크 자세를 취하세요",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 플랭크 시간 포맷팅 (MM:SS)
 */
private fun formatPlankTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * 운동 제어 버튼 (휴식/재개, 완료)
 */
@Composable
private fun WorkoutControlButtons(
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 운동 휴식/재개 버튼 (토글)
        Button(
            onClick = onPauseResume,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPaused) Color(0xFF2196F3) else Color(0xFFFF9800)  // 일시정지 시 파란색, 실행 중 주황색
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isPaused) "▶️" else "⏸️",
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPaused) "운동 재개" else "운동 휴식",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 운동 완료 버튼
        Button(
            onClick = onComplete,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)  // 초록색
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "✅",
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "운동 완료",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
