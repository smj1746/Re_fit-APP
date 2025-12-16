# KSP (Kotlin Symbol Processing) 설정 가이드

## ✅ 현재 설정 상태

### 1. **libs.versions.toml** (Gradle Version Catalog)
```toml
[versions]
kotlin = "2.0.21"
ksp = "2.0.21-1.0.29"

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### 2. **build.gradle.kts** (프로젝트 레벨)
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false  // ← 이 줄이 중요!
}
```

### 3. **app/build.gradle.kts** (앱 레벨)
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)  // ← KSP 플러그인 적용
}

dependencies {
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")  // ← ksp() 함수 사용 가능
}
```

## 🔧 문제 해결

### 문제 1: "Unresolved reference: ksp"
**원인**: 프로젝트 레벨 build.gradle.kts에 KSP 플러그인이 선언되지 않음

**해결 방법**:
```kotlin
// build.gradle.kts (프로젝트 레벨)
plugins {
    alias(libs.plugins.ksp) apply false  // 이 줄 추가!
}
```

### 문제 2: "Plugin [id: 'com.google.devtools.ksp'] was not found"
**원인**: libs.versions.toml에 KSP 플러그인 정의 누락

**해결 방법**:
```toml
# gradle/libs.versions.toml
[versions]
ksp = "2.0.21-1.0.29"

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### 문제 3: 버전 호환성 오류
**원인**: Kotlin 버전과 KSP 버전이 맞지 않음

**해결 방법**: 버전 매핑 표 참고

| Kotlin 버전 | KSP 버전 |
|------------|----------|
| 2.0.21 | 2.0.21-1.0.29 |
| 2.0.20 | 2.0.20-1.0.25 |
| 2.0.10 | 2.0.10-1.0.24 |
| 1.9.24 | 1.9.24-1.0.20 |
| 1.9.23 | 1.9.23-1.0.20 |
| 1.9.22 | 1.9.22-1.0.17 |

## 📋 체크리스트

KSP 설정이 올바른지 확인하세요:

- [ ] `gradle/libs.versions.toml`에 KSP 버전 정의됨
- [ ] `gradle/libs.versions.toml`에 KSP 플러그인 정의됨
- [ ] `build.gradle.kts` (프로젝트)에 `apply false` 선언됨
- [ ] `app/build.gradle.kts`에 KSP 플러그인 적용됨
- [ ] Kotlin과 KSP 버전이 호환됨
- [ ] `ksp()` 함수로 의존성 추가됨

## 🚀 Gradle Sync 및 빌드

### 1. Gradle Sync
```bash
# 의존성 새로고침
.\gradlew.bat --refresh-dependencies

# 또는 Android Studio에서:
# File → Sync Project with Gradle Files
```

### 2. Clean & Build
```bash
.\gradlew.bat clean
.\gradlew.bat assembleDebug
```

### 3. Cache 초기화 (문제 지속 시)
```
Android Studio:
File → Invalidate Caches / Restart
```

## 📚 참고 자료

- [KSP 공식 문서](https://kotlinlang.org/docs/ksp-overview.html)
- [Room Database with KSP](https://developer.android.com/jetpack/androidx/releases/room#ksp)
- [Gradle Version Catalog](https://docs.gradle.org/current/userguide/platforms.html)

## ✨ 최종 확인

모든 설정이 완료되었으면:

1. **Gradle Sync 성공** 확인
2. **빌드 성공** 확인
3. **앱 실행** 테스트

```bash
# 빌드
.\gradlew.bat assembleDebug

# 앱 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 앱 실행
adb shell am start -n com.example.myapplication/.MainActivity
```

---

**Re:fit 팀** - 문제 해결 완료! 💪
