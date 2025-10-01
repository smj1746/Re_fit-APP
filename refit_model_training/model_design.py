"""
Re_fit 커스텀 포즈 분류 모델 - 모델 설계 및 구현
BiLSTM 기반 시퀀스 분류 모델
"""

import numpy as np
import tensorflow as tf
from tensorflow import keras as K
from tensorflow.keras import layers
import matplotlib.pyplot as plt
from sklearn.metrics import classification_report, confusion_matrix
import seaborn as sns

class ReFitPoseClassifier:
    def __init__(self, input_shape=(16, 34), num_classes=11, model_name="refit_pose_classifier"):
        self.input_shape = input_shape
        self.num_classes = num_classes
        self.model_name = model_name
        self.model = None
        self.history = None
        
    def build_model(self, lstm_units=64, dense_units=128, dropout_rate=0.3):
        """BiLSTM 기반 포즈 분류 모델 구축"""
        
        # 입력 레이어
        inputs = layers.Input(shape=self.input_shape, name='pose_sequence')
        
        # 첫 번째 BiLSTM 레이어 (시퀀스 반환)
        lstm1 = layers.Bidirectional(
            layers.LSTM(lstm_units, return_sequences=True, dropout=dropout_rate),
            name='bilstm1'
        )(inputs)
        
        # 두 번째 BiLSTM 레이어
        lstm2 = layers.Bidirectional(
            layers.LSTM(lstm_units, dropout=dropout_rate),
            name='bilstm2'
        )(lstm1)
        
        # Dense 레이어들
        dense1 = layers.Dense(dense_units, activation='relu', name='dense1')(lstm2)
        dropout1 = layers.Dropout(dropout_rate, name='dropout1')(dense1)
        
        dense2 = layers.Dense(dense_units // 2, activation='relu', name='dense2')(dropout1)
        dropout2 = layers.Dropout(dropout_rate, name='dropout2')(dense2)
        
        # 출력 레이어
        outputs = layers.Dense(self.num_classes, activation='softmax', name='pose_classification')(dropout2)
        
        # 모델 생성
        self.model = K.Model(inputs=inputs, outputs=outputs, name=self.model_name)
        
        return self.model
    
    def compile_model(self, learning_rate=1e-3, optimizer='adam'):
        """모델 컴파일"""
        if self.model is None:
            raise ValueError("모델이 구축되지 않았습니다. build_model()을 먼저 호출하세요.")
        
        # 옵티마이저 설정
        if optimizer == 'adam':
            opt = K.optimizers.Adam(learning_rate=learning_rate)
        elif optimizer == 'rmsprop':
            opt = K.optimizers.RMSprop(learning_rate=learning_rate)
        else:
            opt = optimizer
        
        # 모델 컴파일
        self.model.compile(
            optimizer=opt,
            loss='sparse_categorical_crossentropy',
            metrics=['accuracy', 'top_k_categorical_accuracy']
        )
        
        print("모델 컴파일 완료!")
        print(f"총 파라미터 수: {self.model.count_params():,}")
        
    def train_model(self, X_train, y_train, X_val, y_val, 
                   epochs=50, batch_size=32, callbacks=None):
        """모델 학습"""
        if self.model is None:
            raise ValueError("모델이 구축되지 않았습니다.")
        
        # 기본 콜백 설정
        if callbacks is None:
            callbacks = [
                K.callbacks.EarlyStopping(
                    monitor='val_loss',
                    patience=10,
                    restore_best_weights=True,
                    verbose=1
                ),
                K.callbacks.ReduceLROnPlateau(
                    monitor='val_loss',
                    factor=0.5,
                    patience=5,
                    min_lr=1e-6,
                    verbose=1
                ),
                K.callbacks.ModelCheckpoint(
                    f'{self.model_name}_best.h5',
                    monitor='val_accuracy',
                    save_best_only=True,
                    verbose=1
                )
            ]
        
        # 학습 실행
        print("모델 학습 시작...")
        self.history = self.model.fit(
            X_train, y_train,
            validation_data=(X_val, y_val),
            epochs=epochs,
            batch_size=batch_size,
            callbacks=callbacks,
            verbose=1
        )
        
        print("학습 완료!")
        return self.history
    
    def evaluate_model(self, X_test, y_test, label_names=None):
        """모델 평가"""
        if self.model is None:
            raise ValueError("모델이 구축되지 않았습니다.")
        
        # 테스트 데이터 평가
        test_loss, test_accuracy, test_top_k = self.model.evaluate(X_test, y_test, verbose=0)
        
        print(f"테스트 정확도: {test_accuracy:.4f}")
        print(f"테스트 Top-K 정확도: {test_top_k:.4f}")
        
        # 예측 결과
        y_pred = self.model.predict(X_test)
        y_pred_classes = np.argmax(y_pred, axis=1)
        
        # 분류 리포트
        if label_names:
            print("\n분류 리포트:")
            print(classification_report(y_test, y_pred_classes, target_names=label_names))
        
        return {
            'test_accuracy': test_accuracy,
            'test_top_k_accuracy': test_top_k,
            'predictions': y_pred,
            'predicted_classes': y_pred_classes
        }
    
    def plot_training_history(self, save_path=None):
        """학습 과정 시각화"""
        if self.history is None:
            print("학습 기록이 없습니다.")
            return
        
        fig, axes = plt.subplots(1, 2, figsize=(15, 5))
        
        # 정확도 그래프
        axes[0].plot(self.history.history['accuracy'], label='Training Accuracy')
        axes[0].plot(self.history.history['val_accuracy'], label='Validation Accuracy')
        axes[0].set_title('Model Accuracy')
        axes[0].set_xlabel('Epoch')
        axes[0].set_ylabel('Accuracy')
        axes[0].legend()
        axes[0].grid(True)
        
        # 손실 그래프
        axes[1].plot(self.history.history['loss'], label='Training Loss')
        axes[1].plot(self.history.history['val_loss'], label='Validation Loss')
        axes[1].set_title('Model Loss')
        axes[1].set_xlabel('Epoch')
        axes[1].set_ylabel('Loss')
        axes[1].legend()
        axes[1].grid(True)
        
        plt.tight_layout()
        
        if save_path:
            plt.savefig(save_path, dpi=300, bbox_inches='tight')
            print(f"학습 그래프가 {save_path}에 저장되었습니다.")
        
        plt.show()
    
    def plot_confusion_matrix(self, y_true, y_pred, label_names=None, save_path=None):
        """혼동 행렬 시각화"""
        cm = confusion_matrix(y_true, y_pred)
        
        plt.figure(figsize=(10, 8))
        sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
                   xticklabels=label_names, yticklabels=label_names)
        plt.title('Confusion Matrix')
        plt.xlabel('Predicted Label')
        plt.ylabel('True Label')
        
        if save_path:
            plt.savefig(save_path, dpi=300, bbox_inches='tight')
            print(f"혼동 행렬이 {save_path}에 저장되었습니다.")
        
        plt.show()
    
    def save_model(self, filepath):
        """모델 저장"""
        if self.model is None:
            raise ValueError("모델이 구축되지 않았습니다.")
        
        self.model.save(filepath)
        print(f"모델이 {filepath}에 저장되었습니다.")
    
    def load_model(self, filepath):
        """모델 로드"""
        self.model = K.models.load_model(filepath)
        print(f"모델이 {filepath}에서 로드되었습니다.")
        return self.model

class ReFitModelOptimizer:
    """모델 최적화를 위한 클래스"""
    
    @staticmethod
    def convert_to_tflite(model, output_path, quantize=True):
        """TensorFlow Lite 모델로 변환"""
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        
        if quantize:
            # 양자화 설정 (모바일 최적화)
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            converter.target_spec.supported_types = [tf.float16]  # FP16 양자화
        
        tflite_model = converter.convert()
        
        with open(output_path, 'wb') as f:
            f.write(tflite_model)
        
        print(f"TFLite 모델이 {output_path}에 저장되었습니다.")
        
        # 모델 크기 확인
        model_size = len(tflite_model) / (1024 * 1024)  # MB
        print(f"모델 크기: {model_size:.2f} MB")
        
        return tflite_model
    
    @staticmethod
    def test_tflite_model(tflite_path, test_data, test_labels):
        """TFLite 모델 테스트"""
        # TFLite 인터프리터 로드
        interpreter = tf.lite.Interpreter(model_path=tflite_path)
        interpreter.allocate_tensors()
        
        # 입력/출력 텐서 정보
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        print(f"입력 형태: {input_details[0]['shape']}")
        print(f"출력 형태: {output_details[0]['shape']}")
        
        # 테스트 데이터로 추론
        correct_predictions = 0
        total_predictions = len(test_data)
        
        for i in range(total_predictions):
            # 입력 데이터 설정
            interpreter.set_tensor(input_details[0]['index'], test_data[i:i+1])
            
            # 추론 실행
            interpreter.invoke()
            
            # 결과 가져오기
            output_data = interpreter.get_tensor(output_details[0]['index'])
            predicted_class = np.argmax(output_data[0])
            
            if predicted_class == test_labels[i]:
                correct_predictions += 1
        
        accuracy = correct_predictions / total_predictions
        print(f"TFLite 모델 정확도: {accuracy:.4f}")
        
        return accuracy

def main():
    """메인 실행 함수"""
    # 모델 생성
    classifier = ReFitPoseClassifier()
    
    # 모델 구축
    model = classifier.build_model()
    print(model.summary())
    
    # 모델 컴파일
    classifier.compile_model()
    
    print("Re_fit 포즈 분류 모델이 성공적으로 구축되었습니다!")

if __name__ == "__main__":
    main()
