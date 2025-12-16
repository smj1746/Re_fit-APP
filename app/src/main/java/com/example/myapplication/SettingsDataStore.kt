package com.example.myapplication

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "refit_settings")

/**
 * Re:fit 앱의 모든 설정을 관리하는 DataStore 클래스
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        // 기본 설정 키
        val EXERCISE_DURATION = intPreferencesKey("exercise_duration")
        val REST_DURATION = intPreferencesKey("rest_duration")
        val COUNTING_SPEED = stringPreferencesKey("counting_speed")
        val ACCURACY_THRESHOLD = floatPreferencesKey("accuracy_threshold")

        // 피드백 설정 키
        val VOICE_GUIDANCE = booleanPreferencesKey("voice_guidance")
        val VIBRATION_FEEDBACK = booleanPreferencesKey("vibration_feedback")
        val REALTIME_CORRECTION = booleanPreferencesKey("realtime_correction")
        val COMPLETION_NOTIFICATION = booleanPreferencesKey("completion_notification")

        // 화면 표시 설정 키
        val SHOW_SKELETON = booleanPreferencesKey("show_skeleton")
        val SHOW_KEYPOINTS = booleanPreferencesKey("show_keypoints")
        val SHOW_CONFIDENCE = booleanPreferencesKey("show_confidence")
        val COUNTER_SIZE = stringPreferencesKey("counter_size")

        // 개인 설정 키
        val USER_HEIGHT = intPreferencesKey("user_height")
        val USER_WEIGHT = intPreferencesKey("user_weight")
        val EXERCISE_LEVEL = stringPreferencesKey("exercise_level")
        val JOINT_RESTRICTIONS = stringSetPreferencesKey("joint_restrictions")
        val PREFERRED_BODY_PARTS = stringSetPreferencesKey("preferred_body_parts")

        // 목표 설정 키
        val WEEKLY_GOAL_COUNT = intPreferencesKey("weekly_goal_count")
        val WEEKLY_GOAL_TIME = intPreferencesKey("weekly_goal_time")
        val PRIORITY = stringPreferencesKey("priority")
        val HEALTH_GOAL = stringPreferencesKey("health_goal")

        // 기본값
        const val DEFAULT_EXERCISE_DURATION = 60 // 1분
        const val DEFAULT_REST_DURATION = 30 // 30초
        const val DEFAULT_COUNTING_SPEED = "보통"
        const val DEFAULT_ACCURACY_THRESHOLD = 0.85f // 85%
        const val DEFAULT_COUNTER_SIZE = "보통"
        const val DEFAULT_EXERCISE_LEVEL = "초보자"
        const val DEFAULT_PRIORITY = "정확도"
        const val DEFAULT_HEALTH_GOAL = "근력향상"
    }

    // 기본 설정 Flow
    val exerciseDuration: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[EXERCISE_DURATION] ?: DEFAULT_EXERCISE_DURATION
    }

    val restDuration: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[REST_DURATION] ?: DEFAULT_REST_DURATION
    }

    val countingSpeed: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[COUNTING_SPEED] ?: DEFAULT_COUNTING_SPEED
    }

    val accuracyThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[ACCURACY_THRESHOLD] ?: DEFAULT_ACCURACY_THRESHOLD
    }

    // 피드백 설정 Flow
    val voiceGuidance: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[VOICE_GUIDANCE] ?: true
    }

    val vibrationFeedback: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[VIBRATION_FEEDBACK] ?: true
    }

    val realtimeCorrection: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[REALTIME_CORRECTION] ?: true
    }

    val completionNotification: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[COMPLETION_NOTIFICATION] ?: true
    }

    // 화면 표시 설정 Flow
    val showSkeleton: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHOW_SKELETON] ?: true
    }

    val showKeypoints: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHOW_KEYPOINTS] ?: false
    }

    val showConfidence: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHOW_CONFIDENCE] ?: true
    }

    val counterSize: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[COUNTER_SIZE] ?: DEFAULT_COUNTER_SIZE
    }

    // 개인 설정 Flow
    val userHeight: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[USER_HEIGHT] ?: 170
    }

    val userWeight: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[USER_WEIGHT] ?: 70
    }

    val exerciseLevel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[EXERCISE_LEVEL] ?: DEFAULT_EXERCISE_LEVEL
    }

    val jointRestrictions: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[JOINT_RESTRICTIONS] ?: emptySet()
    }

    val preferredBodyParts: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[PREFERRED_BODY_PARTS] ?: emptySet()
    }

    // 목표 설정 Flow
    val weeklyGoalCount: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[WEEKLY_GOAL_COUNT] ?: 3
    }

    val weeklyGoalTime: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[WEEKLY_GOAL_TIME] ?: 150
    }

    val priority: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PRIORITY] ?: DEFAULT_PRIORITY
    }

    val healthGoal: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[HEALTH_GOAL] ?: DEFAULT_HEALTH_GOAL
    }

    // 설정 저장 함수들
    suspend fun setExerciseDuration(value: Int) {
        context.dataStore.edit { prefs -> prefs[EXERCISE_DURATION] = value }
    }

    suspend fun setRestDuration(value: Int) {
        context.dataStore.edit { prefs -> prefs[REST_DURATION] = value }
    }

    suspend fun setCountingSpeed(value: String) {
        context.dataStore.edit { prefs -> prefs[COUNTING_SPEED] = value }
    }

    suspend fun setAccuracyThreshold(value: Float) {
        context.dataStore.edit { prefs -> prefs[ACCURACY_THRESHOLD] = value }
    }

    suspend fun setVoiceGuidance(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[VOICE_GUIDANCE] = value }
    }

    suspend fun setVibrationFeedback(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[VIBRATION_FEEDBACK] = value }
    }

    suspend fun setRealtimeCorrection(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[REALTIME_CORRECTION] = value }
    }

    suspend fun setCompletionNotification(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[COMPLETION_NOTIFICATION] = value }
    }

    suspend fun setShowSkeleton(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[SHOW_SKELETON] = value }
    }

    suspend fun setShowKeypoints(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[SHOW_KEYPOINTS] = value }
    }

    suspend fun setShowConfidence(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[SHOW_CONFIDENCE] = value }
    }

    suspend fun setCounterSize(value: String) {
        context.dataStore.edit { prefs -> prefs[COUNTER_SIZE] = value }
    }

    suspend fun setUserHeight(value: Int) {
        context.dataStore.edit { prefs -> prefs[USER_HEIGHT] = value }
    }

    suspend fun setUserWeight(value: Int) {
        context.dataStore.edit { prefs -> prefs[USER_WEIGHT] = value }
    }

    suspend fun setExerciseLevel(value: String) {
        context.dataStore.edit { prefs -> prefs[EXERCISE_LEVEL] = value }
    }

    suspend fun setJointRestrictions(value: Set<String>) {
        context.dataStore.edit { prefs -> prefs[JOINT_RESTRICTIONS] = value }
    }

    suspend fun setPreferredBodyParts(value: Set<String>) {
        context.dataStore.edit { prefs -> prefs[PREFERRED_BODY_PARTS] = value }
    }

    suspend fun setWeeklyGoalCount(value: Int) {
        context.dataStore.edit { prefs -> prefs[WEEKLY_GOAL_COUNT] = value }
    }

    suspend fun setWeeklyGoalTime(value: Int) {
        context.dataStore.edit { prefs -> prefs[WEEKLY_GOAL_TIME] = value }
    }

    suspend fun setPriority(value: String) {
        context.dataStore.edit { prefs -> prefs[PRIORITY] = value }
    }

    suspend fun setHealthGoal(value: String) {
        context.dataStore.edit { prefs -> prefs[HEALTH_GOAL] = value }
    }
}
