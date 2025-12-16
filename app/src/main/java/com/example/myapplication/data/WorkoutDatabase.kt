package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Re:fit 운동 기록 Database
 */
@Database(
    entities = [WorkoutRecord::class],
    version = 1,
    exportSchema = false
)
abstract class WorkoutDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: WorkoutDatabase? = null

        private const val DATABASE_NAME = "refit_workout_database"

        /**
         * Database 싱글톤 인스턴스 가져오기
         */
        fun getInstance(context: Context): WorkoutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorkoutDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // 버전 변경 시 데이터 삭제 후 재생성
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
