# 📹 Re:fit 카메라 기능 테스트 가이드

실제 카메라 연동 및 포즈 인식 기능 확인 가이드

## ✅ 개선된 기능

### 1. **카메라 초기화 최적화**
- ✅ 해상도 명시: 640x480 (실시간 처리에 최적)
- ✅ PreviewView ScaleType: FILL_CENTER (화면 가득 채우기)
- ✅ 전면 카메라 우선, 없으면 후면 카메라 자동 전환
- ✅ 로딩 상태 표시 추가

### 2. **에러 처리 강화**
- ✅ 카메라 초기화 오류 감지
- ✅ 사용자에게 명확한 오류 메시지 표시
- ✅ "다시 시도" 버튼으로 재초기화 가능
- ✅ 상세한 로그 출력

### 3. **실시간 포즈 감지**
- ✅ ML Kit Pose Detection (STREAM_MODE)
- ✅ 17개 키포인트 실시간 추적
- ✅ 유효 키포인트 수 로깅
- ✅ 스켈레톤 오버레이 시각화

## 🚀 빌드 및 실행

### 1. 프로젝트 빌드
```bash
# Clean
.\gradlew.bat clean

# Debug 빌드
.\gradlew.bat assembleDebug
```

### 2. 에뮬레이터 실행
```bash
# 에뮬레이터 시작
%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Pixel_8 -camera-back webcam0 -camera-front webcam0

# 연결 확인
adb devices
```

**중요**: `-camera-back webcam0 -camera-front webcam0` 옵션으로 실제 웹캠을 에뮬레이터 카메라에 연결합니다.

### 3. 앱 설치 및 실행
```bash
# APK 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 앱 실행
adb shell am start -n com.example.myapplication/.MainActivity
```

### 4. 로그 확인 (실시간)
```bash
# 카메라 관련 로그만 필터링
adb logcat | findstr /C:"CameraScreen"

# 모든 Re:fit 로그
adb logcat | findstr /C:"Re_fit" /C:"CameraScreen" /C:"PoseClassifier" /C:"ExerciseCounter"
```

## 📊 기대되는 로그 출력

### ✅ 정상 작동 시
```
D/CameraScreen: PreviewView 생성 완료
D/CameraScreen: CameraProvider 획득 성공
D/CameraScreen: Preview 설정 완료 (640x480)
D/CameraScreen: ImageAnalysis 설정 완료
D/CameraScreen: 카메라 선택: 전면
D/CameraScreen: 카메라 바인딩 성공
D/CameraScreen: 카메라 초기화 성공
D/CameraScreen: 포즈 감지 성공 (유효 키포인트: 14 / 17)
```

### ⚠️ 오류 발생 시
```
E/CameraScreen: 카메라 초기화 실패: [오류 메시지]
E/CameraScreen: 포즈 감지 실패: [오류 메시지]
```

## 🧪 테스트 시나리오

### 1. 카메라 권한 테스트
1. 앱 실행
2. "카메라 권한이 필요합니다" 화면 확인
3. **"권한 요청"** 버튼 클릭
4. 시스템 권한 다이얼로그에서 **"허용"** 클릭
5. ✅ 카메라 프리뷰가 표시되어야 함

### 2. 카메라 초기화 테스트
1. 권한 허용 후 대기
2. "카메라 초기화 중..." 로딩 표시 확인
3. 2-3초 후 카메라 프리뷰 표시
4. ✅ 실제 카메라 영상이 보여야 함

### 3. 포즈 인식 테스트
1. 카메라 앞에 전신이 보이도록 위치
2. 팔과 다리를 움직여 보기
3. ✅ 초록색 스켈레톤 오버레이가 표시되어야 함
4. ✅ 키포인트(노란 점)들이 관절에 표시되어야 함

### 4. 스쿼트 카운팅 테스트
1. 운동 선택 화면에서 "스쿼트" 선택
2. 카메라 화면에서 스쿼트 동작 수행
   - 앉기 (무릎 구부리기)
   - 일어서기
3. ✅ "카운트: 1"로 증가해야 함
4. ✅ 상태가 "DOWN_PHASE" → "UP_PHASE"로 변경되어야 함

### 5. 타이머 테스트
1. 카메라 화면 진입
2. ✅ 상단에 타이머가 "00:00"부터 시작
3. ✅ 1초마다 증가 (00:01, 00:02, ...)
4. 나가기 버튼 클릭
5. ✅ 팝업에 경과 시간 표시

### 6. 나가기 버튼 테스트
1. 카메라 화면에서 우측 상단 빨간 X 버튼 클릭
2. ✅ 운동 완료 팝업 표시
3. ✅ 운동 시간 및 카운트 표시
4. **"저장"** 클릭
5. ✅ 홈 화면으로 이동

## 🐛 문제 해결

### 문제 1: 카메라 프리뷰가 검은 화면
**원인**: 에뮬레이터 카메라 설정 오류

**해결**:
```bash
# 에뮬레이터 재시작 (웹캠 연결)
adb kill-server
%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Pixel_8 -camera-back webcam0 -camera-front webcam0
```

### 문제 2: "카메라 초기화 실패" 오류
**원인**: CameraX 초기화 실패

**해결**:
1. 앱 재시작
2. 에뮬레이터 재시작
3. "다시 시도" 버튼 클릭
4. 로그 확인:
```bash
adb logcat | findstr /C:"CameraScreen"
```

### 문제 3: 포즈가 감지되지 않음
**원인**:
- 조명이 어두움
- 카메라가 전신을 보지 못함
- 키포인트 신뢰도 낮음

**해결**:
1. 밝은 곳에서 테스트
2. 카메라에서 2-3미터 떨어지기
3. 전신이 프레임에 들어오도록 조정
4. 로그에서 유효 키포인트 수 확인:
```
D/CameraScreen: 포즈 감지 성공 (유효 키포인트: 14 / 17)
```

### 문제 4: 스켈레톤이 보이지 않음
**원인**: PoseGraphic 렌더링 문제

**해결**:
1. 로그에서 포즈 감지 성공 확인
2. 최소 10개 이상의 키포인트가 감지되어야 함
3. 카메라 앞에서 움직여 보기

### 문제 5: 이미지가 왜곡됨
**원인**: PreviewView ScaleType 문제

**수정 완료**:
- ✅ ScaleType.FILL_CENTER로 설정됨
- ✅ 640x480 해상도로 고정됨

## 📱 실제 디바이스 테스트

### Android 디바이스 연결
```bash
# USB 디버깅 활성화 후
adb devices

# APK 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 앱 실행
adb shell am start -n com.example.myapplication/.MainActivity
```

### 실제 디바이스에서는:
- ✅ 더 빠른 카메라 초기화
- ✅ 더 정확한 포즈 감지
- ✅ 더 부드러운 프레임 처리

## 🎥 카메라 설정 정보

| 설정 | 값 |
|------|-----|
| 해상도 | 640x480 |
| 카메라 | 전면 (노트북 웹캠) |
| 프레임 전략 | KEEP_ONLY_LATEST |
| 출력 형식 | YUV_420_888 |
| ScaleType | FILL_CENTER |
| DetectorMode | STREAM_MODE (실시간) |

## 📊 성능 지표

| 항목 | 목표 | 실제 |
|------|------|------|
| 카메라 초기화 시간 | < 3초 | 로그 확인 |
| 포즈 감지 FPS | 15-30 fps | 로그 확인 |
| 유효 키포인트 | ≥ 10개 | 로그 확인 |
| 메모리 사용량 | < 200MB | Android Studio Profiler |

## 🔍 디버깅 팁

### 1. 실시간 로그 모니터링
```bash
# 필터링된 로그
adb logcat *:S CameraScreen:D PoseClassifier:D ExerciseCounter:D

# 로그 파일로 저장
adb logcat > refit_camera_test.log
```

### 2. 카메라 정보 확인
```bash
# 에뮬레이터 카메라 목록
adb shell dumpsys media.camera

# 카메라 권한 확인
adb shell dumpsys package com.example.myapplication | findstr permission
```

### 3. 스크린샷 캡처
```bash
# 현재 화면 캡처
adb shell screencap /sdcard/screenshot.png
adb pull /sdcard/screenshot.png
```

## ✅ 최종 체크리스트

### 빌드 전
- [ ] KSP 플러그인 설정 확인
- [ ] Gradle Sync 성공
- [ ] AndroidManifest.xml 카메라 권한 있음

### 실행 전
- [ ] 에뮬레이터 웹캠 옵션으로 실행
- [ ] adb devices로 연결 확인
- [ ] logcat 준비

### 테스트
- [ ] 카메라 권한 허용
- [ ] 카메라 프리뷰 표시 확인
- [ ] 포즈 스켈레톤 오버레이 확인
- [ ] 스쿼트 카운팅 작동 확인
- [ ] 타이머 작동 확인
- [ ] 나가기 및 저장 기능 확인

---

**모든 기능이 정상 작동하면 실제 카메라 연동 완료!** 🎉

문제가 있으면 로그를 확인하고, 이 가이드의 문제 해결 섹션을 참고하세요.
