# 푸시업 & 플랭크 감지 구현 계획

## 📋 개요
스쿼트 감지기(SquatDetector.kt)와 동일한 방식으로 ML Kit Pose Detection을 활용하여 푸시업과 플랭크 운동을 실시간으로 감지하는 기능을 추가합니다.

---

## 🎯 1. 푸시업 감지기 (PushUpDetector.kt)

### 1.1 사용할 키포인트
```kotlin
// ML Kit PoseLandmark 참조
- LEFT_SHOULDER (11)
- RIGHT_SHOULDER (12)
- LEFT_ELBOW (13)
- RIGHT_ELBOW (14)
- LEFT_WRIST (15)
- RIGHT_WRIST (16)
- LEFT_HIP (23)
- RIGHT_HIP (24)
- LEFT_ANKLE (27)
- RIGHT_ANKLE (28)
```

### 1.2 푸시업 상태 정의
```kotlin
enum class PushUpState {
    UP,          // 팔이 펴진 상태 (시작 자세)
    DESCENDING,  // 하강 중
    DOWN,        // 팔이 굽혀진 상태 (가슴이 바닥에 가까움)
    ASCENDING    // 상승 중
}
```

### 1.3 감지 로직

#### 각도 계산
```kotlin
// 팔꿈치 각도: 어깨-팔꿈치-손목
val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)

// 각도 임계값
private const val UP_ELBOW_ANGLE = 160f      // 팔 펴진 상태
private const val DOWN_ELBOW_ANGLE = 90f     // 팔 굽힌 상태
private const val ANGLE_TOLERANCE = 15f
```

#### 자세 정렬 체크
```kotlin
// 몸의 일직선 유지 확인: 어깨-엉덩이-발목
val bodyAlignment = calculateBodyAlignment(shoulder, hip, ankle)
private const val BODY_ALIGNMENT_THRESHOLD = 20f  // 각도 편차 허용 범위
```

#### 상태 전환 로직
```kotlin
when (currentState) {
    UP -> {
        if (elbowAngle < UP_ELBOW_ANGLE - ANGLE_TOLERANCE) {
            currentState = DESCENDING
        }
    }
    DESCENDING -> {
        if (elbowAngle <= DOWN_ELBOW_ANGLE + ANGLE_TOLERANCE) {
            currentState = DOWN
        }
    }
    DOWN -> {
        if (elbowAngle > DOWN_ELBOW_ANGLE + ANGLE_TOLERANCE) {
            currentState = ASCENDING
        }
    }
    ASCENDING -> {
        if (elbowAngle >= UP_ELBOW_ANGLE - ANGLE_TOLERANCE) {
            currentState = UP
            pushUpCount++  // 1회 완료
        }
    }
}
```

### 1.4 피드백 메시지
```kotlin
val feedback = when {
    !isGoodAlignment -> "몸을 일직선으로 유지하세요"
    leftElbowAngle < rightElbowAngle - 20 -> "왼팔을 더 굽히세요"
    rightElbowAngle < leftElbowAngle - 20 -> "오른팔을 더 굽히세요"
    currentState == DOWN -> "팔을 펴세요"
    currentState == UP -> "완벽합니다!"
    else -> "계속하세요"
}
```

### 1.5 데이터 클래스
```kotlin
data class PushUpResult(
    val count: Int,                    // 푸시업 카운트
    val state: PushUpState,            // 현재 상태
    val leftElbowAngle: Float,         // 왼팔 각도
    val rightElbowAngle: Float,        // 오른팔 각도
    val bodyAlignment: Float,          // 몸 정렬도
    val isGoodForm: Boolean,           // 자세가 올바른지
    val feedback: String               // 피드백 메시지
)
```

---

## 🧘 2. 플랭크 감지기 (PlankDetector.kt)

### 2.1 사용할 키포인트
```kotlin
// ML Kit PoseLandmark 참조
- LEFT_SHOULDER (11)
- RIGHT_SHOULDER (12)
- LEFT_ELBOW (13)
- RIGHT_ELBOW (14)
- LEFT_HIP (23)
- RIGHT_HIP (24)
- LEFT_KNEE (25)
- RIGHT_KNEE (26)
- LEFT_ANKLE (27)
- RIGHT_ANKLE (28)
```

### 2.2 플랭크 상태 정의
```kotlin
enum class PlankState {
    NOT_IN_POSITION,  // 플랭크 자세가 아님
    IN_POSITION,      // 플랭크 자세 유지 중
    HIPS_TOO_HIGH,    // 엉덩이가 너무 높음
    HIPS_TOO_LOW      // 엉덩이가 너무 낮음
}
```

### 2.3 감지 로직

#### 자세 정렬 체크
```kotlin
// 몸의 일직선 확인: 어깨-엉덩이-발목
val bodyAngle = calculateAngle(shoulder, hip, ankle)
private const val PERFECT_PLANK_ANGLE = 180f  // 완벽한 일직선
private const val PLANK_TOLERANCE = 15f       // 허용 오차
```

#### 팔꿈치 위치 확인
```kotlin
// 팔꿈치가 어깨 바로 아래에 있는지 확인
val elbowShoulderDistance = calculateDistance(elbow, shoulder)
val isElbowPositionCorrect = elbowShoulderDistance < ELBOW_THRESHOLD
```

#### 시간 측정
```kotlin
private var plankStartTime: Long = 0
private var totalPlankTime: Long = 0
private var isTimerRunning = false

fun updatePlankTime() {
    if (currentState == PlankState.IN_POSITION) {
        if (!isTimerRunning) {
            plankStartTime = System.currentTimeMillis()
            isTimerRunning = true
        }
        totalPlankTime = System.currentTimeMillis() - plankStartTime
    } else {
        isTimerRunning = false
    }
}
```

#### 상태 판단 로직
```kotlin
currentState = when {
    bodyAngle > PERFECT_PLANK_ANGLE + PLANK_TOLERANCE -> PlankState.HIPS_TOO_LOW
    bodyAngle < PERFECT_PLANK_ANGLE - PLANK_TOLERANCE -> PlankState.HIPS_TOO_HIGH
    !isElbowPositionCorrect -> PlankState.NOT_IN_POSITION
    else -> PlankState.IN_POSITION
}
```

### 2.4 피드백 메시지
```kotlin
val feedback = when (currentState) {
    PlankState.IN_POSITION -> "완벽합니다! 자세를 유지하세요"
    PlankState.HIPS_TOO_HIGH -> "엉덩이를 조금 내리세요"
    PlankState.HIPS_TOO_LOW -> "엉덩이를 올리세요"
    PlankState.NOT_IN_POSITION -> "플랭크 자세를 취하세요"
}
```

### 2.5 데이터 클래스
```kotlin
data class PlankResult(
    val duration: Long,                // 유지 시간 (밀리초)
    val state: PlankState,             // 현재 상태
    val bodyAngle: Float,              // 몸 각도
    val isGoodForm: Boolean,           // 자세가 올바른지
    val feedback: String               // 피드백 메시지
)
```

---

## 🎨 3. UI 통합 (CameraScreen.kt 수정)

### 3.1 운동 타입 선택 UI
```kotlin
@Composable
fun ExerciseTypeSelector(
    currentType: ExerciseType,
    onTypeSelected: (ExerciseType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ExerciseTypeButton(
            type = ExerciseType.SQUAT,
            isSelected = currentType == ExerciseType.SQUAT,
            onClick = { onTypeSelected(ExerciseType.SQUAT) }
        )
        ExerciseTypeButton(
            type = ExerciseType.PUSHUP,
            isSelected = currentType == ExerciseType.PUSHUP,
            onClick = { onTypeSelected(ExerciseType.PUSHUP) }
        )
        ExerciseTypeButton(
            type = ExerciseType.PLANK,
            isSelected = currentType == ExerciseType.PLANK,
            onClick = { onTypeSelected(ExerciseType.PLANK) }
        )
    }
}

@Composable
fun ExerciseTypeButton(
    type: ExerciseType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF4CAF50) else Color.Gray
        ),
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = when (type) {
                ExerciseType.SQUAT -> "스쿼트"
                ExerciseType.PUSHUP -> "푸시업"
                ExerciseType.PLANK -> "플랭크"
            }
        )
    }
}

enum class ExerciseType {
    SQUAT, PUSHUP, PLANK
}
```

### 3.2 감지기 통합
```kotlin
@Composable
fun CameraPreviewWithPoseDetection() {
    var selectedExerciseType by remember { mutableStateOf(ExerciseType.SQUAT) }

    // 각 운동별 감지기
    val squatDetector = remember { SquatDetector() }
    val pushUpDetector = remember { PushUpDetector() }
    val plankDetector = remember { PlankDetector() }

    // Pose 분석 콜백
    val onPoseDetected = { pose: Pose ->
        when (selectedExerciseType) {
            ExerciseType.SQUAT -> {
                squatResult = squatDetector.detectSquat(pose)
            }
            ExerciseType.PUSHUP -> {
                pushUpResult = pushUpDetector.detectPushUp(pose)
            }
            ExerciseType.PLANK -> {
                plankResult = plankDetector.detectPlank(pose)
            }
        }
    }

    Column {
        // 운동 타입 선택 UI
        ExerciseTypeSelector(
            currentType = selectedExerciseType,
            onTypeSelected = { selectedExerciseType = it }
        )

        // 카메라 프리뷰
        CameraPreview(onPoseDetected = onPoseDetected)

        // 운동별 오버레이
        when (selectedExerciseType) {
            ExerciseType.SQUAT -> SquatInfoOverlay(squatResult)
            ExerciseType.PUSHUP -> PushUpInfoOverlay(pushUpResult)
            ExerciseType.PLANK -> PlankInfoOverlay(plankResult)
        }
    }
}
```

### 3.3 푸시업 오버레이
```kotlin
@Composable
fun PushUpInfoOverlay(result: PushUpResult?) {
    result?.let {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 카운트
            Text(
                text = "${result.count}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                color = Color.White
            )

            // 상태
            Text(
                text = when (result.state) {
                    PushUpState.UP -> "준비"
                    PushUpState.DESCENDING -> "하강"
                    PushUpState.DOWN -> "최하단"
                    PushUpState.ASCENDING -> "상승"
                },
                color = Color(0xFF4CAF50)
            )

            // 팔 각도
            Row {
                Text("왼팔: ${result.leftElbowAngle.toInt()}°")
                Spacer(Modifier.width(16.dp))
                Text("오른팔: ${result.rightElbowAngle.toInt()}°")
            }

            // 피드백
            Text(
                text = result.feedback,
                color = if (result.isGoodForm) Color(0xFF4CAF50) else Color(0xFFFF5722)
            )
        }
    }
}
```

### 3.4 플랭크 오버레이
```kotlin
@Composable
fun PlankInfoOverlay(result: PlankResult?) {
    result?.let {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 타이머
            Text(
                text = formatPlankTime(result.duration),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                color = Color.White
            )

            // 상태
            Text(
                text = when (result.state) {
                    PlankState.IN_POSITION -> "완벽한 자세!"
                    PlankState.HIPS_TOO_HIGH -> "엉덩이 ↓"
                    PlankState.HIPS_TOO_LOW -> "엉덩이 ↑"
                    PlankState.NOT_IN_POSITION -> "자세 준비"
                },
                color = if (result.isGoodForm) Color(0xFF4CAF50) else Color(0xFFFF5722)
            )

            // 몸 각도
            Text("몸 각도: ${result.bodyAngle.toInt()}°")

            // 피드백
            Text(
                text = result.feedback,
                color = if (result.isGoodForm) Color(0xFF4CAF50) else Color(0xFFFF5722)
            )
        }
    }
}

fun formatPlankTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}
```

---

## 📁 4. 파일 구조
```
app/src/main/java/com/example/myapplication/
├── SquatDetector.kt           (기존)
├── PushUpDetector.kt          (신규 - 푸시업 감지)
├── PlankDetector.kt           (신규 - 플랭크 감지)
├── CameraScreen.kt            (수정 - UI 통합)
└── ExerciseCounter.kt         (기존 - TFLite 모델 사용)
```

---

## 🔧 5. 구현 명령어 및 순서

### 5.1 PushUpDetector.kt 생성
```bash
# 파일 생성
touch app/src/main/java/com/example/myapplication/PushUpDetector.kt

# 또는 Write 도구 사용하여 직접 작성
```

### 5.2 PlankDetector.kt 생성
```bash
# 파일 생성
touch app/src/main/java/com/example/myapplication/PlankDetector.kt

# 또는 Write 도구 사용하여 직접 작성
```

### 5.3 CameraScreen.kt 수정
```bash
# 기존 파일 읽기
Read: app/src/main/java/com/example/myapplication/CameraScreen.kt

# Edit 도구로 필요한 부분 수정
# 1. ExerciseType enum 추가
# 2. ExerciseTypeSelector UI 추가
# 3. 감지기 통합 로직 추가
# 4. 오버레이 UI 추가
```

### 5.4 빌드 및 테스트
```bash
# Gradle Sync
cmd.exe /c "gradlew.bat --refresh-dependencies"

# 클린 빌드
cmd.exe /c "gradlew.bat clean"

# APK 빌드
cmd.exe /c "gradlew.bat assembleDebug"

# 빌드 성공 여부 확인
cmd.exe /c "gradlew.bat assembleDebug --info 2>&1 | findstr /I /C:\"BUILD\" /C:\"SUCCESS\" /C:\"FAILED\""
```

### 5.5 Git 커밋
```bash
# 변경사항 스테이징
git add app/src/main/java/com/example/myapplication/PushUpDetector.kt
git add app/src/main/java/com/example/myapplication/PlankDetector.kt
git add app/src/main/java/com/example/myapplication/CameraScreen.kt

# 커밋
git commit -m "feat: 푸시업 및 플랭크 감지 기능 추가

ML Kit Pose Detection을 활용한 푸시업과 플랭크 운동 감지 기능 추가

주요 변경사항:
- PushUpDetector.kt: 팔꿈치 각도 기반 푸시업 감지
- PlankDetector.kt: 몸 정렬도 기반 플랭크 감지
- CameraScreen.kt: 운동 타입 선택 UI 및 멀티 운동 지원

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

# 푸시
git push origin mywork
```

---

## 📊 6. 테스트 계획

### 6.1 푸시업 테스트
- [ ] 팔을 완전히 펴면 UP 상태로 인식되는지
- [ ] 팔을 90도 굽히면 DOWN 상태로 인식되는지
- [ ] 1회 완료 시 카운트가 증가하는지
- [ ] 몸이 일직선이 아닐 때 피드백이 표시되는지
- [ ] 좌우 팔 각도 차이가 클 때 피드백이 표시되는지

### 6.2 플랭크 테스트
- [ ] 올바른 플랭크 자세에서 IN_POSITION 상태로 인식되는지
- [ ] 타이머가 정확하게 작동하는지
- [ ] 엉덩이가 높을 때 HIPS_TOO_HIGH 상태로 인식되는지
- [ ] 엉덩이가 낮을 때 HIPS_TOO_LOW 상태로 인식되는지
- [ ] 자세 교정 시 상태가 변경되는지

### 6.3 UI 테스트
- [ ] 운동 타입 전환이 정상적으로 작동하는지
- [ ] 각 운동별 오버레이가 올바르게 표시되는지
- [ ] 카운트/타이머가 실시간으로 업데이트되는지
- [ ] 피드백 메시지가 적절하게 표시되는지

---

## 🎯 7. 예상 개선 사항

### 7.1 성능 최적화
- 키포인트 신뢰도 낮을 때 예외 처리
- 프레임 드롭 방지를 위한 비동기 처리
- 메모리 누수 방지

### 7.2 사용자 경험 개선
- 음성 피드백 추가
- 진동 피드백 (햅틱)
- 운동 기록 저장 (Room Database)
- 통계 및 그래프 표시

### 7.3 정확도 향상
- 여러 프레임 평균으로 노이즈 제거
- 캘리브레이션 기능 추가
- 사용자별 임계값 조정

---

## ✅ 완료 체크리스트

### 코드 작성
- [ ] PushUpDetector.kt 구현 완료
- [ ] PlankDetector.kt 구현 완료
- [ ] CameraScreen.kt 수정 완료
- [ ] ExerciseType enum 추가
- [ ] UI 컴포넌트 추가

### 빌드 및 테스트
- [ ] Gradle Sync 성공
- [ ] APK 빌드 성공
- [ ] 실제 디바이스/에뮬레이터 테스트
- [ ] 각 운동별 감지 테스트

### Git
- [ ] 변경사항 커밋
- [ ] GitHub 푸시
- [ ] PR 생성 (필요 시)

### 문서
- [ ] 코드 주석 추가
- [ ] README 업데이트
- [ ] 구현 완료 문서 작성

---

**작성일**: 2026-03-06
**작성자**: Claude Code + User
**프로젝트**: Re:fit - 피트니스 자세 감지 앱
