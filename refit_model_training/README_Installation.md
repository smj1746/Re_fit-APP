# Re_fit 모델 학습 환경 설정 가이드

## 🚨 Python 3.13 호환성 문제 해결

Python 3.13은 일부 패키지와 호환성 문제가 있습니다. 다음 방법 중 하나를 선택하여 해결하세요.

## 방법 1: Python 3.12 사용 (권장)

### 1. Python 3.12 설치
- [Python 3.12 다운로드](https://www.python.org/downloads/release/python-3120/)
- 설치 시 "Add Python to PATH" 체크

### 2. 가상환경 생성
```bash
# 가상환경 생성
python3.12 -m venv refit_env

# 가상환경 활성화
# Windows:
refit_env\Scripts\activate
# macOS/Linux:
source refit_env/bin/activate
```

### 3. 패키지 설치
```bash
# 자동 설치 스크립트 실행
python install_requirements.py

# 또는 수동 설치
pip install -r requirements.txt
```

## 방법 2: Conda 사용

### 1. Miniconda 설치
- [Miniconda 다운로드](https://docs.conda.io/en/latest/miniconda.html)

### 2. 환경 생성 및 패키지 설치
```bash
# Python 3.11 환경 생성
conda create -n refit python=3.11

# 환경 활성화
conda activate refit

# 패키지 설치
pip install -r requirements.txt
```

## 방법 3: Docker 사용

### 1. Dockerfile 생성
```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

CMD ["python", "train_model.py"]
```

### 2. Docker 실행
```bash
# 이미지 빌드
docker build -t refit-model .

# 컨테이너 실행
docker run -it refit-model
```

## 방법 4: Google Colab 사용 (가장 간단)

### 1. Colab 노트북 열기
- `ReFit_Model_Training.ipynb` 파일을 Google Colab에서 열기

### 2. 런타임 설정
- 런타임 → 런타임 유형 변경 → Python 3.11 선택

### 3. 셀 실행
- 첫 번째 셀부터 순서대로 실행

## 🔧 문제 해결

### 1. TensorFlow 설치 실패
```bash
# CPU 버전 설치
pip install tensorflow-cpu>=2.10.0,<2.16.0

# GPU 버전 설치 (CUDA 지원)
pip install tensorflow>=2.10.0,<2.16.0
```

### 2. OpenCV 설치 실패
```bash
# 대안 설치
pip install opencv-python-headless>=4.5.0,<5.0.0
```

### 3. 메모리 부족
```bash
# 배치 크기 줄이기
# train_model.py에서 batch_size=16으로 변경
```

### 4. CUDA 오류
```bash
# CPU만 사용하도록 설정
export CUDA_VISIBLE_DEVICES=""
```

## 📋 시스템 요구사항

### 최소 요구사항
- Python 3.8-3.12
- RAM: 8GB 이상
- 저장공간: 5GB 이상

### 권장 사양
- Python 3.11
- RAM: 16GB 이상
- GPU: NVIDIA GPU (CUDA 지원)
- 저장공간: 10GB 이상

## 🚀 빠른 시작

### 1. 환경 설정
```bash
# Python 3.11 가상환경 생성
python3.11 -m venv refit_env
source refit_env/bin/activate  # Windows: refit_env\Scripts\activate

# 패키지 설치
python install_requirements.py
```

### 2. 모델 학습
```bash
# 데이터 전처리
python data_preprocessing.py

# 모델 학습
python train_model.py
```

### 3. Jupyter 실행
```bash
# Jupyter Notebook 실행
jupyter notebook

# 또는 JupyterLab 실행
jupyter lab
```

## 🆘 도움이 필요하신가요?

### 일반적인 문제들
1. **Python 버전 오류**: Python 3.8-3.12 사용
2. **패키지 충돌**: 가상환경 사용
3. **메모리 부족**: 배치 크기 줄이기
4. **CUDA 오류**: CPU 모드로 실행

### 추가 지원
- [TensorFlow 설치 가이드](https://www.tensorflow.org/install)
- [Python 가상환경 가이드](https://docs.python.org/3/tutorial/venv.html)
- [Google Colab 가이드](https://colab.research.google.com/)

---

**Re_fit 팀** - 건강한 운동을 위한 AI 기술
