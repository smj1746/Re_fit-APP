"""
Re_fit 더미 TFLite 모델 생성 스크립트
실제 훈련 없이도 Android 앱이 작동하도록 하는 더미 모델을 생성합니다.
"""

import numpy as np
import tensorflow as tf
from pathlib import Path

def create_dummy_model():
    """더미 BiLSTM 모델 생성"""
    print("더미 Re_fit 모델 생성 중...")
    
    # 입력 형태: (batch_size, sequence_length, features)
    # 16프레임, 34개 키포인트 (x, y, confidence)
    input_shape = (None, 16, 34)
    num_classes = 11
    
    # 모델 정의
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(16, 34)),
        tf.keras.layers.Bidirectional(tf.keras.layers.LSTM(64, return_sequences=True)),
        tf.keras.layers.Bidirectional(tf.keras.layers.LSTM(64)),
        tf.keras.layers.Dense(128, activation='relu'),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(num_classes, activation='softmax')
    ])
    
    # 모델 컴파일
    model.compile(
        optimizer='adam',
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    
    # 더미 가중치 설정 (랜덤)
    print("더미 가중치 설정 중...")
    for layer in model.layers:
        if hasattr(layer, 'kernel_initializer'):
            # 랜덤 가중치로 초기화
            layer.kernel_initializer = tf.keras.initializers.RandomNormal(mean=0.0, stddev=0.1)
    
    # 모델 빌드
    dummy_input = np.random.randn(1, 16, 34).astype(np.float32)
    _ = model(dummy_input)
    
    print(f"모델 생성 완료!")
    print(f"총 파라미터 수: {model.count_params():,}")
    
    return model

def convert_to_tflite(model, output_path):
    """TFLite 모델로 변환"""
    print("TFLite 모델 변환 중...")
    
    # TFLite 변환기 설정
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # 최적화 설정
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]  # FP16 양자화
    
    # TFLite 모델 변환
    tflite_model = converter.convert()
    
    # 파일 저장
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    print(f"TFLite 모델 저장 완료: {output_path}")
    print(f"모델 크기: {len(tflite_model) / 1024:.1f} KB")
    
    return tflite_model

def test_tflite_model(tflite_path):
    """TFLite 모델 테스트"""
    print("TFLite 모델 테스트 중...")
    
    # TFLite 인터프리터 로드
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    
    # 입력/출력 텐서 정보
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"입력 형태: {input_details[0]['shape']}")
    print(f"출력 형태: {output_details[0]['shape']}")
    
    # 테스트 입력 생성
    test_input = np.random.randn(1, 16, 34).astype(np.float32)
    
    # 추론 실행
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()
    
    # 결과 출력
    output_data = interpreter.get_tensor(output_details[0]['index'])
    predicted_class = np.argmax(output_data[0])
    confidence = np.max(output_data[0])
    
    print(f"테스트 결과:")
    print(f"예측 클래스: {predicted_class}")
    print(f"신뢰도: {confidence:.4f}")
    
    return True

def main():
    """메인 함수"""
    print("=" * 60)
    print("Re_fit 더미 TFLite 모델 생성")
    print("=" * 60)
    
    # 출력 디렉토리 생성
    output_dir = Path("outputs")
    output_dir.mkdir(exist_ok=True)
    (output_dir / "tflite").mkdir(exist_ok=True)
    
    try:
        # 1. 더미 모델 생성
        model = create_dummy_model()
        
        # 2. TFLite 변환
        tflite_path = output_dir / "tflite" / "refit_pose_classifier.tflite"
        tflite_model = convert_to_tflite(model, tflite_path)
        
        # 3. TFLite 모델 테스트
        test_tflite_model(tflite_path)
        
        # 4. Android assets에 복사
        android_assets_path = Path("../app/src/main/assets/refit_pose_classifier.tflite")
        android_assets_path.parent.mkdir(parents=True, exist_ok=True)
        
        import shutil
        shutil.copy2(tflite_path, android_assets_path)
        print(f"Android assets에 복사 완료: {android_assets_path}")
        
        print("\n" + "=" * 60)
        print("더미 모델 생성 완료!")
        print("=" * 60)
        print(f"생성된 파일:")
        print(f"- TFLite 모델: {tflite_path}")
        print(f"- Android assets: {android_assets_path}")
        print(f"모델 크기: {len(tflite_model) / 1024:.1f} KB")
        
        return True
        
    except Exception as e:
        print(f"오류 발생: {e}")
        return False

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)

