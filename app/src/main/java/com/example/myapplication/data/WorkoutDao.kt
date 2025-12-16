package com.example.myapplication.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 운동 기록 Database Access Object
 */
@Dao
interface WorkoutDao {

    /**
     * 운동 기록 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutRecord): Long

    /**
     * 여러 운동 기록 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkouts(workouts: List<WorkoutRecord>)

    /**
     * 운동 기록 업데이트
     */
    @Update
    suspend fun updateWorkout(workout: WorkoutRecord)

    /**
     * 운동 기록 삭제
     */
    @Delete
    suspend fun deleteWorkout(workout: WorkoutRecord)

    /**
     * 모든 운동 기록 조회 (최신순)
     */
    @Query("SELECT * FROM workout_records ORDER BY timestamp DESC")
    fun getAllWorkouts(): Flow<List<WorkoutRecord>>

    /**
     * 특정 날짜의 운동 기록 조회
     */
    @Query("SELECT * FROM workout_records WHERE date = :date ORDER BY timestamp DESC")
    fun getWorkoutsByDate(date: String): Flow<List<WorkoutRecord>>

    /**
     * 특정 기간의 운동 기록 조회
     */
    @Query("SELECT * FROM workout_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC, timestamp DESC")
    fun getWorkoutsByDateRange(startDate: String, endDate: String): Flow<List<WorkoutRecord>>

    /**
     * 특정 운동 타입의 최고 기록 조회
     */
    @Query("SELECT MAX(count) FROM workout_records WHERE exerciseType = :exerciseType")
    suspend fun getMaxCountByType(exerciseType: String): Int?

    /**
     * 특정 운동 타입의 모든 기록 조회
     */
    @Query("SELECT * FROM workout_records WHERE exerciseType = :exerciseType ORDER BY timestamp DESC")
    fun getWorkoutsByType(exerciseType: String): Flow<List<WorkoutRecord>>

    /**
     * 특정 날짜의 총 운동 시간 조회
     */
    @Query("SELECT SUM(duration) FROM workout_records WHERE date = :date")
    suspend fun getTotalDurationByDate(date: String): Long?

    /**
     * 운동한 날짜 목록 조회 (중복 제거)
     */
    @Query("SELECT DISTINCT date FROM workout_records ORDER BY date DESC")
    fun getDistinctDates(): Flow<List<String>>

    /**
     * 최근 N일간의 운동 기록 조회
     */
    @Query("SELECT * FROM workout_records WHERE date >= :startDate ORDER BY date DESC, timestamp DESC")
    fun getRecentWorkouts(startDate: String): Flow<List<WorkoutRecord>>

    /**
     * 모든 운동 기록 삭제
     */
    @Query("DELETE FROM workout_records")
    suspend fun deleteAllWorkouts()

    /**
     * 연속 운동 일수 계산용 날짜 목록
     */
    @Query("SELECT DISTINCT date FROM workout_records ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentDistinctDates(limit: Int = 30): List<String>
}
