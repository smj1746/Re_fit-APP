"""
Re_fit 커스텀 포즈 분류 모델 - 전체 학습 파이프라인
데이터 전처리부터 모델 학습, 평가, TFLite 변환까지
"""

import os
import json
import numpy as np
import tensorflow as tf
from pathlib import Path
import matplotlib.pyplot as plt
from sklearn.metrics import classification_report
import warnings
warnings.filterwarnings('ignore')

# 로컬 모듈 임포트
from data_preprocessing import ReFitDataPreprocessor
from model_design import ReFitPoseClassifier, ReFitModelOptimizer

class ReFitTrainingPipeline:
    def __init__(self, data_dir="data", output_dir="outputs"):
        self.data_dir = Path(data_dir)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)
        
        # 하위 디렉토리 생성
        (self.output_dir / "models").mkdir(exist_ok=True)
        (self.output_dir / "plots").mkdir(exist_ok=True)
        (self.output_dir / "tflite").mkdir(exist_ok=True)
        
        self.preprocessor = None
        self.classifier = None
        self.label_mapping = None
        
    def run_full_pipeline(self):
        """전체 학습 파이프라인 실행"""
        print("=" * 60)
        print("Re_fit 커스텀 포즈 분류 모델 학습 파이프라인 시작")
        print("=" * 60)
        
        # 1. 데이터 전처리
        print("\n1. 데이터 전처리 단계")
        print("-" * 30)
        self.preprocess_data()
        
        # 2. 모델 구축
        print("\n2. 모델 구축 단계")
        print("-" * 30)
        self.build_model()
        
        # 3. 모델 학습
        print("\n3. 모델 학습 단계")
        print("-" * 30)
        self.train_model()
        
        # 4. 모델 평가
        print("\n4. 모델 평가 단계")
        print("-" * 30)
        self.evaluate_model()
        
        # 5. TFLite 변환
        print("\n5. TFLite 변환 단계")
        print("-" * 30)
        self.convert_to_tflite()
        
        # 6. 결과 요약
        print("\n6. 학습 결과 요약")
        print("-" * 30)
        self.summarize_results()
        
        print("\n" + "=" * 60)
        print("Re_fit 모델 학습 파이프라인 완료!")
        print("=" * 60)
    
    def preprocess_data(self):
        """데이터 전처리"""
        self.preprocessor = ReFitDataPreprocessor(
            data_dir=self.data_dir,
            output_dir=self.output_dir / "processed_data"
        )
        
        # 데이터 전처리 실행
        X_train, X_val, X_test, y_train, y_val, y_test, self.label_mapping = \
            self.preprocessor.preprocess_data()
        
        # 데이터 저장
        self.X_train, self.X_val, self.X_test = X_train, X_val, X_test
        self.y_train, self.y_val, self.y_test = y_train, y_val, y_test
        
        print(f"데이터 전처리 완료!")
        print(f"학습 데이터: {X_train.shape}")
        print(f"검증 데이터: {X_val.shape}")
        print(f"테스트 데이터: {X_test.shape}")
        print(f"클래스 수: {len(self.label_mapping)}")
        
    def build_model(self):
        """모델 구축"""
        # 입력 형태 계산 (16프레임 * 34개 키포인트)
        input_shape = (16, 34)
        num_classes = len(self.label_mapping)
        
        self.classifier = ReFitPoseClassifier(
            input_shape=input_shape,
            num_classes=num_classes,
            model_name="refit_pose_classifier"
        )
        
        # 모델 구축
        model = self.classifier.build_model(
            lstm_units=64,
            dense_units=128,
            dropout_rate=0.3
        )
        
        # 모델 컴파일
        self.classifier.compile_model(
            learning_rate=1e-3,
            optimizer='adam'
        )
        
        print("모델 구축 완료!")
        print(f"총 파라미터 수: {model.count_params():,}")
        
    def train_model(self):
        """모델 학습"""
        # 학습 실행
        history = self.classifier.train_model(
            X_train=self.X_train,
            y_train=self.y_train,
            X_val=self.X_val,
            y_val=self.y_val,
            epochs=50,
            batch_size=32
        )
        
        # 학습 그래프 저장
        self.classifier.plot_training_history(
            save_path=self.output_dir / "plots" / "training_history.png"
        )
        
        # 최고 모델 저장
        self.classifier.save_model(
            self.output_dir / "models" / "refit_pose_classifier.h5"
        )
        
        print("모델 학습 완료!")
        
    def evaluate_model(self):
        """모델 평가"""
        # 테스트 데이터로 평가
        results = self.classifier.evaluate_model(
            X_test=self.X_test,
            y_test=self.y_test,
            label_names=list(self.label_mapping.values())
        )
        
        # 혼동 행렬 저장
        self.classifier.plot_confusion_matrix(
            y_true=self.y_test,
            y_pred=results['predicted_classes'],
            label_names=list(self.label_mapping.values()),
            save_path=self.output_dir / "plots" / "confusion_matrix.png"
        )
        
        # 평가 결과 저장
        evaluation_results = {
            'test_accuracy': float(results['test_accuracy']),
            'test_top_k_accuracy': float(results['test_top_k_accuracy']),
            'num_classes': len(self.label_mapping),
            'label_mapping': self.label_mapping
        }
        
        with open(self.output_dir / "evaluation_results.json", 'w') as f:
            json.dump(evaluation_results, f, indent=2)
        
        print("모델 평가 완료!")
        print(f"테스트 정확도: {results['test_accuracy']:.4f}")
        
    def convert_to_tflite(self):
        """TFLite 모델 변환"""
        # TFLite 모델 변환
        tflite_model = ReFitModelOptimizer.convert_to_tflite(
            model=self.classifier.model,
            output_path=self.output_dir / "tflite" / "refit_pose_classifier.tflite",
            quantize=True
        )
        
        # TFLite 모델 테스트
        accuracy = ReFitModelOptimizer.test_tflite_model(
            tflite_path=self.output_dir / "tflite" / "refit_pose_classifier.tflite",
            test_data=self.X_test,
            test_labels=self.y_test
        )
        
        # TFLite 메타데이터 저장
        tflite_metadata = {
            'model_name': 'refit_pose_classifier',
            'input_shape': [1, 16, 34],
            'output_shape': [1, len(self.label_mapping)],
            'quantized': True,
            'accuracy': float(accuracy),
            'label_mapping': self.label_mapping
        }
        
        with open(self.output_dir / "tflite" / "metadata.json", 'w') as f:
            json.dump(tflite_metadata, f, indent=2)
        
        print("TFLite 변환 완료!")
        print(f"TFLite 모델 정확도: {accuracy:.4f}")
        
    def summarize_results(self):
        """학습 결과 요약"""
        print("\n" + "=" * 60)
        print("Re_fit 모델 학습 결과 요약")
        print("=" * 60)
        
        # 모델 정보
        print(f"모델명: refit_pose_classifier")
        print(f"입력 형태: (16, 34) - 16프레임, 34개 키포인트")
        print(f"출력 클래스: {len(self.label_mapping)}개")
        print(f"클래스 목록: {list(self.label_mapping.values())}")
        
        # 파일 경로
        print(f"\n생성된 파일들:")
        print(f"- Keras 모델: {self.output_dir / 'models' / 'refit_pose_classifier.h5'}")
        print(f"- TFLite 모델: {self.output_dir / 'tflite' / 'refit_pose_classifier.tflite'}")
        print(f"- TFLite 메타데이터: {self.output_dir / 'tflite' / 'metadata.json'}")
        print(f"- 학습 그래프: {self.output_dir / 'plots' / 'training_history.png'}")
        print(f"- 혼동 행렬: {self.output_dir / 'plots' / 'confusion_matrix.png'}")
        
        # 성능 지표
        if hasattr(self.classifier, 'history'):
            best_val_acc = max(self.classifier.history.history['val_accuracy'])
            best_epoch = self.classifier.history.history['val_accuracy'].index(best_val_acc) + 1
            
            print(f"\n성능 지표:")
            print(f"- 최고 검증 정확도: {best_val_acc:.4f} (Epoch {best_epoch})")
            print(f"- 최종 학습 정확도: {self.classifier.history.history['accuracy'][-1]:.4f}")
            print(f"- 최종 검증 정확도: {self.classifier.history.history['val_accuracy'][-1]:.4f}")
        
        print("\n" + "=" * 60)

def main():
    """메인 실행 함수"""
    # GPU 메모리 설정 (필요시)
    gpus = tf.config.experimental.list_physical_devices('GPU')
    if gpus:
        try:
            for gpu in gpus:
                tf.config.experimental.set_memory_growth(gpu, True)
            print(f"GPU 사용 가능: {len(gpus)}개")
        except RuntimeError as e:
            print(f"GPU 설정 실패: {e}")
    else:
        print("GPU를 사용할 수 없습니다. CPU로 실행합니다.")
    
    # 학습 파이프라인 실행
    pipeline = ReFitTrainingPipeline()
    pipeline.run_full_pipeline()

if __name__ == "__main__":
    main()
