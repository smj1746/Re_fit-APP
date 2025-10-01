package com.example.myapplication

import android.util.Log
import kotlin.math.*

/**
 * 운동 반복 카운팅을 위한 FSM (Finite State Machine) 클래스
 * 포즈 분류 결과를 기반으로 운동 반복 횟수를 계산합니다.
 */
class ExerciseCounter {
    
    companion object {
        private const val TAG = "ExerciseCounter"
        
        // 운동 타입 정의
        enum class ExerciseType {
            SQUAT, PUSHUP, PLANK
        }
        
        // FSM 상태 정의
        enum class State {
            IDLE,           // 대기 상태
            DOWN_PHASE,     // 하강 단계
            UP_PHASE,       // 상승 단계
            HOLD_PHASE      // 유지 단계 (플랭크용)
        }
    }
    
    private var currentExercise: ExerciseType = ExerciseType.SQUAT
    private var currentState: State = State.IDLE
    private var repetitionCount: Int = 0
    private var lastPose: String = ""
    private var stateConfidence: Float = 0.0f
    private var minConfidenceThreshold: Float = 0.7f
    
    // 상태 전이 카운터 (노이즈 방지)
    private var stateTransitionCounter: Int = 0
    private val requiredTransitions: Int = 3
    
    // 운동별 설정
    private val exerciseSettings = mapOf(
        ExerciseType.SQUAT to ExerciseSettings(
            downPoses = setOf("squat_down"),
            upPoses = setOf("squat_up"),
            badPoses = setOf("squat_bad_knee", "squat_bad_back")
        ),
        ExerciseType.PUSHUP to ExerciseSettings(
            downPoses = setOf("pushup_down"),
            upPoses = setOf("pushup_up"),
            badPoses = setOf("pushup_bad_form", "pushup_bad_arch")
        ),
        ExerciseType.PLANK to ExerciseSettings(
            downPoses = setOf("plank_good"),
            upPoses = setOf("plank_good"), // 플랭크는 유지가 목표
            badPoses = setOf("plank_arch", "plank_sink")
        )
    )
    
    data class ExerciseSettings(
        val downPoses: Set<String>,
        val upPoses: Set<String>,
        val badPoses: Set<String>
    )
    
    data class CounterResult(
        val count: Int,
        val state: State,
        val isGoodForm: Boolean,
        val feedback: String
    )
    
    /**
     * 운동 타입 설정
     */
    fun setExerciseType(exercise: ExerciseType) {
        if (currentExercise != exercise) {
            currentExercise = exercise
            reset()
            Log.d(TAG, "운동 타입 변경: $exercise")
        }
    }
    
    /**
     * 포즈 분류 결과 처리 및 카운팅
     * @param pose 분류된 포즈명
     * @param confidence 신뢰도
     * @return 카운팅 결과
     */
    fun processPose(pose: String, confidence: Float): CounterResult {
        // 신뢰도가 낮으면 이전 상태 유지
        if (confidence < minConfidenceThreshold) {
            return CounterResult(
                count = repetitionCount,
                state = currentState,
                isGoodForm = !isBadPose(pose),
                feedback = "자세를 명확히 해주세요"
            )
        }
        
        // 상태 전이 로직
        val newState = determineNextState(pose, confidence)
        
        // 상태가 변경되었는지 확인
        if (newState != currentState) {
            stateTransitionCounter++
            if (stateTransitionCounter >= requiredTransitions) {
                // 실제 상태 전이
                val previousState = currentState
                currentState = newState
                stateTransitionCounter = 0
                
                // 반복 카운팅 로직
                handleStateTransition(previousState, currentState, pose)
                
                Log.d(TAG, "상태 전이: $previousState -> $currentState")
            }
        } else {
            stateTransitionCounter = 0
        }
        
        lastPose = pose
        stateConfidence = confidence
        
        return CounterResult(
            count = repetitionCount,
            state = currentState,
            isGoodForm = !isBadPose(pose),
            feedback = generateFeedback(pose, confidence)
        )
    }
    
    /**
     * 다음 상태 결정
     */
    private fun determineNextState(pose: String, confidence: Float): State {
        val settings = exerciseSettings[currentExercise] ?: return State.IDLE
        
        return when (currentState) {
            State.IDLE -> {
                when {
                    settings.downPoses.contains(pose) -> State.DOWN_PHASE
                    settings.upPoses.contains(pose) -> State.UP_PHASE
                    settings.badPoses.contains(pose) -> State.IDLE
                    else -> State.IDLE
                }
            }
            State.DOWN_PHASE -> {
                when {
                    settings.upPoses.contains(pose) -> State.UP_PHASE
                    settings.downPoses.contains(pose) -> State.DOWN_PHASE
                    settings.badPoses.contains(pose) -> State.IDLE
                    else -> State.DOWN_PHASE
                }
            }
            State.UP_PHASE -> {
                when {
                    settings.downPoses.contains(pose) -> State.DOWN_PHASE
                    settings.upPoses.contains(pose) -> State.UP_PHASE
                    settings.badPoses.contains(pose) -> State.IDLE
                    else -> State.UP_PHASE
                }
            }
            State.HOLD_PHASE -> {
                when {
                    settings.downPoses.contains(pose) -> State.HOLD_PHASE
                    settings.badPoses.contains(pose) -> State.IDLE
                    else -> State.HOLD_PHASE
                }
            }
        }
    }
    
    /**
     * 상태 전이 처리 및 카운팅
     */
    private fun handleStateTransition(from: State, to: State, pose: String) {
        when (currentExercise) {
            ExerciseType.SQUAT, ExerciseType.PUSHUP -> {
                // 스쿼트, 푸시업: DOWN -> UP 전이 시 카운트 증가
                if (from == State.DOWN_PHASE && to == State.UP_PHASE) {
                    repetitionCount++
                    Log.d(TAG, "반복 카운트 증가: $repetitionCount")
                }
            }
            ExerciseType.PLANK -> {
                // 플랭크: 정자세 유지 시간 카운팅 (초 단위)
                if (to == State.HOLD_PHASE) {
                    // 플랭크는 별도의 시간 기반 카운팅 로직 필요
                    // 여기서는 간단히 상태 유지로 처리
                }
            }
        }
    }
    
    /**
     * 잘못된 자세인지 확인
     */
    private fun isBadPose(pose: String): Boolean {
        val settings = exerciseSettings[currentExercise] ?: return false
        return settings.badPoses.contains(pose)
    }
    
    /**
     * 피드백 메시지 생성
     */
    private fun generateFeedback(pose: String, confidence: Float): String {
        val settings = exerciseSettings[currentExercise] ?: return ""
        
        return when {
            isBadPose(pose) -> {
                when (pose) {
                    "squat_bad_knee" -> "무릎이 발끝보다 앞으로 나갔습니다"
                    "squat_bad_back" -> "허리를 곧게 펴주세요"
                    "pushup_bad_form" -> "몸을 일직선으로 유지하세요"
                    "pushup_bad_arch" -> "허리 아치를 줄여주세요"
                    "plank_arch" -> "허리를 곧게 펴주세요"
                    "plank_sink" -> "엉덩이를 올려주세요"
                    else -> "자세를 교정해주세요"
                }
            }
            settings.downPoses.contains(pose) -> {
                when (currentExercise) {
                    ExerciseType.SQUAT -> "스쿼트 하강 중..."
                    ExerciseType.PUSHUP -> "푸시업 하강 중..."
                    ExerciseType.PLANK -> "플랭크 유지 중..."
                }
            }
            settings.upPoses.contains(pose) -> {
                when (currentExercise) {
                    ExerciseType.SQUAT -> "스쿼트 상승 중..."
                    ExerciseType.PUSHUP -> "푸시업 상승 중..."
                    ExerciseType.PLANK -> "플랭크 유지 중..."
                }
            }
            else -> "자세를 취해주세요"
        }
    }
    
    /**
     * 카운터 리셋
     */
    fun reset() {
        repetitionCount = 0
        currentState = State.IDLE
        lastPose = ""
        stateConfidence = 0.0f
        stateTransitionCounter = 0
        Log.d(TAG, "카운터 리셋")
    }
    
    /**
     * 현재 카운트 반환
     */
    fun getCount(): Int = repetitionCount
    
    /**
     * 현재 상태 반환
     */
    fun getCurrentState(): State = currentState
    
    /**
     * 운동 타입 반환
     */
    fun getExerciseType(): ExerciseType = currentExercise
    
    /**
     * 신뢰도 임계값 설정
     */
    fun setConfidenceThreshold(threshold: Float) {
        minConfidenceThreshold = threshold.coerceIn(0.0f, 1.0f)
    }
}

