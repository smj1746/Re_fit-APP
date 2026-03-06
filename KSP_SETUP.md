# KAPT (Kotlin Annotation Processing Tool) 설정 가이드

## ✅ 현재 설정 상태 (KAPT 사용 중)

> **참고**: 이 프로젝트는 안정성을 위해 KSP 대신 **kapt**를 사용합니다.

### 1. **libs.versions.toml** (Gradle Version Catalog)
```toml
[versions]
kotlin = "2.0.21"
# KSP 제거 - kapt 사용으로 전환

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
# ksp 제거 - kapt 사용
```

### 2. **build.gradle.kts** (프로젝트 레벨)
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // ksp 제거 - kapt 사용
}
```

### 3. **app/build.gradle.kts** (앱 레벨)
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")  // ← kapt 플러그인 적용
}

dependencies {
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")  // ← kapt() 함수 사용
}
```

## 🔧 문제 해결

### 문제 1: "Unresolved reference: kapt"
**원인**: 앱 레벨 build.gradle.kts에 kapt 플러그인이 적용되지 않음

**해결 방법**:
```kotlin
// app/build.gradle.kts
plugins {
    id("kotlin-kapt")  // 이 줄 추가!
}
```

### 문제 2: kapt 빌드 느림
**원인**: kapt는 KSP보다 빌드 속도가 느립니다

**해결 방법**: gradle.properties에 최적화 옵션 추가
```properties
# gradle.properties
kapt.incremental=true
kapt.use.worker.api=true
```

### 문제 3: Kotlin 버전 호환성
**원인**: Kotlin 2.x 버전에서 kapt 호환성 문제

**해결 방법**: 안정적인 Kotlin 1.9.x로 다운그레이드
```toml
# gradle/libs.versions.toml
[versions]
kotlin = "1.9.10"  # 2.0.21 대신
```

### KSP로 전환을 고려한다면?
kapt보다 2배 빠른 KSP 사용을 원하시면:
- Kotlin 버전을 1.9.10으로 다운그레이드
- KSP 1.9.10-1.0.13 버전 사용
- 자세한 내용은 `KSP_TO_KAPT_MIGRATION.md` 참고

## 📋 체크리스트

kapt 설정이 올바른지 확인하세요:

- [x] `gradle/libs.versions.toml`에서 KSP 관련 설정 제거됨
- [x] `build.gradle.kts` (프로젝트)에서 KSP 플러그인 제거됨
- [x] `app/build.gradle.kts`에 `id("kotlin-kapt")` 적용됨
- [x] Room 컴파일러를 `kapt()` 함수로 추가됨
- [x] Kotlin 버전 2.0.21 사용 중 (또는 안정성 위해 1.9.10)

## 🚀 Gradle Sync 및 빌드

### 1. Android Studio에서 Gradle Sync
```
File → Sync Project with Gradle Files
```

또는 명령줄:
```bash
.\gradlew.bat --refresh-dependencies
```

### 2. Clean & Build
```bash
.\gradlew.bat clean
.\gradlew.bat assembleDebug
```

### 3. Cache 초기화 (문제 지속 시)
```
Android Studio:
File → Invalidate Caches / Restart → Invalidate and Restart
```

### 4. kapt 빌드 최적화 (선택사항)
`gradle.properties` 파일에 추가:
```properties
kapt.incremental=true
kapt.use.worker.api=true
kapt.include.compile.classpath=false
```

## 📚 참고 자료

- [Kotlin kapt 공식 문서](https://kotlinlang.org/docs/kapt.html)
- [Room Database with kapt](https://developer.android.com/jetpack/androidx/releases/room)
- [Gradle Version Catalog](https://docs.gradle.org/current/userguide/platforms.html)
- [kapt vs KSP 비교](https://kotlinlang.org/docs/ksp-overview.html#comparison-to-kapt)

## ✨ 최종 확인

모든 설정이 완료되었으면:

1. ✅ **Gradle Sync 성공** - Android Studio에서 확인
2. ✅ **빌드 성공** - 아래 명령으로 확인
3. ✅ **앱 실행** - 디바이스/에뮬레이터에서 테스트

```bash
# Android Studio에서 직접 빌드하거나

# 또는 명령줄에서:
.\gradlew.bat assembleDebug

# APK 위치:
# app/build/outputs/apk/debug/app-debug.apk
```

---

**Re:fit 팀** - kapt 설정 완료! 💪

> **다음 단계**: Android Studio에서 **Sync Project with Gradle Files**를 실행하세요.
