package com.example.myapplication

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 운동 포즈 일러스트 (Canvas 스틱 피규어)
 * 각 운동의 올바른 자세를 측면 뷰로 표현합니다.
 */
@Composable
fun ExercisePoseIllustration(
    exerciseType: ExerciseCounter.Companion.ExerciseType,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (exerciseType) {
            ExerciseCounter.Companion.ExerciseType.SQUAT  -> drawSquatPose(color)
            ExerciseCounter.Companion.ExerciseType.PUSHUP -> drawPushUpPose(color)
            ExerciseCounter.Companion.ExerciseType.PLANK  -> drawPlankPose(color)
        }
    }
}

// 정규화 좌표(0~1)를 픽셀 좌표로 변환
private fun DrawScope.p(x: Float, y: Float) = Offset(size.width * x, size.height * y)

/**
 * 스쿼트 - 측면 뷰 (무릎 90도, 상체 약간 전경)
 *
 *   O       <- 머리
 *   |\ ->   <- 상체 + 팔 앞으로 뻗기
 *   \
 *    \  /   <- 허벅지 / 종아리
 *     \/
 */
private fun DrawScope.drawSquatPose(color: Color) {
    val sw    = size.minDimension * 0.056f
    val headR = size.minDimension * 0.090f

    val headC    = p(0.46f, 0.09f)
    val shoulder = p(0.44f, 0.24f)
    val hip      = p(0.34f, 0.53f)   // 힙이 내려간 위치
    val knee     = p(0.60f, 0.70f)   // 무릎 앞으로 나옴
    val ankle    = p(0.38f, 0.90f)   // 발목은 무릎보다 뒤
    val footToe  = p(0.55f, 0.90f)

    val elbow    = p(0.70f, 0.27f)   // 팔 앞으로 뻗음
    val wrist    = p(0.86f, 0.31f)

    // 머리
    drawCircle(color, headR, headC, style = Stroke(sw * 0.85f))

    // 목 → 어깨
    drawLine(color, p(0.46f, 0.18f), shoulder, sw, StrokeCap.Round)

    // 상체 (어깨 → 힙, 전경 자세)
    drawLine(color, shoulder, hip, sw, StrokeCap.Round)

    // 위팔 → 팔꿈치 → 손목 (앞으로 수평)
    drawLine(color, shoulder, elbow, sw, StrokeCap.Round)
    drawLine(color, elbow, wrist, sw * 0.82f, StrokeCap.Round)

    // 허벅지 (힙 → 무릎)
    drawLine(color, hip, knee, sw, StrokeCap.Round)

    // 종아리 (무릎 → 발목)
    drawLine(color, knee, ankle, sw, StrokeCap.Round)

    // 발
    drawLine(color, ankle, footToe, sw * 0.72f, StrokeCap.Round)
}

/**
 * 푸시업 - 측면 뷰 (팔꿈치 굽힘, 몸통 수평)
 *
 *  O
 *   \===========  <- 몸통 수평
 *   /|            <- 팔꿈치 아래로 굽힘
 *  / |
 * 바닥
 */
private fun DrawScope.drawPushUpPose(color: Color) {
    val sw    = size.minDimension * 0.056f
    val headR = size.minDimension * 0.080f

    val headC    = p(0.10f, 0.31f)
    val shoulder = p(0.24f, 0.39f)
    val elbow    = p(0.23f, 0.59f)  // 팔꿈치가 어깨 바로 아래
    val hand     = p(0.14f, 0.65f)  // 바닥
    val hip      = p(0.57f, 0.37f)
    val knee     = p(0.74f, 0.40f)
    val ankle    = p(0.89f, 0.43f)
    val footToe  = p(0.94f, 0.51f)

    val groundY = 0.65f

    // 바닥선
    drawLine(
        color.copy(alpha = 0.22f),
        p(0.04f, groundY), p(0.96f, groundY),
        sw * 0.38f, StrokeCap.Round
    )

    // 머리
    drawCircle(color, headR, headC, style = Stroke(sw * 0.85f))

    // 목 → 어깨
    drawLine(
        color,
        Offset(headC.x + headR * 0.68f, headC.y + headR * 0.68f),
        shoulder, sw, StrokeCap.Round
    )

    // 몸통 (어깨 → 힙, 수평)
    drawLine(color, shoulder, hip, sw, StrokeCap.Round)

    // 위팔 (어깨 → 팔꿈치, 수직으로 내려감)
    drawLine(color, shoulder, elbow, sw, StrokeCap.Round)

    // 아래팔 (팔꿈치 → 손, 바닥 방향)
    drawLine(color, elbow, hand, sw, StrokeCap.Round)

    // 허벅지 → 무릎 → 발목
    drawLine(color, hip, knee, sw, StrokeCap.Round)
    drawLine(color, knee, ankle, sw, StrokeCap.Round)

    // 발
    drawLine(color, ankle, footToe, sw * 0.72f, StrokeCap.Round)
}

/**
 * 플랭크 - 측면 뷰 (팔꿈치 지지, 머리~발 일직선)
 *
 *  O
 *   \===============  <- 몸통 완전 수평
 *   /|
 *  / |
 * 바닥
 */
private fun DrawScope.drawPlankPose(color: Color) {
    val sw    = size.minDimension * 0.056f
    val headR = size.minDimension * 0.080f

    val headC    = p(0.10f, 0.25f)
    val shoulder = p(0.24f, 0.36f)
    val elbow    = p(0.26f, 0.57f)  // 팔꿈치 90도
    val hand     = p(0.16f, 0.63f)  // 팔꿈치 앞 바닥
    val hip      = p(0.57f, 0.34f)  // 어깨와 같은 높이
    val knee     = p(0.74f, 0.35f)
    val ankle    = p(0.89f, 0.36f)
    val footToe  = p(0.94f, 0.43f)

    val groundY = 0.63f

    // 바닥선
    drawLine(
        color.copy(alpha = 0.22f),
        p(0.04f, groundY), p(0.96f, groundY),
        sw * 0.38f, StrokeCap.Round
    )

    // 머리
    drawCircle(color, headR, headC, style = Stroke(sw * 0.85f))

    // 목 → 어깨
    drawLine(
        color,
        Offset(headC.x + headR * 0.72f, headC.y + headR * 0.65f),
        shoulder, sw, StrokeCap.Round
    )

    // 몸통 (어깨 → 힙, 완전 수평)
    drawLine(color, shoulder, hip, sw, StrokeCap.Round)

    // 위팔 (어깨 → 팔꿈치)
    drawLine(color, shoulder, elbow, sw, StrokeCap.Round)

    // 아래팔 (팔꿈치 → 손, 전방 바닥)
    drawLine(color, elbow, hand, sw, StrokeCap.Round)

    // 허벅지 → 무릎 → 발목 (완전 수평)
    drawLine(color, hip, knee, sw, StrokeCap.Round)
    drawLine(color, knee, ankle, sw, StrokeCap.Round)

    // 발 (발끝 지지)
    drawLine(color, ankle, footToe, sw * 0.72f, StrokeCap.Round)
}
