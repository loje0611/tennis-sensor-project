# AI Context for Tennis Sensor Hardware

이 파일은 AI 에이전트(Developer, Tester 등)가 이 프로젝트에서 작업할 때 필요한 환경 및 명령어 정보를 제공하기 위한 파일입니다.

## 1. Project Environment
- **성격**: 하드웨어 문서 저장소 (Documentation-only)
- **내용물**: 부품 목록(`BOM.md`), 회로 스키매틱(`PINOUT_AND_SCHEMATIC.md`, `schematic.png`), 메카니컬 사양(`MECHANICAL_SPEC.md`), 차세대 사양(`NEXT_GEN_HARDWARE_SPEC_v2.0.md`)
- **실행 코드/빌드 산출물 없음.**

## 2. Execution Commands
- **자동 테스트 명령 없음 (`test_command: null`).**
- 이 서브프로젝트에는 실행 가능한 테스트 하니스가 없습니다. Tester Agent는 테스트 스크립트를 실행하는 대신, 명세(spec)의 **Acceptance Criteria** 각 항목을 문서 리뷰로 검증하고 그 결과를 QA 리포트에 기록해야 합니다.

## 3. Rules & Conventions
- 변경은 Markdown 문서 및 스키매틱 이미지에 한정됩니다.
- 검증 기준: 문서 간 일관성(예: `BOM.md`의 부품이 `PINOUT_AND_SCHEMATIC.md`의 연결과 일치), 그리고 명세의 Acceptance Criteria 충족 여부.
- 핀아웃/치수 등 수치를 수정할 때는 이를 참조하는 다른 문서(`MECHANICAL_SPEC.md` 등)도 함께 갱신해 정합성을 유지합니다.
