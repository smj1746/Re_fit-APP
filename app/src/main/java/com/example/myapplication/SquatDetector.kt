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
import kotlin.text.toDouble

/**
 * ML Kit 키포인트 기반 스쿼트 동작 감지
 * 힙-무릎-발목 각도를 계산하여 스쿼트 동작을 정확하게 감지합니다.
 */
class SquatDetector {

    companion object {
        private const val TAG = "SquatDetector"

        // 스쿼트 상태 정의
        enum class SquatState {
            STANDING,    // 서있는 상태 (시작 자세)
            DESCENDING,  // 하강 중
            BOTTOM,      // 최하단 (스쿼트 자세)
            ASCENDING    // 상승 중
        }

        // 각도 임계값
        private const val STANDING_KNEE_ANGLE = 160f  // 서있는 자세 무릎 각도
        private const val SQUAT_KNEE_ANGLE = 90f      // 스쿼트 자세 무릎 각도
        private const val ANGLE_TOLERANCE = 15f        // 각도 허용 오차

        // 힙 높이 변화 임계값
        private const val HIP_HEIGHT_THRESHOLD = 0.15f // 전체 키의 15%

        // 신뢰도 임계값
        private const val MIN_CONFIDENCE = 0.6f
    }

    private var currentState = SquatState.STANDING
    private var squatCount = 0
    private var lastHipHeight = 0f
    private var referenceHeight = 0f
    private var isCalibrated = false

    data class SquatResult(
        val count: Int,
        val state: SquatState,
        val leftKneeAngle: Float,
        val rightKneeAngle: Float,
        val hipHeight: Float,
        val isGoodForm: Boolean,
        val feedback: String
    )

    /**
     * 포즈를 분석하여 스쿼트 감지
     */
    fun detectSquat(pose: Pose): SquatResult {
        // 필수 키포인트 추출
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        // 키포인트 유효성 검사
        if (!areKeypointsValid(
                leftHip, rightHip, leftKnee, rightKnee,
                leftAnkle, rightAnkle, leftShoulder, rightShoulder
            )) {
            return SquatResult(
                count = squatCount,
                state = currentState,
                leftKneeAngle = 0f,
                rightKneeAngle = 0f,
                hipHeight = 0f,
                isGoodForm = false,
                feedback = "전신이 보이도록 카메라 위치를 조정해주세요"
            )
        }

        // 무릎 각도 계산
        val leftKneeAngle = calculateAngle(
            leftHip!!.position,
            leftKnee!!.position,
            leftAnkle!!.position
        )
        val rightKneeAngle = calculateAngle(
            rightHip!!.position,
            rightKnee!!.position,
            rightAnkle!!.position
        )
        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        // 힙 높이 계산 (중심점)
        val hipHeight = (leftHip.position.y + rightHip.position.y) / 2

        // 기준 높이 보정 (처음 감지 시)
        if (!isCalibrated) {
            val bodyHeight = calculateBodyHeight(leftShoulder!!, leftHip, leftAnkle)
            referenceHeight = bodyHeight
            lastHipHeight = hipHeight
            isCalibrated = true
            Log.d(TAG, "보정 완료 - 기준 높이: $referenceHeight")
        }

        // 힙 높이 변화량 계산 (정규화)
        val hipHeightChange = (lastHipHeight - hipHeight) / referenceHeight

        // 자세 평가
        val formCheck = evaluateForm(
            leftKneeAngle, rightKneeAngle,
            leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle
        )

        // 상태 전이 및 카운팅
        val newState = updateState(avgKneeAngle, hipHeightChange)

        // 이전 상태 저장
        lastHipHeight = hipHeight

        val feedback = generateFeedback(currentState, formCheck, avgKneeAngle)

        Log.d(TAG, "무릎각도: ${avgKneeAngle.toInt()}°, 힙높이: ${hipHeightChange.format(3)}, 상태: $currentState, 카운트: $squatCount")

        return SquatResult(
            count = squatCount,
            state = currentState,
            leftKneeAngle = leftKneeAngle,
            rightKneeAngle = rightKneeAngle,
            hipHeight = hipHeight,
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
        // .y()와 .x()를 .y와 .x 속성으로 변경했습니다.
        val radians = atan2(lastPoint.y - midPoint.y, lastPoint.x - midPoint.x) -
                atan2(firstPoint.y - midPoint.y, firstPoint.x - midPoint.x)
        var angle = abs(radians * 180.0 / PI).toFloat()

        // Normalize to 0-180 degree range
        if (angle > 180.0) {
            angle = 360.0f - angle
        }

        return angle
    }

    /**
     * 신체 높이 계산
     */
    private fun calculateBodyHeight(
        shoulder: PoseLandmark,
        hip: PoseLandmark,
        ankle: PoseLandmark
    ): Float {
        val shoulderToHip = sqrt(
            (shoulder.position.x - hip.position.x).pow(2) +
            (shoulder.position.y - hip.position.y).pow(2)
        )
        val hipToAnkle = sqrt(
            (hip.position.x - ankle.position.x).pow(2) +
            (hip.position.y - ankle.position.y).pow(2)
        )
        return shoulderToHip + hipToAnkle
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
    private fun updateState(kneeAngle: Float, hipHeightChange: Float): SquatState {
        val previousState = currentState

        currentState = when (currentState) {
            SquatState.STANDING -> {
                // 무릎이 구부러지고 힙이 내려가면 하강 시작
                if (kneeAngle < STANDING_KNEE_ANGLE - ANGLE_TOLERANCE && hipHeightChange > 0.05f) {
                    SquatState.DESCENDING
                } else {
                    SquatState.STANDING
                }
            }

            SquatState.DESCENDING -> {
                // 무릎 각도가 스쿼트 각도에 도달하면 최하단
                if (kneeAngle <= SQUAT_KNEE_ANGLE + ANGLE_TOLERANCE) {
                    SquatState.BOTTOM
                } else if (kneeAngle > STANDING_KNEE_ANGLE - ANGLE_TOLERANCE) {
                    // 다시 서있는 자세로 돌아감 (중단)
                    SquatState.STANDING
                } else {
                    SquatState.DESCENDING
                }
            }

            SquatState.BOTTOM -> {
                // 무릎이 펴지기 시작하면 상승 시작
                if (kneeAngle > SQUAT_KNEE_ANGLE + ANGLE_TOLERANCE * 2) {
                    SquatState.ASCENDING
                } else {
                    SquatState.BOTTOM
                }
            }

            SquatState.ASCENDING -> {
                // 완전히 일어서면 카운트 증가
                if (kneeAngle >= STANDING_KNEE_ANGLE - ANGLE_TOLERANCE) {
                    squatCount++
                    Log.d(TAG, "스쿼트 완료! 총 카운트: $squatCount")
                    SquatState.STANDING
                } else if (kneeAngle < SQUAT_KNEE_ANGLE + ANGLE_TOLERANCE) {
                    // 다시 내려가면 최하단으로
                    SquatState.BOTTOM
                } else {
                    SquatState.ASCENDING
                }
            }
        }

        if (previousState != currentState) {
            Log.d(TAG, "상태 전이: $previousState -> $currentState")
        }

        return currentState
    }

    /**
     * 자세 평가
     */
    private fun evaluateForm(
        leftKneeAngle: Float,
        rightKneeAngle: Float,
        leftHip: PoseLandmark,
        rightHip: PoseLandmark,
        leftKnee: PoseLandmark,
        rightKnee: PoseLandmark,
        leftAnkle: PoseLandmark,
        rightAnkle: PoseLandmark
    ): FormCheck {
        val issues = mutableListOf<String>()

        // 1. 좌우 무릎 각도 차이 확인
        val kneeDiff = abs(leftKneeAngle - rightKneeAngle)
        if (kneeDiff > 20f) {
            issues.add("좌우 균형을 맞춰주세요")
        }

        // 2. 무릎이 발끝보다 앞으로 나가는지 확인
        val leftKneeOverToe = leftKnee.position.x < leftAnkle.position.x - 50f
        val rightKneeOverToe = rightKnee.position.x > rightAnkle.position.x + 50f

        if (leftKneeOverToe || rightKneeOverToe) {
            issues.add("무릎이 발끝보다 앞으로 나갔습니다")
        }

        // 3. 힙이 무릎보다 낮은지 확인 (깊은 스쿼트)
        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
        val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2

        val isDeepSquat = avgHipY > avgKneeY

        return FormCheck(
            isGoodForm = issues.isEmpty(),
            issues = issues,
            isDeepSquat = isDeepSquat
        )
    }

    /**
     * 피드백 생성
     */
    private fun generateFeedback(state: SquatState, formCheck: FormCheck, kneeAngle: Float): String {
        if (!formCheck.isGoodForm) {
            return formCheck.issues.firstOrNull() ?: "자세를 확인해주세요"
        }

        return when (state) {
            SquatState.STANDING -> "준비 자세 - 스쿼트를 시작하세요"
            SquatState.DESCENDING -> "하강 중 - 무릎 각도: ${kneeAngle.toInt()}°"
            SquatState.BOTTOM -> if (formCheck.isDeepSquat) "완벽합니다! 이제 일어나세요" else "조금 더 깊게 내려가세요"
            SquatState.ASCENDING -> "상승 중 - 힘을 내세요!"
        }
    }

    /**
     * 카운터 리셋
     */
    fun reset() {
        squatCount = 0
        currentState = SquatState.STANDING
        lastHipHeight = 0f
        referenceHeight = 0f
        isCalibrated = false
        Log.d(TAG, "스쿼트 감지기 리셋")
    }

    /**
     * 현재 카운트 반환
     */
    fun getCount(): Int = squatCount

    /**
     * 자세 평가 결과
     */
    private data class FormCheck(
        val isGoodForm: Boolean,
        val issues: List<String>,
        val isDeepSquat: Boolean
    )
}

/**
 * Float 포맷팅 확장 함수
 */
private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
