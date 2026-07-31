# AI Context for Tennis Sensor Case

이 파일은 AI 에이전트(Developer, Tester 등)가 이 프로젝트에서 작업할 때 필요한 환경 및 명령어 정보를 제공하기 위한 파일입니다.

## 1. Project Environment
- **성격**: 기구(Mechanical) / CAD 서브프로젝트
- **모델링**: OpenSCAD (`tennis_sensor_case.scad`) → 렌더링 산출물 `*.stl`, `*.png`
- **검증 스크립트**: `verify_geometry.py` (Python 3, 외부 의존성 없음)

## 2. Execution Commands
아래 명령은 이 디렉토리(`tennis-sensor-case/`)에서 실행합니다.

- **지오메트리 검증 (Tester Agent용 테스트)**:
  ```bash
  python3 verify_geometry.py
  ```
  > 파라미터 간 치수/공차 제약을 검사하며, 하나라도 실패하면 **exit code 1**, 모두 통과하면 **exit code 0**을 반환합니다. Tester는 이 종료 코드로 합격/불합격을 판정합니다.

- **STL 재생성 (OpenSCAD 설치 시)**:
  ```bash
  openscad -o assembly.stl tennis_sensor_case.scad
  ```

## 3. Rules & Conventions
- 치수/공차 파라미터를 변경할 때는 반드시 `verify_geometry.py`의 해당 상수도 함께 갱신하고 검증을 통과시켜야 합니다.
- 형상 로직 변경 후에는 관련 `*.stl` / `*.png` 산출물을 재생성해 커밋합니다.
- 자동 유닛 테스트 프레임워크는 사용하지 않으며, 검증은 전적으로 `verify_geometry.py`로 수행합니다.
