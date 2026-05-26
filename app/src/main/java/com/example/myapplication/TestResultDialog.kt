package com.example.myapplication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

// ────────────────────────────────────────────────────────────
// 데이터 모델
// ────────────────────────────────────────────────────────────

/**
 * 운동 테스트 결과
 * @param exerciseType      "SQUAT" | "PUSHUP" | "PLANK"
 * @param detectedReps      실제 감지된 횟수 (스쿼트/푸시업)
 * @param targetReps        목표 횟수 (스쿼트/푸시업: 5, 플랭크: 0)
 * @param formAccuracy      자세 정확도 0.0~1.0 (good-form 프레임 비율)
 * @param durationSeconds   테스트 경과 시간
 * @param plankInPositionSeconds  플랭크 유지 시간 (플랭크 전용)
 */
data class TestResult(
    val exerciseType: String,
    val detectedReps: Int,
    val targetReps: Int,
    val formAccuracy: Float,
    val durationSeconds: Long,
    val plankInPositionSeconds: Long = 0L
)

// ────────────────────────────────────────────────────────────
// 다이얼로그
// ────────────────────────────────────────────────────────────

@Composable
fun TestResultDialog(
    result: TestResult,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val exerciseName = when (result.exerciseType) {
        "SQUAT"  -> "스쿼트"
        "PUSHUP" -> "푸시업"
        "PLANK"  -> "플랭크"
        else     -> "운동"
    }
    val accentColor = when (result.exerciseType) {
        "SQUAT"  -> Color(0xFF1976D2)
        "PUSHUP" -> Color(0xFFE64A19)
        "PLANK"  -> Color(0xFF388E3C)
        else     -> Color(0xFF6200EE)
    }

    // 인식률 (0~1)
    val detectionRate = when {
        result.targetReps > 0 ->
            (result.detectedReps.toFloat() / result.targetReps).coerceIn(0f, 1f)
        else ->  // 플랭크: 30초 기준 유지 비율
            (result.plankInPositionSeconds.toFloat() / 30f).coerceIn(0f, 1f)
    }

    // 종합 점수 (인식 50% + 자세 50%)
    val score = ((detectionRate * 0.5f + result.formAccuracy * 0.5f) * 100).roundToInt()

    val (grade, gradeColor, gradeEmoji) = when {
        score >= 90 -> Triple("완벽!",    Color(0xFF4CAF50), "🏆")
        score >= 70 -> Triple("좋음",     Color(0xFF2196F3), "⭐")
        score >= 50 -> Triple("보통",     Color(0xFFFF9800), "👍")
        else        -> Triple("연습 필요", Color(0xFFFF5722), "💪")
    }

    val feedback = when {
        result.formAccuracy < 0.5f -> "자세를 더 정확하게 유지해보세요."
        detectionRate < 0.6f       -> "동작을 더 크게 취해주세요."
        score >= 90                -> "인식과 자세 모두 완벽합니다!"
        score >= 70                -> "잘 되고 있습니다. 조금만 더 연습해보세요."
        else                       -> "카메라 각도나 조명을 확인해보세요."
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── 등급 이모지 + 헤더 ──────────────────────────
                Text(gradeEmoji, fontSize = 48.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "$exerciseName 테스트 결과",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
                Text(
                    text = grade,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = gradeColor
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // ── 동작 인식 / 자세 유지 ───────────────────────
                if (result.targetReps > 0) {
                    TestStatRow(
                        label    = "동작 인식",
                        valueText = "${result.detectedReps} / ${result.targetReps}회",
                        ratio    = detectionRate,
                        barColor = accentColor
                    )
                } else {
                    TestStatRow(
                        label    = "자세 유지",
                        valueText = "${result.plankInPositionSeconds}초 / 30초",
                        ratio    = detectionRate,
                        barColor = accentColor
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── 자세 정확도 ─────────────────────────────────
                val formBarColor = when {
                    result.formAccuracy >= 0.8f -> Color(0xFF4CAF50)
                    result.formAccuracy >= 0.5f -> Color(0xFFFF9800)
                    else                        -> Color(0xFFFF5722)
                }
                TestStatRow(
                    label    = "자세 정확도",
                    valueText = "${(result.formAccuracy * 100).roundToInt()}%",
                    ratio    = result.formAccuracy,
                    barColor = formBarColor
                )

                Spacer(Modifier.height(12.dp))

                // ── 종합 점수 카드 ──────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "종합 점수",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = accentColor
                        )
                        Text(
                            text = "$score 점",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = accentColor
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 피드백 텍스트 ───────────────────────────────
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // ── 버튼 ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onDismiss(); onRetry() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("다시 테스트")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("완료")
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
// 내부 컴포넌트
// ────────────────────────────────────────────────────────────

@Composable
private fun TestStatRow(
    label: String,
    valueText: String,
    ratio: Float,
    barColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.18f)
        )
    }
}
