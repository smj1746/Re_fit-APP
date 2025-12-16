package com.example.myapplication

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * ML Kit Pose Detection 결과를 캔버스에 그리는 유틸리티
 * 17개 키포인트와 연결선을 시각화합니다.
 */
object PoseGraphic {

    // 스켈레톤 연결 정의 (COCO 17 키포인트 기준)
    private val poseConnections = listOf(
        // 얼굴
        Pair(PoseLandmark.LEFT_EAR, PoseLandmark.LEFT_EYE),
        Pair(PoseLandmark.LEFT_EYE, PoseLandmark.NOSE),
        Pair(PoseLandmark.NOSE, PoseLandmark.RIGHT_EYE),
        Pair(PoseLandmark.RIGHT_EYE, PoseLandmark.RIGHT_EAR),

        // 몸통
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
        Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP),
        Pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),

        // 왼팔
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
        Pair(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),

        // 오른팔
        Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
        Pair(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),

        // 왼다리
        Pair(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
        Pair(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),

        // 오른다리
        Pair(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
        Pair(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
    )

    /**
     * Android Canvas를 사용한 포즈 그리기 (CameraX PreviewView 오버레이용)
     */
    fun drawPose(
        canvas: Canvas,
        pose: Pose,
        imageWidth: Float,
        imageHeight: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        val scaleX = canvasWidth / imageWidth
        val scaleY = canvasHeight / imageHeight

        // Paint 설정
        val linePaint = Paint().apply {
            color = Color.parseColor("#00FF00")  // 초록색
            strokeWidth = 8f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val pointPaint = Paint().apply {
            color = Color.parseColor("#FFFF00")  // 노란색
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val lowConfidencePaint = Paint().apply {
            color = Color.parseColor("#FF5555")  // 빨간색
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // 연결선 그리기
        poseConnections.forEach { (startType, endType) ->
            val startLandmark = pose.getPoseLandmark(startType)
            val endLandmark = pose.getPoseLandmark(endType)

            if (startLandmark != null && endLandmark != null) {
                val startX = startLandmark.position.x * scaleX
                val startY = startLandmark.position.y * scaleY
                val endX = endLandmark.position.x * scaleX
                val endY = endLandmark.position.y * scaleY

                // 신뢰도가 높은 경우에만 연결선 그리기
                if (startLandmark.inFrameLikelihood > 0.5f && endLandmark.inFrameLikelihood > 0.5f) {
                    canvas.drawLine(startX, startY, endX, endY, linePaint)
                }
            }
        }

        // 키포인트 그리기
        pose.allPoseLandmarks.forEach { landmark ->
            val x = landmark.position.x * scaleX
            val y = landmark.position.y * scaleY
            val confidence = landmark.inFrameLikelihood

            // 신뢰도에 따라 색상 변경
            val paint = if (confidence > 0.5f) pointPaint else lowConfidencePaint
            canvas.drawCircle(x, y, 12f, paint)
        }
    }

    /**
     * Jetpack Compose Canvas를 사용한 포즈 그리기
     */
    fun DrawScope.drawPoseCompose(
        pose: Pose,
        imageWidth: Float,
        imageHeight: Float
    ) {
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight

        // 연결선 그리기
        poseConnections.forEach { (startType, endType) ->
            val startLandmark = pose.getPoseLandmark(startType)
            val endLandmark = pose.getPoseLandmark(endType)

            if (startLandmark != null && endLandmark != null) {
                val startX = startLandmark.position.x * scaleX
                val startY = startLandmark.position.y * scaleY
                val endX = endLandmark.position.x * scaleX
                val endY = endLandmark.position.y * scaleY

                if (startLandmark.inFrameLikelihood > 0.5f && endLandmark.inFrameLikelihood > 0.5f) {
                    drawLine(
                        color = androidx.compose.ui.graphics.Color.Green,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 8f
                    )
                }
            }
        }

        // 키포인트 그리기
        pose.allPoseLandmarks.forEach { landmark ->
            val x = landmark.position.x * scaleX
            val y = landmark.position.y * scaleY
            val confidence = landmark.inFrameLikelihood

            val color = if (confidence > 0.5f) {
                androidx.compose.ui.graphics.Color.Yellow
            } else {
                androidx.compose.ui.graphics.Color.Red
            }

            drawCircle(
                color = color,
                radius = 12f,
                center = Offset(x, y)
            )
        }
    }

    /**
     * 키포인트 좌표 추출 (17개)
     */
    fun extractKeypoints(pose: Pose): Array<FloatArray> {
        val keypoints = Array(17) { FloatArray(3) }

        val landmarkMap = mapOf(
            0 to PoseLandmark.NOSE,
            1 to PoseLandmark.LEFT_EYE,
            2 to PoseLandmark.RIGHT_EYE,
            3 to PoseLandmark.LEFT_EAR,
            4 to PoseLandmark.RIGHT_EAR,
            5 to PoseLandmark.LEFT_SHOULDER,
            6 to PoseLandmark.RIGHT_SHOULDER,
            7 to PoseLandmark.LEFT_ELBOW,
            8 to PoseLandmark.RIGHT_ELBOW,
            9 to PoseLandmark.LEFT_WRIST,
            10 to PoseLandmark.RIGHT_WRIST,
            11 to PoseLandmark.LEFT_HIP,
            12 to PoseLandmark.RIGHT_HIP,
            13 to PoseLandmark.LEFT_KNEE,
            14 to PoseLandmark.RIGHT_KNEE,
            15 to PoseLandmark.LEFT_ANKLE,
            16 to PoseLandmark.RIGHT_ANKLE
        )

        landmarkMap.forEach { (index, landmarkType) ->
            val landmark = pose.getPoseLandmark(landmarkType)
            if (landmark != null) {
                keypoints[index][0] = landmark.position.x
                keypoints[index][1] = landmark.position.y
                keypoints[index][2] = landmark.inFrameLikelihood
            } else {
                keypoints[index][0] = 0f
                keypoints[index][1] = 0f
                keypoints[index][2] = 0f
            }
        }

        return keypoints
    }

    /**
     * 신뢰도가 높은 키포인트 개수 반환
     */
    fun countValidKeypoints(pose: Pose, threshold: Float = 0.5f): Int {
        return pose.allPoseLandmarks.count { it.inFrameLikelihood > threshold }
    }

    /**
     * 포즈가 유효한지 확인 (충분한 키포인트가 감지되었는지)
     */
    fun isPoseValid(pose: Pose, minKeypoints: Int = 10): Boolean {
        return countValidKeypoints(pose) >= minKeypoints
    }
}

/**
 * Composable: 포즈 오버레이 캔버스
 */
@Composable
fun PoseOverlay(
    pose: Pose?,
    imageWidth: Float,
    imageHeight: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        pose?.let {
            with(PoseGraphic) {
                drawPoseCompose(it, imageWidth, imageHeight)
            }
        }
    }
}
