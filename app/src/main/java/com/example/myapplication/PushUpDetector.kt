package com.example.myapplication

import android.graphics.PointF
import android.util.Log
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 푸시업 상태 정의
 */
enum class PushUpState {
    UP,          // 팔이 펴진 상태 (시작 자세)
    DESCENDING,  // 하강 중
    DOWN,        // 팔이 굽혀진 상태 (가슴이 바닥에 가까움)
    ASCENDING    // 상승 중
}

/**
 * ML Kit 키포인트 기반 푸시업 동작 감지
 * 어깨-팔꿈치-손목 각도를 계산하여 푸시업 동작을 정확하게 감지합니다.
 */
class PushUpDetector {

    companion object {
        private const val TAG = "PushUpDetector"

        // 각도 임계값
        private const val UP_ELBOW_ANGLE = 160f      // 팔 펴진 상태
        private const val DOWN_ELBOW_ANGLE = 90f     // 팔 굽힌 상태
        private const val ANGLE_TOLERANCE = 15f       // 각도 허용 오차

        // 몸의 정렬도 임계값
        private const val BODY_ALIGNMENT_THRESHOLD = 20f  // 몸의 일직선 허용 각도

        // 신뢰도 임계값
        private const val MIN_CONFIDENCE = 0.6f
    }

    private var currentState = PushUpState.UP
    private var pushUpCount = 0

    data class PushUpResult(
        val count: Int,
        val state: PushUpState,
        val leftElbowAngle: Float,
        val rightElbowAngle: Float,
        val bodyAlignment: Float,
        val isGoodForm: Boolean,
        val feedback: String
    )

    /**
     * 포즈를 분석하여 푸시업 감지
     */
    fun detectPushUp(pose: Pose): PushUpResult {
        // 필수 키포인트 추출
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        // 키포인트 유효성 검사
        if (!areKeypointsValid(
                leftShoulder, rightShoulder, leftElbow, rightElbow,
                leftWrist, rightWrist, leftHip, rightHip, leftAnkle, rightAnkle
            )) {
            return PushUpResult(
                count = pushUpCount,
                state = currentState,
                leftElbowAngle = 0f,
                rightElbowAngle = 0f,
                bodyAlignment = 0f,
                isGoodForm = false,
                feedback = "전신이 보이도록 카메라 위치를 조정해주세요"
            )
        }

        // 팔꿈치 각도 계산 (어깨-팔꿈치-손목)
        val leftElbowAngle = calculateAngle(
            leftShoulder!!.position,
            leftElbow!!.position,
            leftWrist!!.position
        )
        val rightElbowAngle = calculateAngle(
            rightShoulder!!.position,
            rightElbow!!.position,
            rightWrist!!.position
        )
        val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2

        // 몸의 정렬도 계산 (어깨-엉덩이-발목)
        val shoulderCenter = PointF(
            (leftShoulder.position.x + rightShoulder.position.x) / 2,
            (leftShoulder.position.y + rightShoulder.position.y) / 2
        )
        val hipCenter = PointF(
            (leftHip!!.position.x + rightHip!!.position.x) / 2,
            (leftHip.position.y + rightHip.position.y) / 2
        )
        val ankleCenter = PointF(
            (leftAnkle!!.position.x + rightAnkle!!.position.x) / 2,
            (leftAnkle.position.y + rightAnkle.position.y) / 2
        )
        val bodyAlignment = calculateAngle(shoulderCenter, hipCenter, ankleCenter)

        // 자세 평가
        val formCheck = evaluateForm(
            leftElbowAngle, rightElbowAngle, bodyAlignment
        )

        // 상태 전이 및 카운팅
        updateState(avgElbowAngle)

        val feedback = generateFeedback(currentState, formCheck, avgElbowAngle, bodyAlignment)

        Log.d(TAG, "팔꿈치각도: ${avgElbowAngle.toInt()}°, 몸정렬: ${bodyAlignment.toInt()}°, 상태: $currentState, 카운트: $pushUpCount")

        return PushUpResult(
            count = pushUpCount,
            state = currentState,
            leftElbowAngle = leftElbowAngle,
            rightElbowAngle = rightElbowAngle,
            bodyAlignment = bodyAlignment,
            isGoodForm = formCheck.isGoodForm,
            feedback = feedback
        )
    }

    /**
     * 두 점 사이의 각도 계산 (3개 점으로 이루어진 각도)
     */
    private fun calculateAngle(
        firstPoint: PointF,
        midPoint: PointF,
        lastPoint: PointF
    ): Float {
        val radians = atan2(lastPoint.y - midPoint.y, lastPoint.x - midPoint.x) -
                atan2(firstPoint.y - midPoint.y, firstPoint.x - midPoint.x)
        var angle = abs(radians * 180.0 / PI).toFloat()

        // 0-180도 범위로 정규화
        if (angle > 180.0) {
            angle = 360.0f - angle
        }

        return angle
    }

    /**
     * 키포인트 유효성 검사
     */
    private fun areKeypointsValid(vararg landmarks: PoseLandmark?): Boolean {
        return landmarks.all { it != null && it.inFrameLikelihood > MIN_CONFIDENCE }
    }

    /**
     * 상태 업데이트 및 카운팅
     */
    private fun updateState(elbowAngle: Float) {
        val previousState = currentState

        currentState = when (currentState) {
            PushUpState.UP -> {
                // 팔이 굽혀지기 시작하면 하강 시작
                if (elbowAngle < UP_ELBOW_ANGLE - ANGLE_TOLERANCE) {
                    PushUpState.DESCENDING
                } else {
                    PushUpState.UP
                }
            }

            PushUpState.DESCENDING -> {
                // 팔꿈치 각도가 90도에 도달하면 최하단
                if (elbowAngle <= DOWN_ELBOW_ANGLE + ANGLE_TOLERANCE) {
                    PushUpState.DOWN
                } else if (elbowAngle > UP_ELBOW_ANGLE - ANGLE_TOLERANCE) {
                    // 다시 팔을 펴면 시작 자세로
                    PushUpState.UP
                } else {
                    PushUpState.DESCENDING
                }
            }

            PushUpState.DOWN -> {
                // 팔이 펴지기 시작하면 상승 시작
                if (elbowAngle > DOWN_ELBOW_ANGLE + ANGLE_TOLERANCE * 2) {
                    PushUpState.ASCENDING
                } else {
                    PushUpState.DOWN
                }
            }

            PushUpState.ASCENDING -> {
                // 완전히 팔을 펴면 카운트 증가
                if (elbowAngle >= UP_ELBOW_ANGLE - ANGLE_TOLERANCE) {
                    pushUpCount++
                    Log.d(TAG, "푸시업 완료! 총 카운트: $pushUpCount")
                    PushUpState.UP
                } else if (elbowAngle < DOWN_ELBOW_ANGLE + ANGLE_TOLERANCE) {
                    // 다시 내려가면 최하단으로
                    PushUpState.DOWN
                } else {
                    PushUpState.ASCENDING
                }
            }
        }

        if (previousState != currentState) {
            Log.d(TAG, "상태 전이: $previousState -> $currentState")
        }
    }

    /**
     * 자세 평가
     */
    private fun evaluateForm(
        leftElbowAngle: Float,
        rightElbowAngle: Float,
        bodyAlignment: Float
    ): FormCheck {
        val issues = mutableListOf<String>()

        // 1. 좌우 팔꿈치 각도 차이 확인
        val elbowDiff = abs(leftElbowAngle - rightElbowAngle)
        if (elbowDiff > 20f) {
            issues.add(
                if (leftElbowAngle < rightElbowAngle) "왼팔을 더 펴세요"
                else "오른팔을 더 펴세요"
            )
        }

        // 2. 몸의 일직선 유지 확인
        val bodyAlignmentDeviation = abs(180f - bodyAlignment)
        if (bodyAlignmentDeviation > BODY_ALIGNMENT_THRESHOLD) {
            issues.add("몸을 일직선으로 유지하세요")
        }

        return FormCheck(
            isGoodForm = issues.isEmpty(),
            issues = issues,
            bodyAlignmentDeviation = bodyAlignmentDeviation
        )
    }

    /**
     * 피드백 생성
     */
    private fun generateFeedback(
        state: PushUpState,
        formCheck: FormCheck,
        elbowAngle: Float,
        bodyAlignment: Float
    ): String {
        if (!formCheck.isGoodForm) {
            return formCheck.issues.firstOrNull() ?: "자세를 확인해주세요"
        }

        return when (state) {
            PushUpState.UP -> "준비 자세 - 푸시업을 시작하세요"
            PushUpState.DESCENDING -> "하강 중 - 팔꿈치 각도: ${elbowAngle.toInt()}°"
            PushUpState.DOWN -> "완벽합니다! 이제 팔을 펴세요"
            PushUpState.ASCENDING -> "상승 중 - 힘을 내세요!"
        }
    }

    /**
     * 카운터 리셋
     */
    fun reset() {
        pushUpCount = 0
        currentState = PushUpState.UP
        Log.d(TAG, "푸시업 감지기 리셋")
    }

    /**
     * 현재 카운트 반환
     */
    fun getCount(): Int = pushUpCount

    /**
     * 자세 평가 결과
     */
    private data class FormCheck(
        val isGoodForm: Boolean,
        val issues: List<String>,
        val bodyAlignmentDeviation: Float
    )
}
