# TASK-028 — `:feature:lab` 내비게이션 통합 (`AppRoutes.LAB` 및 메인 BottomBar 배선)

## Revision History
| Rev | Date | Author | 사유 |
|---|---|---|---|
| v1 | 2026-08-14 | PM | 최초 작성 (LabScreen을 :app 메인 내비게이션 및 바텀바에 통합 배선) |

---

## 1. 개요 및 범위 (Overview & Scope)

### 1.1 개요
본 명세서는 Phase 2 C그룹(`TASK-026`, `TASK-027`)에서 구축된 `:feature:lab`의 실시간 포즈 분석 화면(`LabScreen`)을 `:app` 모듈의 메인 내비게이션 시스템(`AppRoutes`, `AppNavHost`)에 통합하여 사용자가 바텀 내비게이션 바를 통해 진입할 수 있도록 배선하는 작업을 정의합니다.

### 1.2 범위
- `io.github.loje0611.tennisdoc.navigation.AppRoutes`에 `const val LAB = "lab"` 라우트 상수 추가.
- `AppNavHost`의 Scaffold `NavigationBar`에 `Lab` 탭 항목 추가 (아이콘, 라벨, 테마 스타일, 탭 이동 및 백스택 복원 로직).
- `showBottomBar` 가시성 판단 조건에 `AppRoutes.LAB` 포함.
- `NavHost` 내 `composable(AppRoutes.LAB)` 등록 및 `:feature:lab` 모듈의 `LabScreen` 호출 연결.
- `AppRoutesContractTest` 및 내비게이션 관련 단위 테스트 갱신 및 검증.

---

## 2. 정의 및 참조 (Definitions & References)

### 2.1 주요 정의
- **`AppRoutes`**: 앱 전체에서 사용하는 내비게이션 라우트 문자열 상수를 보유한 단일 정의 객체.
- **`AppNavHost`**: Jetpack Compose Navigation 기반의 메인 화면 호스트 및 바텀바 컨테이너.
- **`LabScreen`**: `:feature:lab` 모듈의 CameraX 480p 라이브 프리뷰 및 MediaPipe 실시간 3D 스켈레톤 오버레이 화면.

### 2.2 참고 문서
- CameraX 파이프라인 명세: [`docs/specs/TASK-027-camerax-frame-pipeline.md`](TASK-027-camerax-frame-pipeline.md)
- History 내비게이션 이관 명세: [`docs/specs/TASK-017-feature-history-module.md`](TASK-017-feature-history-module.md)
- Phase 2 계획서: [`docs/PHASE2_PLAN.md`](../PHASE2_PLAN.md)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-1: `AppRoutes` 라우트 상수 추가
- `AppRoutes.kt`에 `const val LAB = "lab"`을 추가한다.

### FR-2: `AppNavHost` 바텀바 가시성 조건 갱신
- `AppNavHost.kt`의 `showBottomBar` 조건식에 `currentRoute == AppRoutes.LAB`을 추가하여 Lab 화면에서도 바텀바가 유지되도록 한다.

### FR-3: `NavigationBar` 내 `Lab` 탭 항목 추가
- 바텀 내비게이션 바에 `Lab` 아이템을 배치한다 (`Lab` ↔ `History` ↔ `Settings` 순서).
- 선택 조건: `currentRoute == AppRoutes.LAB`
- 탭 클릭 시 이동:
  ```kotlin
  navController.navigate(AppRoutes.LAB) {
      popUpTo(navController.graph.findStartDestination().id) {
          saveState = true
      }
      launchSingleTop = true
      restoreState = true
  }
  ```
- 테마 스타일:
  - 아이콘: `Icons.Filled.Sensors` 또는 `Icons.Filled.Science` / `Icons.Filled.Videocam`
  - 선택 시 하이라이트: `SwingTheme.colors.neonGreen` (또는 `SwingTheme.colors.electricCyanSlice`) 글로우 효과 및 볼드 텍스트 적용.

### FR-4: `NavHost` 라우트 등록 및 `LabScreen` 연결
- `NavHost` 내에 `composable(AppRoutes.LAB)` 블록을 추가한다.
- `:feature:lab` 패키지의 `LabScreen`을 임포트하여 호출하며, Scaffold의 `innerPadding`을 `contentPadding` 또는 modifier 패딩으로 전달한다.

### FR-5: 내비게이션 계약 단위 테스트 갱신
- `AppRoutesContractTest`에 `AppRoutes.LAB`의 존재 및 `"lab"` 값 일치 검증을 추가한다.
- 기존 Match 비활성화 테스트(`AppRoutesMatchDeactivationTest`) 및 History 스모크 테스트의 회귀가 없음을 검증한다.

---

## 4. 인터페이스 및 데이터 구조 (Interfaces & Data Structures)

### 4.1 `AppRoutes.kt`
```kotlin
package io.github.loje0611.tennisdoc.navigation

object AppRoutes {
    const val LAB = "lab"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ENGINEERING_MODE = "engineering_mode"
    const val SESSION_DETAIL = "session_detail/{sessionId}"

    fun sessionDetail(sessionId: String): String = "session_detail/$sessionId"
}
```

### 4.2 `AppNavHost.kt` 라우트 등록부
```kotlin
composable(AppRoutes.LAB) {
    LabScreen(
        modifier = Modifier.padding(innerPadding)
    )
}
```

---

## 5. UI/UX 요구사항

- **바텀바 일관성**: 기존 `History`, `Settings` 탭과 동일한 디자인 토큰(글로우 섀도우, 폰트, 인디케이터 투명화)을 적용.
- **아이콘 및 라벨**:
  - 라벨 텍스트: `"Lab"`
  - 선택 시 폰트 굵기: `FontWeight.ExtraBold`, 미선택 시 `FontWeight.Medium`

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

### 6.1 모듈 아키텍처 규칙 준수
- `:app` 모듈이 `:feature:lab`을 참조하여 `LabScreen`을 호출하는 것은 선언된 모듈 의존성 그래프와 일치해야 한다 (`verifyModuleDependencies` 통과).

---

## 7. 오류 처리 및 엣지 케이스 (Error Handling & Edge Cases)

- **상태 보존 (State Restoration)**: 탭 간 전환 시 `saveState = true` 및 `restoreState = true`를 통해 탭 복귀 시 프리뷰 재초기화 부하 최소화.
- **초기 로딩 시 바텀바**: `currentRoute == null` 처리 유지로 첫 프레임 깜빡임 방지.

---

## 8. 인수 조건 (Acceptance Criteria)

- [ ] **AC-1**: `AppRoutes.LAB` 상수가 `"lab"`으로 정의된다.
- [ ] **AC-2**: `AppNavHost`의 `NavigationBar`에 `Lab` 탭이 렌더링되고, 클릭 시 `AppRoutes.LAB`으로 이동한다.
- [ ] **AC-3**: `AppNavHost` 내 `composable(AppRoutes.LAB)`에서 `LabScreen`이 정상적으로 로드된다.
- [ ] **AC-4**: `AppRoutesContractTest`에서 `AppRoutes.LAB` 검증이 통과한다.
- [ ] **AC-5**: `./gradlew :app:test verifyModuleDependencies assembleDebug` 명령이 0 failures로 통과한다.

---

## 9. 테스트 지침 (Testing Instructions)

```bash
cd TennisDocAI
./gradlew :app:test verifyModuleDependencies assembleDebug
```
