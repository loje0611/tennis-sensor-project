# TASK-015 — `:core:analysis` 추출 (Kinematic · Coaching · Edge Impulse NDK)

| 항목 | 값 |
|---|---|
| Task ID | TASK-015 |
| Target Project | `TennisDocAI` |
| Depends on | TASK-011, TASK-013, TASK-014 |
| 관련 계획 | [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md) §4, §4.3 |

## Revision History

| 회차 | 날짜 | 작성자 | 사유 |
|---|---|---|---|
| v1 | 2026-08-07 | PM | 최초 작성 |

---

## 1. 배경

A그룹의 마지막이자 **가장 위험한 작업**입니다. 순수 Kotlin 코드만 옮긴 TASK-010~012·014와 달리, 본 task는 **CMake 네이티브 빌드(1,383개 파일 · 약 29MB) 와 JNI 브리지를 라이브러리 모듈로 이전**합니다. 빌드 구성·패키징·난독화 세 층위가 동시에 움직입니다.

다행히 선행 작업으로 안전망이 갖춰져 있습니다.

- **TASK-013** — JNI가 `RegisterNatives` 방식으로 전환되어 클래스 경로가 C++ 내 문자열 **한 곳**(`NATIVE_CLASS_PATH`)에 모였고, 그 값이 틀리면 `JNI_OnLoad`가 `JNI_ERR`을 반환해 **라이브러리 적재 자체가 실패**합니다. 또한 `verifyJniBindings`가 기대 디스크립터를 **Kotlin 소스의 `package` 선언에서 파생**시키므로, 패키지를 옮기고 C++을 고치지 않으면 자동 검출됩니다. **본 task는 그 안전망의 첫 실전 시험입니다.**
- **TASK-014** — `:core:model`이 생겨 `SwingMetrics`·`SwingClassificationKeys`를 참조할 수 있습니다.

## 2. 목적

`:core:analysis` 모듈에 스윙 분석 로직과 Edge Impulse 추론 스택 전체(Kotlin + C++ + CMake)를 이전한다. 완료 시 `:app`에는 분석 알고리즘과 네이티브 빌드가 남지 않는다.

## 3. 범위

### 3.1 이동 대상

| 현재 위치 | 이동 후 |
|---|---|
| `app/src/main/java/.../tennisdoc/analysis/` 7개 파일 | `core/analysis/src/main/java/.../tennisdoc/core/analysis/` |
| `app/src/main/java/.../tennisdoc/inference/EdgeImpulseNative.kt` | `core/analysis/src/main/java/.../tennisdoc/core/analysis/inference/` |
| `app/src/main/cpp/` 전체 (`CMakeLists.txt`, `ei_jni_bridge.cpp`, `edge_impulse/`) | `core/analysis/src/main/cpp/` |
| `app/src/test/.../analysis/` 4개 테스트<br>(`CoachingEngineTest`, `KinematicAnalyzerTest`, `VolleyDetectorTest`, `SwingInferenceBufferTest`) | `core/analysis/src/test/.../core/analysis/` |

이동 대상 7개 파일: `CoachingEngine.kt`, `EdgeImpulseInputSpec.kt`, `KinematicAnalyzer.kt`, `RawSwingTelemetry.kt`, `SwingInferenceBuffer.kt`, `SwingKinematicsBuffer.kt`, `VolleyDetector.kt`.

### 3.2 구성 변경

- `core/analysis/build.gradle.kts` 신규 (`tennisdoc.android.library` + NDK 구성 + `:core:model` 의존)
- `app/build.gradle.kts` — `externalNativeBuild`·`ndk` 블록 제거, `implementation(project(":core:analysis"))` 추가
- 루트 `build.gradle.kts` — `verifyJniBindings`의 경로 가정 점검·갱신 (§4.6)
- `app/proguard-rules.pro` 또는 모듈 consumer 규칙 — JNI keep 규칙 갱신 (§4.5)

### 3.3 제외 (건드리지 말 것)

- **네이티브 라이브러리 이름 `swingsense_ei` 는 변경하지 않는다.** 구 브랜드명이지만, 개명은 `System.loadLibrary`·CMake `project()`·`verifyJniBindings`의 `libswingsense_ei.so` 문자열을 동시에 바꾸는 별개의 변경입니다. 본 task의 위험도를 고려해 **분리합니다.**
- `edge_impulse/` **내부 파일의 내용** — 위치만 옮기고 한 줄도 수정하지 않는다.
- `SwingHistoryRepository`, `MockSwingDataGenerator`, `SwingAnalysisForegroundService`의 모듈 이동 — `import` 갱신만 한다.
- Room 스키마, `:core:data`, `:core:model`, `:core:sensor`, `:core:ui` 의 내용.
- `:feature:*` 모듈 신설 — TASK-016 이후.

---

## 4. 기능 요구사항

### FR-1. 모듈 생성 및 의존성
`settings.gradle.kts`에 `include(":core:analysis")`를 추가하고, `core/analysis/build.gradle.kts`에 `tennisdoc.android.library` 컨벤션 플러그인을 적용한다. `namespace`는 `io.github.loje0611.tennisdoc.core.analysis`.

의존성은 `:core:model`을 선언한다. `verifyModuleDependencies`의 허용 집합(`:core:analysis` → `{:core:model, :core:sensor}`)은 이미 갱신되어 있으므로 **맵 수정은 불필요**하다. `:core:sensor`는 실제로 필요할 때만 선언한다(현재 분석 코드에 `:core:sensor` 참조 없음).

### FR-2. 패키지 이동
이동한 Kotlin 파일의 패키지를 다음으로 변경한다.

| 대상 | 새 패키지 |
|---|---|
| 분석 7개 파일 | `io.github.loje0611.tennisdoc.core.analysis` |
| `EdgeImpulseNative.kt` | `io.github.loje0611.tennisdoc.core.analysis.inference` |

`:core:data`(`core.data`)·`:core:model`(`core.model`)과 동일한 명명 규칙을 따른다.

> **패키지를 바꾸는 이유**: 이름을 유지하면 JNI 디스크립터를 건드리지 않아 당장은 편합니다. 그러나 `...tennisdoc.inference`가 `:core:analysis` 안에 있는 구조는 모듈 경계와 패키지 구조가 어긋나 이후 혼선을 낳습니다. 무엇보다 **TASK-013에서 이 이동을 안전하게 만들려고 `RegisterNatives`와 `verifyJniBindings`를 도입했습니다.** 지금이 그 장치를 사용할 시점입니다.

### FR-3. C++ 클래스 경로 동기화
`ei_jni_bridge.cpp`의 `NATIVE_CLASS_PATH` 상수를 새 패키지에 맞춰 갱신한다.

```
"io/github/loje0611/tennisdoc/core/analysis/inference/EdgeImpulseNative"
```

이 값은 `JNI_OnLoad`의 `FindClass` 인자이며, 틀리면 라이브러리 적재가 실패한다. **FR-2를 수행하고 이 요구사항을 누락하면 `verifyJniBindings`가 실패해야 한다**(AC-9의 변이 검증이 이를 확인한다).

### FR-4. 네이티브 빌드 구성 이전
`app/build.gradle.kts`의 `externalNativeBuild { cmake { ... } }`(`defaultConfig` 내부·`android` 직속 양쪽)와 `ndk { abiFilters }` 를 `core/analysis/build.gradle.kts`로 옮긴다. 다음 설정값은 **그대로 보존**한다.

- ABI 4종: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`
- CMake 버전 `3.22.1`, `path = file("src/main/cpp/CMakeLists.txt")`
- `arguments += listOf("-DANDROID_STL=c++_shared")`, `cppFlags += "-std=c++17"`

`app`에는 네이티브 빌드 구성이 남지 않아야 한다. 최종 APK에 4개 ABI의 `libswingsense_ei.so`가 모두 패키징되는 것이 완료 기준이다(AC-4).

### FR-5. 파일 이동 방식
`edge_impulse/` 는 1,383개 파일 · 약 29MB이다. **`git mv`(또는 이동 후 `git add -A` 없이 명시적 경로 스테이징)로 이동해 Git이 rename으로 인식하게 한다.** 삭제 후 재생성 형태가 되면 diff가 수만 줄로 불어나 리뷰가 불가능해진다.

### FR-6. 참조 갱신
이동한 타입을 사용하는 `:app` 코드의 `import`를 갱신한다. 확인된 사용처는 `SwingAnalysisForegroundService.kt`, `SessionDetailViewModel.kt`, `MockSwingDataGenerator.kt`, `SwingHistoryRepository.kt` 이며, 누락 시 컴파일이 실패하므로 AC-3이 실질 검증 수단이다.

`ImuFrameSpecConsistencyTest.kt`는 **`:app`에 그대로 둔다**(`:app`은 `:core:sensor`와 `:core:analysis`를 모두 참조하므로 컴파일된다). `import` 갱신만 허용하며, **단정문과 기대값(`40`, `6`, `240`)은 변경하지 않는다.** 실패 메시지 문자열 중 모듈명 표기는 새 구조에 맞게 고쳐도 된다.

### FR-7. ProGuard keep 규칙 갱신
`app/proguard-rules.pro`의 다음 규칙은 FR-2 수행 시 **가리키는 클래스가 사라져 무효가 된다.**

```
-keep class io.github.loje0611.tennisdoc.inference.EdgeImpulseNative { *; }
```

새 FQCN(`io.github.loje0611.tennisdoc.core.analysis.inference.EdgeImpulseNative`)을 keep하도록 갱신하고, **구 패키지를 가리키는 죽은 규칙은 남기지 않는다.**

규칙은 `core/analysis/consumer-rules.pro`에 두고 모듈 `build.gradle.kts`에서 `consumerProguardFiles`로 선언하는 방식을 **권장**한다. 규칙이 대상 코드와 같은 모듈에 있으면 이후 이동 시 함께 따라간다. `app/proguard-rules.pro`에 두어도 무방하나, 어느 쪽이든 **FQCN이 실제 패키지와 일치**해야 한다(AC-8).

> **이 요구사항이 중요한 이유**: 이 결함은 **릴리스 빌드에서만** 발현합니다. `RegisterNatives`는 `FindClass(NATIVE_CLASS_PATH)`로 클래스를 찾는데, R8이 클래스를 제거·개명하면 조회가 실패하고 `JNI_OnLoad`가 `JNI_ERR`을 반환해 **분류 기능이 통째로 죽습니다.** 디버그 빌드와 단위 테스트는 전부 초록색입니다. TASK-012에서 Room keep 규칙을 두고 겪은 것과 같은 종류이며, 이번에는 결과가 더 무겁습니다.

### FR-8. `verifyJniBindings` 경로 정합성
루트 `build.gradle.kts`의 `verifyJniBindings`는 현재 `:app:mergeDebugNativeLibs`에 의존하고 `app/build/intermediates/**` 에서 `.so`를 찾는다. 네이티브 빌드가 `:core:analysis`로 옮겨간 뒤에도 **4개 ABI를 모두 찾아 검사에 성공해야 한다.**

`.so`가 위 경로에서 계속 발견된다면 태스크를 수정할 필요가 없다. 발견되지 않는다면 `dependsOn`과 탐색 경로를 새 산출물 위치에 맞게 갱신한다. **어느 경우든 "찾지 못했으니 통과"가 되어서는 안 된다** — 현재 구현은 ABI 누락 시 예외를 던지므로 이 성질을 유지한다(AC-10에서 확인).

또한 이 태스크는 `fileTree(rootDir).matching { include("**/EdgeImpulseNative.kt") }.singleOrNull()` 로 소스를 찾는다. **파일이 두 곳에 존재하면 `singleOrNull()`이 `null`이 되어 실패**하므로, 구 파일을 반드시 삭제해야 한다.

### FR-9. 문서 갱신
`README.md`와 `TennisDocAI/AI_README.md`의 모듈 구조·테스트 명령 설명에 `:core:analysis`를 반영한다. **본 task에서 수정이 허용된 문서는 이 두 파일뿐이다.**

---

## 5. 인수 조건 (Acceptance Criteria)

> 모든 명령은 `TennisDocAI/`에서 실행한다.

| # | 조건 |
|---|---|
| **AC-1** | `./gradlew projects` 출력에 `:core:analysis`가 나타난다. |
| **AC-2** | `core/analysis/build.gradle.kts`에 CMake 경로·버전(`3.22.1`)·ABI 4종이 선언되어 있고, `app/build.gradle.kts`에는 `externalNativeBuild`·`ndk { abiFilters }` 블록이 **없다**. |
| **AC-3** | `./gradlew assembleDebug` 성공. |
| **AC-4** | 생성된 APK(`app/build/outputs/apk/debug/*.apk`)에 `lib/{arm64-v8a, armeabi-v7a, x86_64, x86}/libswingsense_ei.so` **4개가 모두** 포함된다(`unzip -l` 등으로 확인). |
| **AC-5** | `./gradlew test` 성공, 실패 0건. 총 테스트 수가 직전 기준선 **57건 미만이 아니다**. 이동한 4개 테스트가 `:core:analysis`에서 실행되었음을 리포트로 제시한다. |
| **AC-6** | `./gradlew verifyModuleDependencies` 성공. |
| **AC-7** | `./gradlew verifyJniBindings` 성공하며, 출력의 클래스 디스크립터가 `io/github/loje0611/tennisdoc/core/analysis/inference/EdgeImpulseNative` 이다. |
| **AC-8** | ProGuard keep 규칙의 FQCN이 `EdgeImpulseNative.kt`의 실제 `package` 선언과 일치한다. 저장소 전체에서 `io.github.loje0611.tennisdoc.inference` 문자열이 **0건**이다(`build/` 제외). |
| **AC-9** | **(변이 검증 — JNI 안전망)** `ei_jni_bridge.cpp`의 `NATIVE_CLASS_PATH`를 구 값(`.../tennisdoc/inference/EdgeImpulseNative`)으로 되돌리면 `verifyJniBindings`가 **실패**한다. 확인 후 원복하고 재통과를 보인다. |
| **AC-10** | **(변이 검증 — 조용한 통과 방지)** `.so` 하나를 삭제하거나 ABI 하나를 제외하면 `verifyJniBindings`가 **실패**한다(산출물 부재 시 건너뛰지 않음). 확인 후 원복한다. |
| **AC-11** | **(변이 검증 — 테스트 실효성)** 이동한 테스트가 `:core:analysis`에서 실제 실행됨을 보인다. 피검증 로직 한 곳을 일시 훼손하면 `:core:analysis:test`가 **실패**해야 한다. 확인 후 원복한다. |
| **AC-12** | `git show --stat`에서 `edge_impulse/` 파일들이 **rename으로 표시**된다(대량 삭제+추가가 아님). |
| **AC-13** | `edge_impulse/` 내부 파일의 **내용 변경이 0건**이다(경로 변경만). |
| **AC-14** | `app/src/main/cpp/`, `app/src/main/java/.../tennisdoc/analysis/`, `.../tennisdoc/inference/` 디렉토리가 **존재하지 않는다**. |
| **AC-15** | Room 스키마(`app/schemas/**`)·`:core:data`·`:core:model`·`:core:sensor`·`:core:ui` 소스에 변경이 없다. |
| **AC-16** | 변경 경로가 `TennisDocAI/` 내부에 한정된다. 단, 루트 `README.md`는 FR-9에 의해 허용된다. |

---

## 6. 검증 시 주의사항

- **가장 큰 위험은 "빌드가 성공하는데 기능이 죽는" 경우입니다.** `assembleDebug`가 통과해도 ⑴ `.so`가 APK에 안 들어갔거나 ⑵ 클래스 디스크립터가 어긋났거나 ⑶ keep 규칙이 죽었으면 분류가 조용히 비활성화됩니다. AC-4·AC-7·AC-8이 각각을 겨냥합니다.
- **단위 테스트는 이 결함을 잡지 못합니다.** JVM 테스트는 `.so`를 적재하지 않으며, `EdgeImpulseNative`는 적재 실패 시 예외 대신 `isAvailable = false`로 축약됩니다. 테스트 초록색을 네이티브 정상 동작의 근거로 삼지 마십시오.
- **안드로이드 라이브러리 모듈의 단위 테스트 주의**: 이동한 테스트가 `android.util.Log`를 간접 호출하면(예: `EdgeImpulseNative` 초기화 경로) `Method not mocked` 예외가 날 수 있습니다. `:app`에서는 드러나지 않던 문제가 모듈 이동으로 나타날 수 있습니다. 발생 시 원인을 리포트에 기록하십시오 — 이는 명세 결함이 아니라 **환경 차이**이며, 모듈의 `testOptions { unitTests.isReturnDefaultValues = true }` 로 대응 가능합니다.
- **`verifyJniBindings`는 `:app:mergeDebugNativeLibs`에 의존합니다.** 단독 실행 전 `assembleDebug`가 선행되었는지 확인하고, 태스크가 `SKIPPED`/`UP-TO-DATE`로 지나가지 않았는지 로그로 확인하십시오. 실제 실행 여부는 출력 문자열(`verifyJniBindings PASSED: ...`)로 판단합니다.
- **AC-12는 diff 가독성을 위한 것이 아니라 AC-13의 전제입니다.** rename으로 인식되지 않으면 29MB 변경 속에서 내용 수정 여부를 확인할 수 없습니다.
- 계측 테스트(`androidTest`)는 실기기가 필요하므로 **실행하지 않아도 됩니다.** 미실행은 "검증 불가"가 아니며 컴파일 성공으로 충분합니다.
- 네이티브 재빌드는 시간이 오래 걸립니다. 타임아웃을 실패로 오판하지 마십시오.
- 모든 변이(AC-9·AC-10·AC-11)는 **반드시 원복**하고, 원복 후 AC-3~AC-7 재통과를 확인하십시오.

## 7. 완료 정의

AC-1 ~ AC-16 전부 충족.

## 8. 실패 시 처리

본 task는 A그룹 최대 리스크이며 `BLOCKED` 가능성이 가장 높다. 3회 소진 시, **어느 단계에서 막혔는지**(모듈 인식 / CMake 구성 / `.so` 패키징 / JNI 디스크립터 / ProGuard)를 QA 리포트에 명시한다. 원인 단계가 특정되어야 PM이 명세를 개정하거나 task를 재분해할 수 있다.
