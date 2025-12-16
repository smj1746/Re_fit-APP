package com.example.myapplication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 운동 선택 화면
 * - 스쿼트, 푸시업, 플랭크 중 선택
 * - 각 운동별 아이콘과 설명 표시
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelectionScreen(
    onExerciseSelected: (ExerciseCounter.Companion.ExerciseType) -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedExercise by remember { mutableStateOf<ExerciseCounter.Companion.ExerciseType?>(null) }

    val exercises = listOf(
        ExerciseInfo(
            type = ExerciseCounter.Companion.ExerciseType.SQUAT,
            icon = "🏋️",
            name = "스쿼트",
            description = "하체 근력 강화를 위한 기본 운동",
            benefits = listOf("대퇴사두근 강화", "엉덩이 근육 발달", "코어 안정성 향상")
        ),
        ExerciseInfo(
            type = ExerciseCounter.Companion.ExerciseType.PUSHUP,
            icon = "🤸",
            name = "푸시업",
            description = "상체 근력 강화를 위한 대표 운동",
            benefits = listOf("가슴 근육 발달", "팔 근력 강화", "어깨 안정성 향상")
        ),
        ExerciseInfo(
            type = ExerciseCounter.Companion.ExerciseType.PLANK,
            icon = "🧘",
            name = "플랭크",
            description = "코어 강화 및 자세 교정 운동",
            benefits = listOf("코어 근력 강화", "자세 개선", "복부 근육 발달")
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "운동 선택",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "오늘 할 운동을 선택하세요",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(exercises) { exercise ->
                    ExerciseCard(
                        exerciseInfo = exercise,
                        isSelected = selectedExercise == exercise.type,
                        onClick = { selectedExercise = exercise.type }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 시작하기 버튼
            Button(
                onClick = {
                    selectedExercise?.let { onExerciseSelected(it) }
                },
                enabled = selectedExercise != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "운동 시작하기",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exerciseInfo: ExerciseInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 아이콘
            Text(
                text = exerciseInfo.icon,
                fontSize = 48.sp,
                modifier = Modifier.padding(end = 16.dp)
            )

            // 운동 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = exerciseInfo.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = exerciseInfo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 효과
                exerciseInfo.benefits.forEach { benefit ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 4.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            }
                        )
                        Text(
                            text = benefit,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            }
                        )
                    }
                }
            }
        }
    }
}

data class ExerciseInfo(
    val type: ExerciseCounter.Companion.ExerciseType,
    val icon: String,
    val name: String,
    val description: String,
    val benefits: List<String>
)
