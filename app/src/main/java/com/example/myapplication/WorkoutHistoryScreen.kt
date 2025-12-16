package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.WorkoutRecord
import com.example.myapplication.data.WorkoutRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 운동 히스토리 화면
 * 날짜별 운동 기록을 표시합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { WorkoutRepository(context) }
    val scope = rememberCoroutineScope()

    // 날짜별로 그룹화된 운동 기록
    var groupedWorkouts by remember { mutableStateOf<Map<String, List<WorkoutRecord>>>(emptyMap()) }
    var consecutiveDays by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    // 운동 기록 로드
    LaunchedEffect(Unit) {
        repository.getRecentWorkouts(30).collect { workouts ->
            groupedWorkouts = workouts.groupBy { it.date }
            isLoading = false
        }

        consecutiveDays = repository.getConsecutiveDays()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "운동 히스토리",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로 가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (groupedWorkouts.isEmpty()) {
            EmptyHistoryView(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 연속 운동일 배지
                if (consecutiveDays > 0) {
                    item {
                        ConsecutiveDaysBadge(consecutiveDays)
                    }
                }

                // 날짜별 운동 기록
                items(groupedWorkouts.entries.toList()) { (date, workouts) ->
                    DailyWorkoutCard(
                        date = date,
                        workouts = workouts,
                        onDeleteWorkout = { workout ->
                            scope.launch {
                                repository.deleteWorkout(workout)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 연속 운동일 배지
 */
@Composable
private fun ConsecutiveDaysBadge(consecutiveDays: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50) // 초록색
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🔥",
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "연속 운동 중!",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Text(
                    text = "$consecutiveDays 일 연속",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 날짜별 운동 카드
 */
@Composable
private fun DailyWorkoutCard(
    date: String,
    workouts: List<WorkoutRecord>,
    onDeleteWorkout: (WorkoutRecord) -> Unit
) {
    val totalDuration = workouts.sumOf { it.duration }
    val hasNewRecord = workouts.any { it.isNewRecord }
    val formattedDate = formatDate(date)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 날짜 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 $formattedDate",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (hasNewRecord) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEB3B) // 노란색
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "⭐ 신기록",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFFF6F00),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 운동 목록
            workouts.forEach { workout ->
                WorkoutItem(
                    workout = workout,
                    onDelete = { onDeleteWorkout(workout) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Divider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // 총 운동 시간
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "총 운동시간",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = formatDuration(totalDuration),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 개별 운동 아이템
 */
@Composable
private fun WorkoutItem(
    workout: WorkoutRecord,
    onDelete: () -> Unit
) {
    val (icon, name, unit) = when (workout.exerciseType) {
        "SQUAT" -> Triple("🏋️", "스쿼트", "회")
        "PUSHUP" -> Triple("🤸", "푸시업", "회")
        "PLANK" -> Triple("🧘", "플랭크", "초")
        else -> Triple("💪", "운동", "회")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (workout.isNewRecord) {
                Color(0xFFFFF9C4) // 연한 노란색
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = icon,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "$name: ${workout.count} $unit",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (workout.isNewRecord) {
                        Text(
                            text = "⭐ 신기록!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800)
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 빈 히스토리 뷰
 */
@Composable
private fun EmptyHistoryView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📊",
                fontSize = 64.sp
            )
            Text(
                text = "아직 운동 기록이 없습니다",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "첫 운동을 시작해보세요!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * 날짜 포맷팅
 */
private fun formatDate(dateString: String): String {
    return try {
        val date = LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        when (date) {
            today -> "오늘 (${date.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))})"
            yesterday -> "어제 (${date.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))})"
            else -> date.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))
        }
    } catch (e: Exception) {
        dateString
    }
}

/**
 * 시간 포맷팅
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> String.format("%d시간 %02d분", hours, minutes)
        minutes > 0 -> String.format("%02d분 %02d초", minutes, secs)
        else -> String.format("%02d초", secs)
    }
}
