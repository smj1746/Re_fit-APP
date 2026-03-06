# KSP에서 KAPT로 마이그레이션

## 문제 상황
- **Kotlin 2.0.21 + KSP 2.0.21-1.0.29** 조합이 불안정
- Room Database 컴파일 오류 발생
- Gradle Sync 실패

## 해결 방법: KAPT로 전환

### 왜 KAPT인가?
- ✅ **성숙하고 안정적**: 오랫동안 사용되어 온 검증된 기술
- ✅ **호환성 우수**: 대부분의 라이브러리와 호환
- ✅ **에러 적음**: 프로덕션 환경에서 안전
- ⚠️ **빌드 속도**: KSP보다 느림 (하지만 안정성 > 속도)

## 변경 내역

### 1. gradle/libs.versions.toml
```toml
# 변경 전
[versions]
agp = "8.10.0"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.29"

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }

# 변경 후
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
# KSP 제거

[plugins]
# ksp 제거 - kapt 사용
```

### 2. build.gradle.kts (프로젝트 레벨)
```kotlin
// 변경 전
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// 변경 후
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // ksp 제거 - kapt 사용
}
```

### 3. app/build.gradle.kts (앱 레벨)
```kotlin
// 변경 전
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}

// 변경 후
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")  // KSP 대신 kapt 사용
}

dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")  // ksp 대신 kapt 사용
}
```

## 빌드 및 테스트

### 1. 클린 빌드
```bash
./gradlew clean
./gradlew build
```

### 2. Android Studio에서
1. **File → Invalidate Caches / Restart**
2. **File → Sync Project with Gradle Files**
3. **Build → Clean Project**
4. **Build → Rebuild Project**

### 3. 확인 사항
- [ ] Gradle Sync 성공
- [ ] Room Database 컴파일 성공
- [ ] APK 빌드 성공
- [ ] 앱 실행 정상

## 성능 비교

| 항목 | KSP | KAPT |
|------|-----|------|
| 빌드 속도 | ⚡ 빠름 | 🐢 느림 |
| 안정성 | ⚠️ 불안정 (Kotlin 2.0+) | ✅ 안정적 |
| 호환성 | 제한적 | 광범위 |
| 프로덕션 사용 | ❌ 권장 안 함 | ✅ 권장 |

## 향후 계획

### KSP로 다시 전환하려면?
Kotlin과 KSP가 안정화되면 (예: Kotlin 1.9.10 + KSP 1.9.10-1.0.13):

```toml
# gradle/libs.versions.toml
[versions]
kotlin = "1.9.10"
ksp = "1.9.10-1.0.13"

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.ksp)  // kapt 제거
}

dependencies {
    ksp("androidx.room:room-compiler:2.6.1")  // kapt 제거
}
```

## 추가 참고 자료

- [Kotlin KAPT 공식 문서](https://kotlinlang.org/docs/kapt.html)
- [KSP vs KAPT 비교](https://developer.android.com/studio/build/migrate-to-ksp)
- [Room Database 설정](https://developer.android.com/training/data-storage/room)

## 트러블슈팅

### Q: kapt 빌드가 너무 느려요
A: kapt는 KSP보다 느리지만, 증분 빌드를 활성화하면 개선됩니다:
```kotlin
kapt {
    correctErrorTypes = true
    useBuildCache = true
}
```

### Q: "Could not find method kapt()" 오류
A: `id("kotlin-kapt")` 플러그인이 제대로 적용되었는지 확인

### Q: Room Database 컴파일 오류
A: Clean Project 후 Rebuild 시도

---

**마이그레이션 일자**: 2025-12-16
**작업자**: Claude Code + User
**상태**: ✅ 완료
