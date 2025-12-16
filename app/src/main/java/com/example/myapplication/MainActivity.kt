package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.WorkoutRecord
import com.example.myapplication.data.WorkoutRepository
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ReFitApp()
                }
            }
        }
    }
}

@Composable
fun ReFitApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // PoseClassifier와 ExerciseCounter를 앱 레벨에서 관리
    val poseClassifier = remember { PoseClassifier(context) }
    val exerciseCounter = remember { ExerciseCounter() }
    val workoutRepository = remember { WorkoutRepository(context) }

    // 운동 완료 다이얼로그 상태
    var showSummaryDialog by remember { mutableStateOf(false) }
    var workoutCount by remember { mutableStateOf(0) }
    var workoutDuration by remember { mutableStateOf(0L) }
    var currentExerciseType by remember { mutableStateOf("SQUAT") }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // 홈 화면
        composable("home") {
            HomeScreen(
                onStartExercise = {
                    navController.navigate("exercise_selection")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                }
            )
        }

        // 운동 선택 화면
        composable("exercise_selection") {
            ExerciseSelectionScreen(
                onExerciseSelected = { exerciseType ->
                    // 운동 타입 설정
                    exerciseCounter.setExerciseType(exerciseType)
                    currentExerciseType = exerciseType.name
                    // 카메라 화면으로 이동
                    navController.navigate("camera")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 카메라/운동 화면
        composable("camera") {
            CameraScreen(
                poseClassifier = poseClassifier,
                exerciseCounter = exerciseCounter,
                onExitWorkout = { count, duration ->
                    workoutCount = count
                    workoutDuration = duration
                    showSummaryDialog = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 운동 히스토리 화면
        composable("history") {
            WorkoutHistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 설정 화면
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }

    // 운동 완료 다이얼로그
    if (showSummaryDialog) {
        WorkoutSummaryDialog(
            exerciseType = currentExerciseType,
            count = workoutCount,
            durationSeconds = workoutDuration,
            onSave = {
                // 운동 기록 저장
                scope.launch {
                    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    val workout = WorkoutRecord(
                        date = today,
                        exerciseType = currentExerciseType,
                        count = workoutCount,
                        duration = workoutDuration
                    )
                    workoutRepository.saveWorkout(workout)
                }
                // 홈으로 이동
                navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                }
                // 카운터 리셋
                exerciseCounter.reset()
            },
            onRetry = {
                // 카운터 리셋
                exerciseCounter.reset()
            },
            onDismiss = {
                showSummaryDialog = false
                // 홈으로 이동
                navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                }
                // 카운터 리셋
                exerciseCounter.reset()
            }
        )
    }

    // 뒤로가기 처리
    DisposableEffect(Unit) {
        onDispose {
            poseClassifier.close()
        }
    }
}
