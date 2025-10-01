package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.*

/**
 * Re_fit 포즈 분류 모델을 위한 TFLite 인터프리터 클래스
 * MoveNet 키포인트를 입력으로 받아 운동 상태를 분류합니다.
 */
class PoseClassifier(private val context: Context) {
    
    companion object {
        private const val TAG = "PoseClassifier"
        private const val MODEL_FILENAME = "refit_pose_classifier.tflite"
        private const val INPUT_SHAPE = 16 * 34 // 16프레임, 34개 키포인트 (x, y, confidence)
        private const val OUTPUT_CLASSES = 11
        private const val SEQUENCE_LENGTH = 16
        private const val KEYPOINT_DIM = 34
        
        // 클래스 레이블 정의
        val LABELS = arrayOf(
            "squat_down", "squat_up", "squat_bad_knee", "squat_bad_back",
            "pushup_down", "pushup_up", "pushup_bad_form", "pushup_bad_arch",
            "plank_good", "plank_arch", "plank_sink"
        )
    }
    
    private var interpreter: Interpreter? = null
    private val keypointBuffer = mutableListOf<FloatArray>()
    private var isModelLoaded = false
    
    init {
        loadModel()
    }
    
    /**
     * TFLite 모델 로드
     */
    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile(MODEL_FILENAME)
            val options = Interpreter.Options().apply {
                setNumThreads(4) // CPU 스레드 수 설정
            }
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            Log.d(TAG, "모델 로드 성공")
        } catch (e: Exception) {
            Log.e(TAG, "모델 로드 실패: ${e.message}")
            isModelLoaded = false
        }
    }
    
    /**
     * assets에서 모델 파일 로드
     */
    private fun loadModelFile(filename: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * MoveNet 키포인트를 정규화하여 버퍼에 추가
     * @param keypoints MoveNet에서 추출한 17개 키포인트 (x, y, confidence)
     * @return 정규화된 키포인트 배열 (34개 값: x, y, confidence 순서)
     */
    fun addKeypoints(keypoints: Array<FloatArray>): FloatArray? {
        if (keypoints.size != 17) {
            Log.w(TAG, "키포인트 개수가 올바르지 않습니다: ${keypoints.size}")
            return null
        }
        
        // 키포인트 정규화
        val normalizedKeypoints = normalizeKeypoints(keypoints)
        keypointBuffer.add(normalizedKeypoints)
        
        // 버퍼 크기 제한 (16프레임 유지)
        if (keypointBuffer.size > SEQUENCE_LENGTH) {
            keypointBuffer.removeAt(0)
        }
        
        // 충분한 프레임이 쌓이면 분류 실행
        return if (keypointBuffer.size >= SEQUENCE_LENGTH) {
            classifyPose()
        } else {
            null
        }
    }
    
    /**
     * 키포인트 정규화 (골반 중심, 어깨폭 스케일링)
     */
    private fun normalizeKeypoints(keypoints: Array<FloatArray>): FloatArray {
        val result = FloatArray(KEYPOINT_DIM)
        
        // x, y 좌표 추출
        val xy = Array(17) { i -> floatArrayOf(keypoints[i][0], keypoints[i][1]) }
        val confidence = FloatArray(17) { i -> keypoints[i][2] }
        
        // 골반 중심점 계산 (왼쪽/오른쪽 엉덩이의 중점)
        val pelvisX = (xy[11][0] + xy[12][0]) / 2.0f
        val pelvisY = (xy[11][1] + xy[12][1]) / 2.0f
        
        // 골반 중심으로 이동
        for (i in 0 until 17) {
            xy[i][0] -= pelvisX
            xy[i][1] -= pelvisY
        }
        
        // 어깨폭으로 스케일링
        val shoulderWidth = sqrt(
            (xy[5][0] - xy[6][0]).pow(2) + (xy[5][1] - xy[6][1]).pow(2)
        ) + 1e-6f
        
        for (i in 0 until 17) {
            xy[i][0] /= shoulderWidth
            xy[i][1] /= shoulderWidth
        }
        
        // 결과 배열에 저장 (x, y, confidence 순서)
        for (i in 0 until 17) {
            result[i * 3] = xy[i][0]     // x
            result[i * 3 + 1] = xy[i][1] // y
            result[i * 3 + 2] = confidence[i] // confidence
        }
        
        return result
    }
    
    /**
     * 포즈 분류 실행
     * @return 분류 결과 (클래스 인덱스, 신뢰도, 클래스명)
     */
    private fun classifyPose(): FloatArray? {
        if (!isModelLoaded || keypointBuffer.size < SEQUENCE_LENGTH) {
            return null
        }
        
        try {
            // 입력 데이터 준비 (1, 16, 34)
            val input = Array(1) { Array(SEQUENCE_LENGTH) { FloatArray(KEYPOINT_DIM) } }
            
            for (i in 0 until SEQUENCE_LENGTH) {
                val frameIndex = keypointBuffer.size - SEQUENCE_LENGTH + i
                if (frameIndex >= 0) {
                    keypointBuffer[frameIndex].copyInto(input[0][i])
                }
            }
            
            // 출력 배열 준비
            val output = Array(1) { FloatArray(OUTPUT_CLASSES) }
            
            // 추론 실행
            interpreter?.run(input, output)
            
            // 결과 분석
            val probabilities = output[0]
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
            val confidence = probabilities[maxIndex]
            
            Log.d(TAG, "분류 결과: ${LABELS[maxIndex]} (신뢰도: ${String.format("%.1f", confidence * 100)}%)")
            
            return floatArrayOf(maxIndex.toFloat(), confidence)
            
        } catch (e: Exception) {
            Log.e(TAG, "분류 실행 실패: ${e.message}")
            return null
        }
    }
    
    /**
     * 현재 운동 상태 반환
     * @return 운동 상태 정보 (클래스명, 신뢰도)
     */
    fun getCurrentPose(): Pair<String, Float>? {
        if (keypointBuffer.size < SEQUENCE_LENGTH) {
            return null
        }
        
        val result = classifyPose()
        return if (result != null) {
            val classIndex = result[0].toInt()
            val confidence = result[1]
            Pair(LABELS[classIndex], confidence)
        } else {
            null
        }
    }
    
    /**
     * 버퍼 초기화
     */
    fun clearBuffer() {
        keypointBuffer.clear()
    }
    
    /**
     * 모델 해제
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }
    
    /**
     * 모델 로드 상태 확인
     */
    fun isModelReady(): Boolean = isModelLoaded
}

