# TASK-013: JNI 바인딩을 `RegisterNatives` 방식으로 전환 (심볼 결합 제거 및 결함 수정)

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-07 | PM | 최초 작성 |

---

## 1. Overview & Scope

### 1.1 배경 — 현재 스윙 분류가 동작하지 않는다

JNI는 기본적으로 **함수 이름 규칙**으로 네이티브 함수를 찾는다. C++ 함수 이름이 `Java_<패키지>_<클래스>_<메서드>` 형태여야 하며, 여기서 패키지 구분자 `.`는 `_`로 치환된다.

현재 이 규칙이 깨져 있다.

| | 값 |
|---|---|
| C++이 내보내는 심볼 | `Java_com_example_swingsenseai_inference_EdgeImpulseNative_runClassifierNative` |
| Kotlin 클래스의 실제 위치 | `io.github.loje0611.tennisdoc.inference.EdgeImpulseNative` |
| 이름 규칙상 필요한 심볼 | `Java_io_github_loje0611_tennisdoc_inference_EdgeImpulseNative_runClassifierNative` |

C++ 심볼이 **앱 개명 이전의 패키지명**을 그대로 쓰고 있다. `JNI_OnLoad`/`RegisterNatives`가 없어 이름 기반 바인딩이 유일한 연결 수단이므로, 네이티브 메서드 호출 시 `UnsatisfiedLinkError`가 발생한다.

**빌드 산출물로 확인된 사실**(4개 ABI 모두 동일):
- 내보내진 심볼에 `Java_com_example_swingsenseai_...`가 존재한다.
- `Java_io_github_loje0611_...`는 **존재하지 않는다.**
- `JNI_OnLoad`도 **존재하지 않는다.**

### 1.2 왜 지금까지 발견되지 않았는가 — 실패가 조용하기 때문

1. `System.loadLibrary("swingsense_ei")`는 `.so` 파일 자체가 존재하므로 **성공**한다. 따라서 `isAvailable`이 `true`를 반환한다.
2. 심볼 탐색 실패는 라이브러리 적재 시점이 아니라 **네이티브 메서드를 실제로 호출하는 시점**에 드러난다.
3. 그 호출을 감싼 `catch (t: Throwable)`가 `UnsatisfiedLinkError`를 **삼키고 빈 문자열 `""`를 반환**한다.
4. 빈 문자열은 "분류 결과 없음"을 뜻하는 정상 반환값과 **구별되지 않는다.**

결과적으로 앱은 크래시하지 않고, 로그에만 오류를 남기며, 사용자에게는 **"스윙이 전혀 감지되지 않는" 상태**로 나타난다. 단위 테스트는 JVM에서 실행되어 `.so`를 적재하지 않고 추론 경로를 덮는 계측 테스트도 없어, TASK-009~012의 모든 QA가 그린이면서 이 결함을 통과시켰다.

### 1.3 목적
이름 기반 바인딩을 **`JNI_OnLoad` + `RegisterNatives` 명시적 등록 방식**으로 전환하여,

1. 현재 결함을 **수정**하고,
2. C++ 심볼 이름과 Kotlin 패키지 경로의 **암묵적 결합을 제거**하며,
3. 바인딩 실패가 **조용히 묻히지 않고 즉시 관찰 가능**하도록 만들고,
4. 동일 유형의 재발을 **기기 없이 자동 검출**하는 검증 수단을 갖춘다.

### 1.4 왜 단순 개명이 아니라 `RegisterNatives`인가
C++ 함수명을 새 패키지에 맞춰 고치는 것만으로도 현재 결함은 사라진다. 그러나 `:core:analysis` 모듈 추출(후속 작업) 시 이 클래스는 **한 번 더 이동**할 예정이며, 그때 같은 결함이 같은 방식으로 재발한다.

`RegisterNatives`는 클래스 경로를 **C++ 코드 안의 명시적 문자열 한 곳**으로 모으고, 그 문자열이 틀리면 **라이브러리 적재 자체가 실패**하게 만들 수 있다. 즉 결합이 사라지는 것이 아니라 **암묵적 결합이 명시적·검증 가능한 결합으로 바뀐다.** 이것이 본 작업의 핵심 가치다.

### 1.5 In Scope / Out of Scope

| 구분 | 항목 |
|---|---|
| **In Scope** | `JNI_OnLoad` 도입 및 `RegisterNatives` 등록, 낡은 이름 기반 심볼 제거, 바인딩 실패의 관찰 가능성 확보, 기기 불필요 자동 검증 수단 도입, 관련 문서 갱신 |
| **Out of Scope** | 추론 알고리즘·전처리·라벨 선택 로직 변경 · Edge Impulse SDK 및 모델 파일 변경 · 네이티브 라이브러리 이름(`swingsense_ei`) 변경 · ABI 목록 변경 · `:core:analysis` 모듈 이동(후속 작업) · 추론 결과의 정확도 검증 |

### 1.6 본 작업의 성격
**결함 수정 + 구조 개선**이다. 추론의 **입출력 동작(같은 입력 → 같은 라벨)은 변경되어서는 안 된다.** 변경되는 것은 Kotlin과 C++이 서로를 찾는 *방식*뿐이다.

---

## 2. Definitions & References

### 2.1 용어

| 용어 | 정의 |
|---|---|
| **이름 기반 바인딩** | JNI 기본 동작. `Java_<패키지>_<클래스>_<메서드>` 규칙에 맞는 심볼을 런타임에 탐색한다. 이름이 어긋나면 호출 시점에 `UnsatisfiedLinkError`가 난다. |
| **`JNI_OnLoad`** | `System.loadLibrary` 시 JVM이 호출하는 네이티브 진입점. 지원 JNI 버전을 반환하며, `JNI_ERR`을 반환하면 **적재가 실패**한다. |
| **`RegisterNatives`** | Kotlin/Java 메서드와 C++ 함수 포인터를 **명시적으로 연결**하는 JNI API. 함수 이름 규칙에 의존하지 않는다. |
| **클래스 디스크립터** | JNI가 클래스를 지칭할 때 쓰는 문자열. 패키지 구분자가 `/`다. 예: `io/github/loje0611/tennisdoc/inference/EdgeImpulseNative` |
| **메서드 시그니처** | JNI 표기의 인자·반환 타입. `FloatArray → String?`는 `([F)Ljava/lang/String;`에 해당한다. |
| **ABI** | 네이티브 라이브러리가 빌드되는 CPU 아키텍처. 본 프로젝트는 `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` 4종. |

### 2.2 관련 파일 (현재 위치)
- `TennisDocAI/app/src/main/cpp/ei_jni_bridge.cpp` — JNI 브리지
- `TennisDocAI/app/src/main/cpp/CMakeLists.txt` — 네이티브 빌드 (`project(swingsense_ei)`)
- `TennisDocAI/app/src/main/java/io/github/loje0611/tennisdoc/inference/EdgeImpulseNative.kt` — Kotlin 래퍼
- `TennisDocAI/app/build.gradle.kts` — `externalNativeBuild`, `abiFilters`
- `TennisDocAI/build.gradle.kts` — 기존 커스텀 검증 태스크 `verifyModuleDependencies`가 있다. **본 작업의 검증 태스크는 이 선례를 따른다.**

### 2.3 관련 문서
- `docs/PHASE2_PLAN.md` §4.1 — 본 결함의 발견 경위와 분해 근거
- `TennisDocAI/AI_README.md` — 빌드/테스트 명령 (Tester가 조회하는 파일)

### 2.4 대상
- `target_project`: `TennisDocAI`

---

## 3. Functional Requirements

### FR-1. `JNI_OnLoad`를 통한 명시적 네이티브 메서드 등록
네이티브 라이브러리는 `JNI_OnLoad`를 내보내야 하며, 그 안에서 다음을 수행한다.

1. 대상 클래스를 **클래스 디스크립터 문자열로 조회**한다.
2. 분류기 네이티브 메서드를 **`RegisterNatives`로 등록**한다. 등록 항목은 메서드 이름, JNI 시그니처, C++ 함수 포인터로 구성된다.
3. 지원하는 JNI 버전을 반환한다.

- 등록되는 메서드의 **이름·시그니처·정적 여부는 Kotlin 선언과 정확히 일치**해야 한다.
- C++ 함수의 이름은 더 이상 JNI 이름 규칙을 따를 필요가 없다. **`Java_`로 시작하는 이름을 사용하지 않는다.**

### FR-2. 바인딩 실패는 크게 실패해야 한다 (Fail Loud)
`JNI_OnLoad` 안에서 클래스 조회 또는 메서드 등록이 실패하면, **`JNI_ERR`을 반환하여 라이브러리 적재 자체를 실패**시켜야 한다.

- 실패 시 진단 가능한 로그를 남긴다(어느 단계에서 실패했는지 식별 가능해야 한다).
- 보류 중인 JNI 예외가 있다면 정리한 뒤 반환한다.
- **이 요구사항이 §1.2 문제의 근본 해결책이다.** 적재가 실패하면 Kotlin 쪽 `System.loadLibrary`가 `UnsatisfiedLinkError`를 던지고, 기존 처리에 따라 `nativeLoaded = false`가 되어 `isAvailable`이 `false`를 반환한다. 즉 **바인딩 결함이 "분류 결과 없음"이 아니라 "분류기 사용 불가"로 드러난다.**

### FR-3. 낡은 심볼 제거
전환 후 빌드된 네이티브 라이브러리에는 **`Java_com_example_swingsenseai`로 시작하는 심볼이 존재해서는 안 된다.**

- 개명 이전 패키지를 가리키는 잔재를 남기지 않는다.
- 이름 기반 바인딩용 심볼을 **함께 유지하지 않는다.** 두 방식을 병행하면 어느 쪽이 실제로 쓰이는지 불분명해져 본 작업의 목적이 훼손된다.

### FR-4. 추론 동작 보존
분류 함수의 내부 로직을 변경하지 않는다. 구체적으로 다음을 모두 유지한다.

| 항목 | 요구 동작 |
|---|---|
| 입력이 null | 빈 문자열 반환 |
| 입력 길이가 모델 입력 프레임 크기와 다름 | 경고 로그 후 빈 문자열 반환 |
| 배열 복사 중 JNI 예외 발생 | 예외를 정리하고 빈 문자열 반환 |
| 분류기 실행 실패 | 경고 로그 후 빈 문자열 반환 |
| 라벨이 0개인 모델 구성 | 빈 문자열 반환 |
| 정상 실행 | **확률이 가장 높은 라벨** 문자열 반환 |

- 최고 확률 라벨을 고르는 방식(동점 시 더 낮은 인덱스 우선)을 유지한다.
- 입력 프레임 크기 검사 기준을 변경하지 않는다.

### FR-5. Kotlin 래퍼의 실패 구분
`EdgeImpulseNative`는 다음 두 상황을 **구별 가능하게** 노출해야 한다.

| 상황 | 요구되는 관찰 결과 |
|---|---|
| 네이티브 바인딩이 성립하지 않음 (라이브러리 적재 실패 포함) | `isAvailable == false` |
| 바인딩은 정상이나 추론이 결과를 내지 못함 | `isAvailable == true`, 분류 결과는 빈 문자열 |

- 즉 **바인딩 결함이 `isAvailable == true`인 채로 숨겨져서는 안 된다.**
- 라이브러리 적재 실패 시 앱이 크래시하지 않고 폴백 동작하는 기존 성질은 유지한다.
- 공개 API(`isAvailable`, 분류 호출 함수)의 시그니처를 변경하지 않는다.

### FR-6. 기기 없이 동작하는 자동 검증 수단
바인딩 정합성을 **실기기·에뮬레이터 없이** 검증하는 자동화 수단을 프로젝트에 추가한다.

- 기존 `verifyModuleDependencies`와 동일한 성격의 **Gradle 검증 태스크**로 구현한다(이름은 구현자가 결정).
- 이 태스크는 **APK에 실제로 패키징되는 네이티브 라이브러리**를 대상으로 하며, `abiFilters`에 선언된 **모든 ABI를 검사**한다.
- 검사가 성립하려면 네이티브 빌드가 선행되어야 하므로, 태스크 간 순서가 보장되어야 한다.
- 검증 실패 시 **빌드를 실패**시키고, 어떤 ABI에서 무엇이 어긋났는지 메시지에 포함한다.
- 외부 도구(`nm`, `readelf` 등) 없이도 수행 가능해야 한다. 라이브러리 파일에서 필요한 문자열의 존재/부재를 직접 확인하는 방식으로 충분하다.

**검사해야 할 속성**:

1. `JNI_OnLoad`가 존재한다.
2. `Java_com_example_swingsenseai`로 시작하는 심볼이 존재하지 않는다.
3. 등록에 사용되는 클래스 디스크립터가 **Kotlin 클래스의 실제 위치와 일치**한다.

### FR-7. 드리프트 검출: 기대값은 Kotlin 소스에서 파생되어야 한다
FR-6의 3번 검사에서, 기대하는 클래스 디스크립터를 **검증 로직에 별도로 하드코딩해서는 안 된다.**

- 기대값은 `EdgeImpulseNative`가 선언된 **Kotlin 소스 파일의 실제 패키지 선언으로부터 도출**되어야 한다.
- **이유**: 기대값을 따로 적어두면, 패키지를 옮길 때 소스·C++·검증값 **세 곳이 모두 어긋날 수 있고** 검증이 통과해버린다. 기대값이 소스에서 파생되면, 패키지가 바뀌는 순간 기대값도 함께 바뀌므로 C++을 고치지 않은 실수가 **자동으로 검출**된다.
- 이것이 본 검증의 핵심 성질이다. 단순히 "현재 값이 맞는지" 확인하는 것이 아니라 **"앞으로 어긋나면 잡히는지"** 를 보장해야 한다.

### FR-8. 표준 검증 명령 및 문서 갱신
FR-6의 검증 태스크가 **프로젝트 표준 검증 명령에 포함**되어야 한다.

- `TennisDocAI/AI_README.md`의 테스트/빌드 명령을 갱신한다. **이 파일은 Tester가 명령을 조회하는 파일이므로 누락하면 이후 모든 task의 QA가 이 검증을 건너뛴다.**
- `docs/AGENT_WORKFLOW.md`에 테스트 명령표가 있다면 함께 갱신한다.
- 검증 태스크가 무엇을 보장하는지 한 줄 설명을 문서에 남긴다.

---

## 4. Interfaces & Data Structures

### 4.1 Kotlin 측 (시그니처 변경 금지)

```kotlin
object EdgeImpulseNative {
    val isAvailable: Boolean                                  // FR-5
    fun runClassifier(flatFeatures: FloatArray): String       // FR-4
    // 네이티브 선언: FloatArray 입력, 널 허용 String 반환
}
```

### 4.2 JNI 등록 정보

| 항목 | 값 |
|---|---|
| 클래스 디스크립터 | `EdgeImpulseNative`의 실제 패키지에서 파생 (현재: `io/github/loje0611/tennisdoc/inference/EdgeImpulseNative`) |
| 메서드 이름 | Kotlin의 네이티브 선언과 동일 |
| JNI 시그니처 | `([F)Ljava/lang/String;` |
| 네이티브 라이브러리 | `swingsense_ei` (**변경 금지** — `System.loadLibrary` 인자와 일치해야 한다) |
| 대상 ABI | `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` |

### 4.3 실패 전파 경로 (FR-2의 설계 의도)

```
클래스 조회/등록 실패
  → JNI_OnLoad가 JNI_ERR 반환
    → System.loadLibrary 실패 (UnsatisfiedLinkError)
      → nativeLoaded = false
        → isAvailable == false        ← 관찰 가능한 신호
```

이 경로가 성립해야 **바인딩 결함이 "결과 없음"으로 위장되지 않는다.**

---

## 5. UI/UX Requirements

**N/A (네이티브 브리지 계층).**

단, 본 작업으로 **스윙 분류가 실제로 동작하게 되므로** 사용자 관점의 동작은 달라진다. 이는 결함 수정의 의도된 결과이며 회귀가 아니다. 화면 구성·레이아웃·문구는 변경하지 않는다.

---

## 6. Non-Functional Requirements

| 항목 | 요구사항 |
|---|---|
| 언어 | C++17 (기존 설정 유지), Kotlin |
| 빌드 도구 | CMake 3.22.1, 기존 `externalNativeBuild` 설정 유지 |
| 신규 서드파티 라이브러리 | **추가 금지.** JNI 표준 API로 충분하다. |
| Edge Impulse SDK | **수정 금지.** 벤더 코드와 모델 파일은 손대지 않는다. |
| ABI 커버리지 | 기존 4종을 모두 유지한다. 축소하지 않는다. |
| 성능 | 등록은 라이브러리 적재 시 1회 수행되므로 추론 성능에 영향이 없어야 한다. |
| 검증 이식성 | FR-6 검증은 개발 PC(리눅스/맥)에서 외부 바이너리 도구 없이 수행 가능해야 한다. |

---

## 7. Error Handling & Edge Cases

| 상황 | 요구 동작 |
|---|---|
| 클래스 디스크립터가 실제 클래스와 불일치 | `JNI_OnLoad`가 `JNI_ERR` 반환 → 적재 실패 → `isAvailable == false` |
| 메서드 시그니처 불일치 | 등록 실패 → `JNI_ERR` 반환 → 적재 실패 |
| 라이브러리 파일 자체가 없음 (미지원 ABI 등) | 기존 동작 유지 — 크래시 없이 `isAvailable == false` |
| 분류 입력 길이 불일치 | FR-4 표에 따라 빈 문자열 반환 |
| 검증 태스크 실행 시 `.so`가 아직 빌드되지 않음 | 네이티브 빌드가 선행되도록 보장하거나, **검사 없이 성공해서는 안 된다.** 조용한 무통과(silent skip)는 결함으로 간주한다. |
| 일부 ABI만 검사됨 | 결함. 선언된 모든 ABI가 검사되어야 한다. |

---

## 8. Acceptance Criteria

> 각 항목은 **결과물의 관찰 가능한 속성**이다. 구현 코드를 다시 읽어 확인하는 방식이 아니라, 빌드·검증 태스크·산출물 검사로 확인되어야 한다.

- [ ] **AC-1** 프로젝트 표준 검증 명령(FR-8 반영 후)이 성공한다.
- [ ] **AC-2** 빌드된 네이티브 라이브러리가 **4개 ABI 모두**에 대해 생성된다 (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).
- [ ] **AC-3** 4개 ABI **모두**의 라이브러리에 `JNI_OnLoad`가 존재한다.
- [ ] **AC-4** 4개 ABI **모두**의 라이브러리에 `Java_com_example_swingsenseai`로 시작하는 심볼이 **존재하지 않는다.**
- [ ] **AC-5** 4개 ABI **모두**의 라이브러리에 `EdgeImpulseNative`의 실제 패키지에서 파생된 클래스 디스크립터 문자열이 존재한다.
- [ ] **AC-6** FR-6의 검증 태스크가 존재하며 단독 실행으로 성공한다.
- [ ] **AC-7 (변이 검증 — 필수)** C++의 클래스 디스크립터를 **의도적으로 틀린 값으로 바꾸면 검증 태스크가 실패**한다. 확인 후 반드시 원복하고, 원복 뒤 다시 성공함을 보인다.
- [ ] **AC-8 (변이 검증 — 필수)** `EdgeImpulseNative.kt`의 **패키지 선언을 일시적으로 바꾸면 검증 태스크가 실패**한다(FR-7의 드리프트 검출 성질). 확인 후 반드시 원복한다.
- [ ] **AC-9** 검증 태스크가 네이티브 빌드 산출물 없이 **조용히 통과하지 않는다.** 검사 대상을 찾지 못하면 실패한다.
- [ ] **AC-10** 네이티브 라이브러리 이름이 `swingsense_ei`로 유지되고, Kotlin의 라이브러리 적재 인자와 일치한다.
- [ ] **AC-11** `EdgeImpulseNative`의 공개 API(`isAvailable`, 분류 호출 함수)의 시그니처가 작업 전과 동일하다.
- [ ] **AC-12** FR-4 표의 6개 분기 처리가 모두 보존되어 있다.
- [ ] **AC-13** Edge Impulse SDK 디렉터리와 모델 파일에 **변경이 없다.**
- [ ] **AC-14** `abiFilters` 목록이 축소되지 않았다.
- [ ] **AC-15** `:app`과 모든 `:core:*` 모듈의 기존 단위 테스트가 **모두 통과**한다 (회귀 없음).
- [ ] **AC-16** `AI_README.md`의 검증 명령에 FR-6의 태스크가 포함되어 있다.
- [ ] **AC-17** 작업 범위 밖 파일이 수정되지 않았다. 특히 `tennis-vision-analyzer/` 등 다른 서브프로젝트에 변경이 없다.

---

## 9. Testing Instructions

### 기본 명령
`TennisDocAI/AI_README.md`의 표준 검증 명령을 사용한다. **본 작업이 그 명령을 갱신하므로(FR-8), 검증 시에는 갱신된 명령을 사용한다.**

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies <새 검증 태스크> test assembleDebug
```

### 검증 시 유의사항

- **본 작업의 핵심은 AC-7·AC-8 변이 검증이다.** 검증 태스크가 통과하는 것만으로는 그 태스크가 실효성이 있는지 알 수 없다. 일부러 틀리게 만들었을 때 **실제로 실패하는지**를 확인해야 한다. 두 변이 모두 확인 후 **반드시 원복**하고, 작업 트리가 깨끗한지 확인한다.
  - AC-7은 C++ 쪽 실수를, AC-8은 Kotlin 쪽 이동을 각각 모사한다. **둘 다 필요하다** — 하나만 검출되면 후속 모듈 이동 시 재발을 막지 못한다.
- **네이티브 빌드에는 NDK가 필요하다.** NDK가 없어 `.so`가 생성되지 않는 경우, 이는 환경 문제이며 `local.properties`·NDK 설치로 해결한다. 다만 **검증 태스크가 `.so` 부재를 이유로 조용히 통과한다면 그것은 결함**이므로(AC-9), 구분해서 판정한다.
- 4개 ABI **전부**를 확인한다. 하나만 확인하고 통과 처리하지 않는다.
- 심볼 확인은 라이브러리 파일 내 문자열 검사로 충분하다. 외부 도구가 있으면 사용해도 되나, **검증 태스크 자체는 외부 도구 없이 동작해야 한다**(FR-6).
- **추론 결과의 정확도는 본 작업의 검증 대상이 아니다.** 실기기 없이는 확인할 수 없으며, 본 작업은 "호출이 연결되는가"까지를 책임진다. 실기기가 있다면 분류 동작 확인 결과를 참고 자료로 첨부할 수 있으나 **합격/불합격 판정 근거로 삼지 않는다.**
- `test` 태스크는 모듈에 테스트가 없어도 성공한다. `BUILD SUCCESSFUL`만으로 AC-15를 판정하지 말고 **테스트 리포트의 실제 실행 건수**를 확인한다.
