package com.example.myapplication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 운동 선택 화면
 * - 스쿼트, 푸시업, 플랭크 중 선택
 * - 각 운동별 아이콘과 설명 표시
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelectionScreen(
    onExerciseSelected: (ExerciseCounter.Companion.ExerciseType) -> Unit,
    onTestExercise: (ExerciseCounter.Companion.ExerciseType) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    var selectedExercise by remember { mutableStateOf<ExerciseCounter.Companion.ExerciseType?>(null) }

    val exercises = listOf(
        ExerciseInfo(
            type = ExerciseCounter.Companion.ExerciseType.SQUAT,
            name = "스쿼트",
            description = "힙-무릎-발목 각도 분석",
            benefits = listOf("대퇴사두근 강화", "엉덩이 근육 발달", "코어 안정성 향상"),
            accentColor = Color(0xFF1976D2)
        ),
        ExerciseInfo(
            type = ExerciseCounter.Companion.ExerciseType.PUSHUP,
            name = "푸시업",
            description = "어깨-팔꿈치-손목 각도 분석",
            benefits = listOf("가슴 근육 발달", "팔 근력 강화", "어깨 안정성 향상"),
            accentColor = Color(0xFFE64A19)
        ),
        ExerciseInfo(
            type = ExerciseCounter.Companion.ExerciseType.PLANK,
            name = "플랭크",
            description = "머리-어깨-골반-발목 정렬 분석",
            benefits = listOf("코어 근력 강화", "자세 개선", "복부 근육 발달"),
            accentColor = Color(0xFF388E3C)
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
                        onClick = { selectedExercise = exercise.type },
                        onTest = { onTestExercise(exercise.type) }
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
    onClick: () -> Unit,
    onTest: () -> Unit
) {
    val accent = exerciseInfo.accentColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 상단 색상 바
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(accent, accent.copy(alpha = 0.4f))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 운동 포즈 일러스트
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ExercisePoseIllustration(
                        exerciseType = exerciseInfo.type,
                        color = accent,
                        modifier = Modifier.size(68.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 운동 정보
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = exerciseInfo.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "선택됨",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = exerciseInfo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = accent.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    exerciseInfo.benefits.forEach { benefit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(accent.copy(alpha = 0.5f), RoundedCornerShape(50))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = benefit,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 테스트 버튼
                    OutlinedButton(
                        onClick = onTest,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "테스트 (5회)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

data class ExerciseInfo(
    val type: ExerciseCounter.Companion.ExerciseType,
    val name: String,
    val description: String,
    val benefits: List<String>,
    val accentColor: Color = Color(0xFF1976D2)
)
