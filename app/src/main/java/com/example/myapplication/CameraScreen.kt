package com.example.myapplication

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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
    onExitWorkout: (Int, Long) -> Unit,
    onNavigateBack: () -> Unit,
    onTestComplete: (TestResult) -> Unit = {},
    isTestMode: Boolean = false,
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
                    onExitWorkout = onExitWorkout,
                    onNavigateBack = onNavigateBack,
                    onTestComplete = onTestComplete,
                    isTestMode = isTestMode
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
    onExitWorkout: (Int, Long) -> Unit,
    onNavigateBack: () -> Unit,
    onTestComplete: (TestResult) -> Unit = {},
    isTestMode: Boolean = false
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

    // 운동 타입 선택 — exerciseCounter에서 초기화
    var selectedExerciseType by remember {
        mutableStateOf(
            when (exerciseCounter.getExerciseType()) {
                ExerciseCounter.Companion.ExerciseType.SQUAT  -> ExerciseType.SQUAT
                ExerciseCounter.Companion.ExerciseType.PUSHUP -> ExerciseType.PUSHUP
                ExerciseCounter.Companion.ExerciseType.PLANK  -> ExerciseType.PLANK
            }
        )
    }

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

    // ── 테스트 모드: good-form 프레임 추적 ─────────────────────
    val testFrameTotal = remember { AtomicInteger(0) }
    val testFrameGoodForm = remember { AtomicInteger(0) }
    var testCompleted by remember { mutableStateOf(false) }

    // snapshotFlow로 매 포즈 결과마다 good-form 여부 집계
    if (isTestMode) {
        LaunchedEffect(Unit) {
            snapshotFlow {
                when (selectedExerciseType) {
                    ExerciseType.SQUAT  -> squatResult?.isGoodForm
                    ExerciseType.PUSHUP -> pushUpResult?.isGoodForm
                    ExerciseType.PLANK  -> plankResult?.isGoodForm
                }
            }.collect { isGood ->
                if (!testCompleted) {
                    testFrameTotal.incrementAndGet()
                    if (isGood == true) testFrameGoodForm.incrementAndGet()
                }
            }
        }
    }

    // 테스트 완료 헬퍼
    fun finishTest() {
        if (testCompleted) return
        testCompleted = true
        // stop() 이전에 elapsed 저장 (stop()이 내부 값을 0으로 리셋함)
        val elapsedSecs = workoutTimer.getElapsedSeconds()
        workoutTimer.stop()
        val formAcc = if (testFrameTotal.get() > 0)
            testFrameGoodForm.get().toFloat() / testFrameTotal.get() else 0f
        val reps = when (selectedExerciseType) {
            ExerciseType.SQUAT  -> squatResult?.count ?: 0
            ExerciseType.PUSHUP -> pushUpResult?.count ?: 0
            ExerciseType.PLANK  -> 0
        }
        val plankSecs = (plankResult?.duration ?: 0L) / 1000L
        onTestComplete(
            TestResult(
                exerciseType           = selectedExerciseType.name,
                detectedReps           = reps,
                targetReps             = if (selectedExerciseType == ExerciseType.PLANK) 0 else 5,
                formAccuracy           = formAcc,
                durationSeconds        = elapsedSecs,
                plankInPositionSeconds = plankSecs
            )
        )
    }

    // 플랭크 테스트: 30초 자동완료
    if (isTestMode && selectedExerciseType == ExerciseType.PLANK) {
        LaunchedEffect(Unit) {
            delay(30_000L)
            finishTest()
        }
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

        // 테스트 모드: 스쿼트/푸시업 5회 달성 시 자동 완료
        if (isTestMode && selectedExerciseType != ExerciseType.PLANK) {
            val testCount = when (selectedExerciseType) {
                ExerciseType.SQUAT  -> squatResult?.count ?: 0
                ExerciseType.PUSHUP -> pushUpResult?.count ?: 0
                else                -> 0
            }
            LaunchedEffect(testCount) {
                if (testCount >= 5) finishTest()
            }
        }

        // 상단 정보 바: 타이머 + 카운트(중앙) + X 버튼(우측)
        TopInfoBar(
            formattedTime = formattedTime,
            exerciseType = selectedExerciseType,
            counterResult = counterResult,
            pushUpResult = pushUpResult,
            plankResult = plankResult,
            isTestMode = isTestMode,
            onExit = {
                workoutTimer.stop()
                onNavigateBack()
            },
            modifier = Modifier.align(Alignment.TopCenter)
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
 * 상단 정보 바: [빈 공간] [타이머 + 카운트] [X 버튼]
 * - 그라디언트 배경 (상단 불투명 → 하단 투명)
 */
@Composable
private fun TopInfoBar(
    formattedTime: String,
    exerciseType: ExerciseType,
    counterResult: ExerciseCounter.CounterResult,
    pushUpResult: PushUpDetector.PushUpResult?,
    plankResult: PlankDetector.PlankResult?,
    onExit: () -> Unit,
    isTestMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rawCount = when (exerciseType) {
        ExerciseType.SQUAT  -> counterResult.count
        ExerciseType.PUSHUP -> pushUpResult?.count ?: 0
        ExerciseType.PLANK  -> 0
    }
    val countText = when {
        isTestMode && exerciseType == ExerciseType.PLANK ->
            // 플랭크 테스트: 유지 시간 / 목표 30초
            "${(plankResult?.duration ?: 0L) / 1000L}초 / 30초"
        isTestMode ->
            "$rawCount / 5"
        exerciseType == ExerciseType.PLANK ->
            formatPlankTime(plankResult?.duration ?: 0L)
        else ->
            "${rawCount}회"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.65f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // 타이머 + 카운트 (중앙)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 테스트 모드 배지
            if (isTestMode) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "테스트 모드",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = Color(0xFF4CAF50)
            )
            Text(
                text = countText,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp
                ),
                color = Color.White
            )
        }

        // X 버튼 (우측)
        ExitButton(
            onClick = onExit,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

/**
 * 나가기 버튼 (우측 상단)
 * - 48dp 터치 영역 유지, 시각적 원은 ~34dp (30% 축소)
 * - 프레스 시 스케일 애니메이션
 */
@Composable
private fun ExitButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "exitButtonScale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(color = Color(0xFFFF5722), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "나가기",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
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
