"""
Re_fit 커스텀 포즈 분류 모델 - 데이터 전처리 스크립트
AI Hub 피트니스 자세 이미지 데이터셋을 활용한 키포인트 추출 및 전처리
"""

import os
import json
import numpy as np
import cv2
import tensorflow as tf
import tensorflow_hub as hub
from pathlib import Path
import matplotlib.pyplot as plt
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder
import warnings
warnings.filterwarnings('ignore')

class ReFitDataPreprocessor:
    def __init__(self, data_dir="data", output_dir="processed_data"):
        self.data_dir = Path(data_dir)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)
        
        # MoveNet 모델 로드 (Lightning - 모바일 최적화)
        self.movenet = hub.load("https://tfhub.dev/google/movenet/singlepose/lightning/4")
        
        # 레이블 정의 (스쿼트, 푸시업, 플랭크의 정자세/오류자세)
        self.labels = [
            "squat_down", "squat_up", "squat_bad_knee", "squat_bad_back",
            "pushup_down", "pushup_up", "pushup_bad_form", "pushup_bad_arch",
            "plank_good", "plank_arch", "plank_sink"
        ]
        
        # COCO 17 키포인트 인덱스
        self.keypoint_names = [
            'nose', 'left_eye', 'right_eye', 'left_ear', 'right_ear',
            'left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow',
            'left_wrist', 'right_wrist', 'left_hip', 'right_hip',
            'left_knee', 'right_knee', 'left_ankle', 'right_ankle'
        ]
        
    def extract_keypoints(self, image_np):
        """MoveNet을 사용하여 17개 키포인트 추출"""
        try:
            # 이미지 전처리 (192x192로 리사이즈)
            img = tf.image.resize_with_pad(tf.expand_dims(image_np, axis=0), 192, 192)
            inputs = tf.cast(img, dtype=tf.int32)
            
            # MoveNet 추론
            outputs = self.movenet.signatures['serving_default'](inputs)
            keypoints = outputs['output_0'].numpy().reshape(1, 17, 3)[0]  # (y, x, confidence)
            
            return keypoints
        except Exception as e:
            print(f"키포인트 추출 실패: {e}")
            return None
    
    def normalize_keypoints(self, keypoints):
        """키포인트 정규화 (골반 중심, 어깨폭 스케일링)"""
        xy = keypoints[:, :2]  # (x, y) 좌표만 사용
        confidence = keypoints[:, 2]
        
        # 골반 중심점 계산 (왼쪽/오른쪽 엉덩이의 중점)
        pelvis = (xy[11] + xy[12]) / 2.0  # left_hip, right_hip
        
        # 골반 중심으로 이동
        xy = xy - pelvis
        
        # 어깨폭으로 스케일링
        shoulder_width = np.linalg.norm(xy[5] - xy[6]) + 1e-6  # left_shoulder, right_shoulder
        xy = xy / shoulder_width
        
        # 정규화된 키포인트와 신뢰도 결합
        normalized = np.column_stack([xy, confidence])
        return normalized.reshape(-1)  # (51,) - 17개 키포인트 * 3개 값
    
    def process_image(self, image_path, label):
        """단일 이미지 처리"""
        try:
            # 이미지 로드
            image = cv2.imread(str(image_path))
            if image is None:
                return None, None
                
            image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            
            # 키포인트 추출
            keypoints = self.extract_keypoints(image_rgb)
            if keypoints is None:
                return None, None
            
            # 키포인트 정규화
            normalized_kpts = self.normalize_keypoints(keypoints)
            
            return normalized_kpts, label
            
        except Exception as e:
            print(f"이미지 처리 실패 {image_path}: {e}")
            return None, None
    
    def create_sequences(self, keypoints_list, labels_list, window_size=16):
        """시퀀스 데이터 생성 (슬라이딩 윈도우)"""
        X_sequences = []
        y_sequences = []
        
        # 각 운동별로 시퀀스 생성
        for i in range(len(keypoints_list) - window_size + 1):
            sequence = keypoints_list[i:i + window_size]
            # 가장 빈번한 레이블을 시퀀스 레이블로 사용
            sequence_label = max(set(labels_list[i:i + window_size]), 
                               key=labels_list[i:i + window_size].count)
            
            X_sequences.append(np.concatenate(sequence, axis=0))
            y_sequences.append(sequence_label)
        
        return np.array(X_sequences), np.array(y_sequences)
    
    def load_aihub_data(self, aihub_dir):
        """AI Hub 데이터셋 로드 (실제 데이터 구조에 맞게 수정 필요)"""
        print("AI Hub 데이터셋 로딩 중...")
        
        # AI Hub 데이터 구조 예시 (실제 구조에 맞게 수정)
        # data/
        #   ├── squat/
        #   │   ├── good/
        #   │   └── bad/
        #   ├── pushup/
        #   │   ├── good/
        #   │   └── bad/
        #   └── plank/
        #       ├── good/
        #       └── bad/
        
        all_keypoints = []
        all_labels = []
        
        # 각 운동별 데이터 처리
        for exercise in ['squat', 'pushup', 'plank']:
            exercise_dir = self.data_dir / exercise
            if not exercise_dir.exists():
                print(f"경고: {exercise} 디렉토리가 없습니다. 샘플 데이터를 생성합니다.")
                continue
                
            for quality in ['good', 'bad']:
                quality_dir = exercise_dir / quality
                if not quality_dir.exists():
                    continue
                    
                # 이미지 파일들 처리
                for img_path in quality_dir.glob('*.jpg'):
                    if exercise == 'squat':
                        if quality == 'good':
                            label = 'squat_down'  # 또는 'squat_up'
                        else:
                            label = 'squat_bad_knee'
                    elif exercise == 'pushup':
                        if quality == 'good':
                            label = 'pushup_down'  # 또는 'pushup_up'
                        else:
                            label = 'pushup_bad_form'
                    else:  # plank
                        if quality == 'good':
                            label = 'plank_good'
                        else:
                            label = 'plank_arch'
                    
                    keypoints, processed_label = self.process_image(img_path, label)
                    if keypoints is not None:
                        all_keypoints.append(keypoints)
                        all_labels.append(processed_label)
        
        return np.array(all_keypoints), np.array(all_labels)
    
    def create_synthetic_data(self, num_samples=1000):
        """AI Hub 데이터가 없을 경우 합성 데이터 생성"""
        print("합성 데이터 생성 중...")
        
        np.random.seed(42)
        all_keypoints = []
        all_labels = []
        
        for _ in range(num_samples):
            # 랜덤 키포인트 생성 (정규화된 형태)
            keypoints = np.random.randn(51) * 0.5  # 17개 키포인트 * 3개 값
            
            # 레이블 랜덤 선택
            label = np.random.choice(self.labels)
            
            all_keypoints.append(keypoints)
            all_labels.append(label)
        
        return np.array(all_keypoints), np.array(all_labels)
    
    def preprocess_data(self):
        """전체 데이터 전처리 파이프라인"""
        print("Re_fit 데이터 전처리 시작...")
        
        # AI Hub 데이터 로드 시도
        try:
            keypoints, labels = self.load_aihub_data(self.data_dir)
            if len(keypoints) == 0:
                print("AI Hub 데이터가 없습니다. 합성 데이터를 생성합니다.")
                keypoints, labels = self.create_synthetic_data()
        except Exception as e:
            print(f"AI Hub 데이터 로드 실패: {e}")
            print("합성 데이터를 생성합니다.")
            keypoints, labels = self.create_synthetic_data()
        
        print(f"로드된 데이터: {len(keypoints)}개 샘플")
        
        # 시퀀스 데이터 생성
        print("시퀀스 데이터 생성 중...")
        X_sequences, y_sequences = self.create_sequences(keypoints, labels)
        
        print(f"생성된 시퀀스: {len(X_sequences)}개")
        
        # 레이블 인코딩
        label_encoder = LabelEncoder()
        y_encoded = label_encoder.fit_transform(y_sequences)
        
        # 학습/검증/테스트 분할
        X_train, X_temp, y_train, y_temp = train_test_split(
            X_sequences, y_encoded, test_size=0.3, random_state=42, stratify=y_encoded
        )
        X_val, X_test, y_val, y_test = train_test_split(
            X_temp, y_temp, test_size=0.5, random_state=42, stratify=y_temp
        )
        
        # 데이터 저장
        np.save(self.output_dir / "X_train.npy", X_train)
        np.save(self.output_dir / "X_val.npy", X_val)
        np.save(self.output_dir / "X_test.npy", X_test)
        np.save(self.output_dir / "y_train.npy", y_train)
        np.save(self.output_dir / "y_val.npy", y_val)
        np.save(self.output_dir / "y_test.npy", y_test)
        
        # 레이블 매핑 저장
        label_mapping = {i: label for i, label in enumerate(label_encoder.classes_)}
        with open(self.output_dir / "label_mapping.json", 'w') as f:
            json.dump(label_mapping, f, indent=2)
        
        print(f"전처리 완료! 데이터가 {self.output_dir}에 저장되었습니다.")
        print(f"학습 데이터: {X_train.shape}, 검증 데이터: {X_val.shape}, 테스트 데이터: {X_test.shape}")
        
        return X_train, X_val, X_test, y_train, y_val, y_test, label_mapping

def main():
    """메인 실행 함수"""
    preprocessor = ReFitDataPreprocessor()
    preprocessor.preprocess_data()

if __name__ == "__main__":
    main()
