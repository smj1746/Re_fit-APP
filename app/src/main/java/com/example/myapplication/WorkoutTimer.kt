package com.example.myapplication

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 운동 시간 측정 타이머
 * 실시간으로 경과 시간을 추적합니다.
 */
class WorkoutTimer {

    companion object {
        private const val TAG = "WorkoutTimer"
    }

    private var timerJob: Job? = null
    private var startTime: Long = 0
    private var pausedTime: Long = 0
    private var isPaused = false

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    private val _formattedTime = MutableStateFlow("00:00")
    val formattedTime: StateFlow<String> = _formattedTime.asStateFlow()

    /**
     * 타이머 시작
     */
    fun start() {
        if (timerJob?.isActive == true) {
            Log.w(TAG, "타이머가 이미 실행 중입니다")
            return
        }

        startTime = System.currentTimeMillis()
        isPaused = false

        timerJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                if (!isPaused) {
                    val elapsed = (System.currentTimeMillis() - startTime + pausedTime) / 1000
                    _elapsedTime.value = elapsed
                    _formattedTime.value = formatTime(elapsed)
                }
                delay(1000) // 1초마다 업데이트
            }
        }

        Log.d(TAG, "타이머 시작")
    }

    /**
     * 타이머 일시정지
     */
    fun pause() {
        if (isPaused) return

        pausedTime = _elapsedTime.value * 1000
        isPaused = true
        Log.d(TAG, "타이머 일시정지: ${_formattedTime.value}")
    }

    /**
     * 타이머 재개
     */
    fun resume() {
        if (!isPaused) return

        startTime = System.currentTimeMillis()
        isPaused = false
        Log.d(TAG, "타이머 재개")
    }

    /**
     * 타이머 정지 및 리셋
     */
    fun stop() {
        timerJob?.cancel()
        timerJob = null

        val finalTime = _elapsedTime.value
        val finalFormatted = _formattedTime.value

        _elapsedTime.value = 0
        _formattedTime.value = "00:00"
        startTime = 0
        pausedTime = 0
        isPaused = false

        Log.d(TAG, "타이머 정지: $finalFormatted ($finalTime 초)")
    }

    /**
     * 현재 경과 시간 (초)
     */
    fun getElapsedSeconds(): Long {
        return _elapsedTime.value
    }

    /**
     * 타이머 실행 중 여부
     */
    fun isRunning(): Boolean {
        return timerJob?.isActive == true && !isPaused
    }

    /**
     * 시간 포맷팅 (MM:SS 또는 HH:MM:SS)
     */
    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    /**
     * 리소스 정리
     */
    fun cleanup() {
        timerJob?.cancel()
        timerJob = null
        Log.d(TAG, "타이머 정리 완료")
    }
}
