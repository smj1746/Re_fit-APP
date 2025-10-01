#!/usr/bin/env python3
"""
Re_fit 모델 학습을 위한 패키지 설치 스크립트
Python 3.13 호환성 문제를 해결하기 위한 대안 설치 방법
"""

import sys
import subprocess
import platform

def check_python_version():
    """Python 버전 확인"""
    version = sys.version_info
    print(f"Python 버전: {version.major}.{version.minor}.{version.micro}")
    
    if version.major == 3 and version.minor >= 13:
        print("⚠️  Python 3.13은 일부 패키지와 호환성 문제가 있습니다.")
        print("   Python 3.8-3.12 사용을 권장합니다.")
        return False
    elif version.major == 3 and version.minor >= 8:
        print("✅ Python 버전이 호환됩니다.")
        return True
    else:
        print("❌ Python 3.8 이상이 필요합니다.")
        return False

def install_packages():
    """패키지 설치"""
    packages = [
        # 핵심 패키지들
        "tensorflow>=2.10.0,<2.16.0",
        "tensorflow-hub>=0.12.0",
        "tensorflow-model-optimization>=0.7.0",
        "numpy>=1.21.0,<2.0.0",
        "pandas>=1.3.0,<3.0.0",
        "scikit-learn>=1.0.0,<1.4.0",
        "opencv-python>=4.5.0,<5.0.0",
        "Pillow>=8.3.0,<11.0.0",
        "matplotlib>=3.5.0,<3.8.0",
        "seaborn>=0.11.0,<0.13.0",
        "tqdm>=4.62.0,<5.0.0",
        "protobuf>=3.20.0,<4.0.0",
        "h5py>=3.1.0,<4.0.0"
    ]
    
    print("📦 패키지 설치를 시작합니다...")
    
    for package in packages:
        try:
            print(f"설치 중: {package}")
            subprocess.check_call([
                sys.executable, "-m", "pip", "install", package, "--upgrade"
            ])
            print(f"✅ {package} 설치 완료")
        except subprocess.CalledProcessError as e:
            print(f"❌ {package} 설치 실패: {e}")
            return False
    
    return True

def install_jupyter():
    """Jupyter Notebook 설치 (선택사항)"""
    try:
        print("📓 Jupyter Notebook 설치 중...")
        subprocess.check_call([
            sys.executable, "-m", "pip", "install", 
            "jupyter>=1.0.0,<2.0.0", "ipykernel>=6.0.0,<7.0.0"
        ])
        print("✅ Jupyter Notebook 설치 완료")
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ Jupyter Notebook 설치 실패: {e}")
        return False

def verify_installation():
    """설치 확인"""
    print("\n🔍 설치 확인 중...")
    
    try:
        import tensorflow as tf
        print(f"✅ TensorFlow {tf.__version__}")
        
        import tensorflow_hub as hub
        print(f"✅ TensorFlow Hub {hub.__version__}")
        
        import numpy as np
        print(f"✅ NumPy {np.__version__}")
        
        import cv2
        print(f"✅ OpenCV {cv2.__version__}")
        
        import matplotlib
        print(f"✅ Matplotlib {matplotlib.__version__}")
        
        import sklearn
        print(f"✅ Scikit-learn {sklearn.__version__}")
        
        return True
    except ImportError as e:
        print(f"❌ 패키지 임포트 실패: {e}")
        return False

def main():
    """메인 함수"""
    print("🚀 Re_fit 모델 학습 환경 설정")
    print("=" * 50)
    
    # Python 버전 확인
    if not check_python_version():
        print("\n💡 해결 방법:")
        print("1. Python 3.8-3.12 사용")
        print("2. 가상환경 생성: python -m venv refit_env")
        print("3. 가상환경 활성화:")
        if platform.system() == "Windows":
            print("   refit_env\\Scripts\\activate")
        else:
            print("   source refit_env/bin/activate")
        return False
    
    # 패키지 설치
    if not install_packages():
        print("\n❌ 패키지 설치에 실패했습니다.")
        return False
    
    # Jupyter 설치 (선택사항)
    install_jupyter()
    
    # 설치 확인
    if verify_installation():
        print("\n🎉 모든 패키지가 성공적으로 설치되었습니다!")
        print("\n📝 다음 단계:")
        print("1. python data_preprocessing.py  # 데이터 전처리")
        print("2. python train_model.py        # 모델 학습")
        print("3. jupyter notebook             # Jupyter 실행")
        return True
    else:
        print("\n❌ 설치 확인에 실패했습니다.")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
