# Re_fit 커스텀 포즈 분류 모델 학습 패키지

이 패키지는 Re_fit 안드로이드 앱을 위한 온디바이스 포즈 분류 모델을 학습하고 배포하기 위한 전체 파이프라인을 제공합니다.

에뮬레이터 확인: adb devices - Pixel 8 에뮬레이터 준비됨

앱 설치: adb install -r app-deb

앱 실행: adb shell am start -n com.example.myapplication/.MainActivity



## 🎯 주요 기능

- **MoveNet 기반 키포인트 추출**: 17개 관절 키포인트 자동 추출
- **BiLSTM 시퀀스 분류**: 16프레임 윈도우 기반 포즈 상태 분류
- **실시간 운동 감지**: 스쿼트, 푸시업, 플랭크의 정자세/오류자세 구분
- **TFLite 최적화**: 모바일 온디바이스 추론을 위한 양자화된 모델
- **반복 카운팅**: FSM 기반 운동 반복 횟수 자동 계산

## 📁 프로젝트 구조

```
refit_model_training/
├── data_preprocessing.py    # 데이터 전처리 및 키포인트 추출
├── model_design.py         # BiLSTM 모델 설계 및 구현
├── train_model.py          # 전체 학습 파이프라인
├── requirements.txt        # 필요한 패키지 목록
├── README.md              # 이 파일
└── outputs/               # 학습 결과 저장 디렉토리
    ├── models/            # Keras 모델 파일
    ├── tflite/           # TFLite 모델 및 메타데이터
    └── plots/            # 학습 그래프 및 혼동 행렬
```

## 🚀 빠른 시작

### 1. 환경 설정

```bash
# 가상환경 생성 (권장)
python -m venv refit_env
source refit_env/bin/activate  # Windows: refit_env\Scripts\activate

# 패키지 설치
pip install -r requirements.txt
```

### 2. 데이터 준비

AI Hub 피트니스 자세 이미지 데이터셋을 다운로드하고 다음과 같은 구조로 정리합니다:

```
data/
├── squat/
│   ├── good/          # 정자세 이미지
│   └── bad/           # 오류자세 이미지
├── pushup/
│   ├── good/
│   └── bad/
└── plank/
    ├── good/
    └── bad/
```

**참고**: AI Hub 데이터가 없는 경우, 스크립트가 자동으로 합성 데이터를 생성합니다.

### 3. 모델 학습 실행

```bash
# 전체 파이프라인 실행
python train_model.py
```

또는 단계별 실행:

```bash
# 1. 데이터 전처리만
python data_preprocessing.py

# 2. 모델 구축만
python model_design.py
```

## 📊 모델 아키텍처

### 입력
- **형태**: (16, 34) - 16프레임, 34개 정규화된 키포인트
- **전처리**: MoveNet → 골반 중심 정규화 → 어깨폭 스케일링

### 모델 구조
```
Input (16, 34)
    ↓
BiLSTM (64 units, return_sequences=True)
    ↓
BiLSTM (64 units)
    ↓
Dense (128 units, ReLU)
    ↓
Dropout (0.3)
    ↓
Dense (64 units, ReLU)
    ↓
Dropout (0.3)
    ↓
Dense (11 units, Softmax)  # 11개 클래스
```

### 출력 클래스
1. `squat_down` - 스쿼트 하단
2. `squat_up` - 스쿼트 상단
3. `squat_bad_knee` - 스쿼트 무릎 오류
4. `squat_bad_back` - 스쿼트 허리 오류
5. `pushup_down` - 푸시업 하단
6. `pushup_up` - 푸시업 상단
7. `pushup_bad_form` - 푸시업 자세 오류
8. `pushup_bad_arch` - 푸시업 허리 아치 오류
9. `plank_good` - 플랭크 정자세
10. `plank_arch` - 플랭크 허리 아치
11. `plank_sink` - 플랭크 허리 하강

## 🔧 주요 클래스 및 함수

### ReFitDataPreprocessor
- `extract_keypoints()`: MoveNet으로 키포인트 추출
- `normalize_keypoints()`: 키포인트 정규화
- `create_sequences()`: 시퀀스 데이터 생성
- `preprocess_data()`: 전체 전처리 파이프라인

### ReFitPoseClassifier
- `build_model()`: BiLSTM 모델 구축
- `train_model()`: 모델 학습
- `evaluate_model()`: 모델 평가
- `plot_training_history()`: 학습 과정 시각화

### ReFitModelOptimizer
- `convert_to_tflite()`: TFLite 모델 변환
- `test_tflite_model()`: TFLite 모델 테스트

## 📈 성능 최적화

### 모바일 최적화
- **양자화**: FP16 양자화로 모델 크기 50% 감소
- **Lightning MoveNet**: 실시간 추론을 위한 경량 모델
- **배치 처리**: 효율적인 메모리 사용

### 정확도 향상
- **데이터 증강**: 시퀀스 오버샘플링
- **정규화**: 키포인트 정규화로 일반화 성능 향상
- **앙상블**: 여러 모델의 예측 결합 (선택사항)

## 📱 Android 연동

학습된 TFLite 모델은 Android 앱에서 다음과 같이 사용할 수 있습니다:

```kotlin
// TFLite 인터프리터 로드
val interpreter = Interpreter(loadModelFile("refit_pose_classifier.tflite"))

// 키포인트 시퀀스로 추론
val input = Array(1) { Array(16) { FloatArray(34) } }
val output = Array(1) { FloatArray(11) }
interpreter.run(input, output)

// 결과 해석
val predictedClass = output[0].indices.maxByOrNull { output[0][it] } ?: 0
```

## 🐛 문제 해결

### 일반적인 문제들

1. **GPU 메모리 부족**
   ```python
   # GPU 메모리 증가 설정
   gpus = tf.config.experimental.list_physical_devices('GPU')
   tf.config.experimental.set_memory_growth(gpus[0], True)
   ```

2. **데이터 로딩 실패**
   - AI Hub 데이터 경로 확인
   - 이미지 파일 형식 확인 (JPG, PNG)

3. **TFLite 변환 실패**
   - TensorFlow 버전 호환성 확인
   - 모델 구조 단순화

## 📚 참고 자료

- [TensorFlow 공식 문서](https://www.tensorflow.org/?hl=ko)
- [TensorFlow Hub](https://www.tensorflow.org/hub?hl=ko)
- [AI Hub](https://www.aihub.or.kr/)
- [MoveNet 모델](https://tfhub.dev/google/movenet/singlepose/lightning/4)

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

## 🤝 기여하기

버그 리포트, 기능 요청, 풀 리퀘스트를 환영합니다!

---

**Re_fit 팀** - 건강한 운동을 위한 AI 기술

