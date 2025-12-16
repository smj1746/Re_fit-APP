package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 운동 기록 데이터 모델
 * Room Database Entity
 */
@Entity(tableName = "workout_records")
data class WorkoutRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val date: String,                    // "2024-12-16" 형식
    val exerciseType: String,            // "SQUAT", "PUSHUP", "PLANK"
    val count: Int,                      // 반복 횟수
    val duration: Long,                  // 운동 시간 (초)
    val timestamp: Long = System.currentTimeMillis(), // 기록 생성 시간
    val isNewRecord: Boolean = false     // 신기록 여부
)

/**
 * 날짜별 운동 통계
 */
data class DailyWorkoutSummary(
    val date: String,
    val totalDuration: Long,             // 총 운동 시간 (초)
    val workouts: List<WorkoutRecord>,   // 해당 날짜의 모든 운동
    val hasNewRecord: Boolean            // 신기록이 있는지
)

/**
 * 운동 타입별 통계
 */
data class ExerciseStats(
    val exerciseType: String,
    val totalCount: Int,                 // 총 횟수
    val maxCount: Int,                   // 최고 기록
    val averageCount: Int,               // 평균 횟수
    val workoutDays: Int                 // 운동한 날짜 수
)

/**
 * 운동 세션 정보 (실시간 운동 중)
 */
data class WorkoutSession(
    val exerciseType: String,
    val startTime: Long = System.currentTimeMillis(),
    var count: Int = 0,
    var duration: Long = 0
) {
    /**
     * 운동 세션을 WorkoutRecord로 변환
     */
    fun toWorkoutRecord(date: String, isNewRecord: Boolean = false): WorkoutRecord {
        return WorkoutRecord(
            date = date,
            exerciseType = exerciseType,
            count = count,
            duration = duration,
            isNewRecord = isNewRecord
        )
    }
}
