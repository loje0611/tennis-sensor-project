# AI Context for TennisDocAI

이 파일은 AI 에이전트(Developer, Tester 등)가 이 프로젝트에서 작업할 때 필요한 환경 및 명령어 정보를 제공하기 위한 파일입니다.

## 1. Project Environment
- **언어 및 런타임**: Kotlin / Android (Gradle Kotlin DSL)
- **빌드 시스템**: Gradle (`./gradlew`, wrapper 사용)
- **멀티모듈**: `:app`, `:core:ui`, `:core:sensor`, `:core:data`, `:core:analysis`, `:core:vision`, `:feature:match`, `:feature:history`, `:feature:lab`
- **소스 위치**: 각 모듈 디렉토리 하위의 `src/main`, 단위 테스트는 `src/test`, 계측 테스트는 `src/androidTest`

## 2. Execution Commands
에이전트가 터미널 명령어를 실행할 때는 반드시 이 디렉토리(`TennisDocAI/`)에서 아래 명령어를 기준으로 실행해야 합니다.

- **단위 테스트 실행 (Tester Agent용, 기기 불필요)**:
  ```bash
  ./gradlew verifyModuleDependencies test assembleDebug
  ```
  (특정 모듈만: `./gradlew :app:testDebugUnitTest`)

- **계측 테스트 실행 (실기기/에뮬레이터 연결 필요)**:
  ```bash
  ./gradlew connectedAndroidTest
  ```
  > 참고: CI/에이전트 환경에 기기가 없으면 계측 테스트는 건너뛰고 단위 테스트(`./gradlew verifyModuleDependencies test assembleDebug`) 결과로 검증합니다.

- **디버그 빌드**:
  ```bash
  ./gradlew assembleDebug
  ```

## 3. Rules & Conventions
- 반드시 프로젝트 루트에 포함된 `./gradlew` wrapper를 사용하세요. 전역 `gradle` 설치본을 사용하지 마세요.
- 단위 테스트 코드는 `app/src/test/`에, JVM에 의존하지 않는 계측 테스트는 `app/src/androidTest/`에 작성합니다.
- 테스트 클래스 명명 규칙은 `*Test.kt`를 따릅니다.
