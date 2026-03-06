package com.example.myapplication

import android.graphics.PointF
import android.util.Log
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * ML Kit 키포인트 기반 플랭크 자세 감지
 * 어깨-엉덩이-발목의 정렬도를 계산하여 플랭크 자세를 정확하게 감지하고 시간을 측정합니다.
 */
class PlankDetector {

    companion object {
        private const val TAG = "PlankDetector"

        // 플랭크 상태 정의
        enum class PlankState {
            NOT_IN_POSITION,  // 플랭크 자세가 아님
            IN_POSITION,      // 플랭크 자세 유지 중
            HIPS_TOO_HIGH,    // 엉덩이가 너무 높음
            HIPS_TOO_LOW      // 엉덩이가 너무 낮음
        }

        // 각도 임계값
        private const val PERFECT_PLANK_ANGLE = 180f  // 완벽한 일직선
        private const val PLANK_TOLERANCE = 15f       // 허용 오차

        // 팔꿈치 위치 임계값
        private const val ELBOW_SHOULDER_DISTANCE_THRESHOLD = 100f  // 픽셀 단위

        // 신뢰도 임계값
        private const val MIN_CONFIDENCE = 0.6f
    }

    private var currentState = PlankState.NOT_IN_POSITION
    private var plankStartTime: Long = 0
    private var totalPlankTime: Long = 0
    private var isTimerRunning = false
    private var bestTime: Long = 0

    data class PlankResult(
        val duration: Long,              // 현재 세션 유지 시간 (밀리초)
        val totalDuration: Long,         // 총 누적 시간 (밀리초)
        val bestDuration: Long,          // 최고 기록 (밀리초)
        val state: PlankState,           // 현재 상태
        val bodyAngle: Float,            // 몸 각도
        val isGoodForm: Boolean,         // 자세가 올바른지
        val feedback: String             // 피드백 메시지
    )

    /**
     * 포즈를 분석하여 플랭크 감지
     */
    fun detectPlank(pose: Pose): PlankResult {
        // 필수 키포인트 추출
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        // 키포인트 유효성 검사
        if (!areKeypointsValid(
                leftShoulder, rightShoulder, leftElbow, rightElbow,
                leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle
            )) {
            stopTimer()
            return PlankResult(
                duration = 0L,
                totalDuration = totalPlankTime,
                bestDuration = bestTime,
                state = PlankState.NOT_IN_POSITION,
                bodyAngle = 0f,
                isGoodForm = false,
                feedback = "전신이 보이도록 카메라 위치를 조정해주세요"
            )
        }

        // 몸의 정렬도 계산 (어깨-엉덩이-발목)
        val shoulderCenter = PointF(
            (leftShoulder!!.position.x + rightShoulder!!.position.x) / 2,
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
        val bodyAngle = calculateAngle(shoulderCenter, hipCenter, ankleCenter)

        // 팔꿈치 위치 확인 (어깨 바로 아래에 있어야 함)
        val elbowCenter = PointF(
            (leftElbow!!.position.x + rightElbow!!.position.x) / 2,
            (leftElbow.position.y + rightElbow.position.y) / 2
        )
        val elbowShoulderDistance = calculateDistance(elbowCenter, shoulderCenter)
        val isElbowPositionCorrect = elbowShoulderDistance < ELBOW_SHOULDER_DISTANCE_THRESHOLD

        // 무릎 각도 확인 (무릎이 펴져 있어야 함)
        val leftKneeAngle = calculateKneeAngle(
            leftHip.position,
            leftKnee!!.position,
            leftAnkle.position
        )
        val rightKneeAngle = calculateKneeAngle(
            rightHip.position,
            rightKnee!!.position,
            rightAnkle.position
        )
        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2
        val areKneesStraight = avgKneeAngle > 150f

        // 자세 평가 및 상태 판단
        val formCheck = evaluateForm(bodyAngle, isElbowPositionCorrect, areKneesStraight)
        updateState(bodyAngle, isElbowPositionCorrect, areKneesStraight)

        // 타이머 업데이트
        val currentDuration = updateTimer()

        val feedback = generateFeedback(currentState, formCheck, bodyAngle)

        Log.d(TAG, "몸각도: ${bodyAngle.toInt()}°, 상태: $currentState, 시간: ${formatTime(currentDuration)}")

        return PlankResult(
            duration = currentDuration,
            totalDuration = totalPlankTime,
            bestDuration = bestTime,
            state = currentState,
            bodyAngle = bodyAngle,
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
     * 무릎 각도 계산
     */
    private fun calculateKneeAngle(
        hipPoint: PointF,
        kneePoint: PointF,
        anklePoint: PointF
    ): Float {
        return calculateAngle(hipPoint, kneePoint, anklePoint)
    }

    /**
     * 두 점 사이의 거리 계산
     */
    private fun calculateDistance(point1: PointF, point2: PointF): Float {
        val dx = point1.x - point2.x
        val dy = point1.y - point2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * 키포인트 유효성 검사
     */
    private fun areKeypointsValid(vararg landmarks: PoseLandmark?): Boolean {
        return landmarks.all { it != null && it.inFrameLikelihood > MIN_CONFIDENCE }
    }

    /**
     * 상태 업데이트
     */
    private fun updateState(bodyAngle: Float, isElbowPositionCorrect: Boolean, areKneesStraight: Boolean) {
        val previousState = currentState

        currentState = when {
            !isElbowPositionCorrect || !areKneesStraight -> PlankState.NOT_IN_POSITION
            bodyAngle < PERFECT_PLANK_ANGLE - PLANK_TOLERANCE -> PlankState.HIPS_TOO_HIGH
            bodyAngle > PERFECT_PLANK_ANGLE + PLANK_TOLERANCE -> PlankState.HIPS_TOO_LOW
            else -> PlankState.IN_POSITION
        }

        if (previousState != currentState) {
            Log.d(TAG, "상태 전이: $previousState -> $currentState")
        }
    }

    /**
     * 타이머 업데이트
     */
    private fun updateTimer(): Long {
        return when (currentState) {
            PlankState.IN_POSITION -> {
                if (!isTimerRunning) {
                    plankStartTime = System.currentTimeMillis()
                    isTimerRunning = true
                    Log.d(TAG, "플랭크 타이머 시작")
                }
                val currentDuration = System.currentTimeMillis() - plankStartTime

                // 최고 기록 업데이트
                if (currentDuration > bestTime) {
                    bestTime = currentDuration
                }

                currentDuration
            }
            else -> {
                if (isTimerRunning) {
                    val sessionDuration = System.currentTimeMillis() - plankStartTime
                    totalPlankTime += sessionDuration
                    isTimerRunning = false
                    Log.d(TAG, "플랭크 타이머 정지. 세션 시간: ${formatTime(sessionDuration)}")
                }
                0L
            }
        }
    }

    /**
     * 타이머 정지
     */
    private fun stopTimer() {
        if (isTimerRunning) {
            val sessionDuration = System.currentTimeMillis() - plankStartTime
            totalPlankTime += sessionDuration
            isTimerRunning = false
            Log.d(TAG, "플랭크 타이머 강제 정지")
        }
    }

    /**
     * 자세 평가
     */
    private fun evaluateForm(
        bodyAngle: Float,
        isElbowPositionCorrect: Boolean,
        areKneesStraight: Boolean
    ): FormCheck {
        val issues = mutableListOf<String>()

        if (!isElbowPositionCorrect) {
            issues.add("팔꿈치를 어깨 바로 아래에 두세요")
        }

        if (!areKneesStraight) {
            issues.add("무릎을 펴세요")
        }

        val bodyAlignmentDeviation = abs(PERFECT_PLANK_ANGLE - bodyAngle)
        if (bodyAlignmentDeviation > PLANK_TOLERANCE) {
            if (bodyAngle < PERFECT_PLANK_ANGLE) {
                issues.add("엉덩이를 조금 내리세요")
            } else {
                issues.add("엉덩이를 올리세요")
            }
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
    private fun generateFeedback(state: PlankState, formCheck: FormCheck, bodyAngle: Float): String {
        if (!formCheck.isGoodForm) {
            return formCheck.issues.firstOrNull() ?: "자세를 확인해주세요"
        }

        return when (state) {
            PlankState.IN_POSITION -> "완벽합니다! 자세를 유지하세요"
            PlankState.HIPS_TOO_HIGH -> "엉덩이를 조금 내리세요 (${bodyAngle.toInt()}°)"
            PlankState.HIPS_TOO_LOW -> "엉덩이를 올리세요 (${bodyAngle.toInt()}°)"
            PlankState.NOT_IN_POSITION -> "플랭크 자세를 취하세요"
        }
    }

    /**
     * 시간 포맷팅 (밀리초 -> MM:SS)
     */
    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 1000) / 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * 카운터 리셋
     */
    fun reset() {
        currentState = PlankState.NOT_IN_POSITION
        plankStartTime = 0
        totalPlankTime = 0
        isTimerRunning = false
        bestTime = 0
        Log.d(TAG, "플랭크 감지기 리셋")
    }

    /**
     * 총 플랭크 시간 반환
     */
    fun getTotalTime(): Long = totalPlankTime

    /**
     * 최고 기록 반환
     */
    fun getBestTime(): Long = bestTime

    /**
     * 자세 평가 결과
     */
    private data class FormCheck(
        val isGoodForm: Boolean,
        val issues: List<String>,
        val bodyAlignmentDeviation: Float
    )
}
