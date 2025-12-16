package com.example.myapplication.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 운동 기록 Repository
 * Database 접근 및 비즈니스 로직 처리
 */
class WorkoutRepository(context: Context) {

    private val workoutDao: WorkoutDao = WorkoutDatabase.getInstance(context).workoutDao()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        private const val TAG = "WorkoutRepository"
    }

    /**
     * 운동 기록 저장
     */
    suspend fun saveWorkout(workout: WorkoutRecord): Long {
        // 신기록 여부 확인
        val isNewRecord = checkIfNewRecord(workout.exerciseType, workout.count)
        val updatedWorkout = workout.copy(isNewRecord = isNewRecord)

        Log.d(TAG, "운동 기록 저장: $updatedWorkout")
        return workoutDao.insertWorkout(updatedWorkout)
    }

    /**
     * 신기록 여부 확인
     */
    private suspend fun checkIfNewRecord(exerciseType: String, count: Int): Boolean {
        val maxCount = workoutDao.getMaxCountByType(exerciseType) ?: 0
        return count > maxCount
    }

    /**
     * 모든 운동 기록 조회
     */
    fun getAllWorkouts(): Flow<List<WorkoutRecord>> {
        return workoutDao.getAllWorkouts()
    }

    /**
     * 특정 날짜의 운동 기록 조회
     */
    fun getWorkoutsByDate(date: String): Flow<List<WorkoutRecord>> {
        return workoutDao.getWorkoutsByDate(date)
    }

    /**
     * 날짜별 운동 요약 조회
     */
    fun getDailyWorkoutSummaries(): Flow<List<DailyWorkoutSummary>> {
        return workoutDao.getDistinctDates().map { dates ->
            dates.map { date ->
                val workouts = workoutDao.getWorkoutsByDate(date)
                val totalDuration = workoutDao.getTotalDurationByDate(date) ?: 0L

                DailyWorkoutSummary(
                    date = date,
                    totalDuration = totalDuration,
                    workouts = emptyList(), // Flow로 처리하므로 비워둠
                    hasNewRecord = false // 나중에 계산
                )
            }
        }
    }

    /**
     * 특정 운동 타입의 통계 조회
     */
    suspend fun getExerciseStats(exerciseType: String): ExerciseStats {
        val maxCount = workoutDao.getMaxCountByType(exerciseType) ?: 0
        // 추가 통계 계산 가능

        return ExerciseStats(
            exerciseType = exerciseType,
            totalCount = 0, // 계산 필요
            maxCount = maxCount,
            averageCount = 0, // 계산 필요
            workoutDays = 0 // 계산 필요
        )
    }

    /**
     * 연속 운동 일수 계산
     */
    suspend fun getConsecutiveDays(): Int {
        val dates = workoutDao.getRecentDistinctDates(limit = 30)
        if (dates.isEmpty()) return 0

        val today = LocalDate.now().format(dateFormatter)
        if (dates.first() != today) return 0

        var consecutiveDays = 1
        for (i in 0 until dates.size - 1) {
            val currentDate = LocalDate.parse(dates[i], dateFormatter)
            val nextDate = LocalDate.parse(dates[i + 1], dateFormatter)

            val daysBetween = ChronoUnit.DAYS.between(nextDate, currentDate)
            if (daysBetween == 1L) {
                consecutiveDays++
            } else {
                break
            }
        }

        Log.d(TAG, "연속 운동 일수: $consecutiveDays")
        return consecutiveDays
    }

    /**
     * 오늘 날짜 문자열 반환
     */
    fun getTodayDate(): String {
        return LocalDate.now().format(dateFormatter)
    }

    /**
     * 최근 N일간의 운동 기록 조회
     */
    fun getRecentWorkouts(days: Int = 30): Flow<List<WorkoutRecord>> {
        val startDate = LocalDate.now().minusDays(days.toLong()).format(dateFormatter)
        return workoutDao.getRecentWorkouts(startDate)
    }

    /**
     * 운동 기록 삭제
     */
    suspend fun deleteWorkout(workout: WorkoutRecord) {
        workoutDao.deleteWorkout(workout)
        Log.d(TAG, "운동 기록 삭제: ${workout.id}")
    }

    /**
     * 모든 운동 기록 삭제
     */
    suspend fun deleteAllWorkouts() {
        workoutDao.deleteAllWorkouts()
        Log.d(TAG, "모든 운동 기록 삭제")
    }
}
