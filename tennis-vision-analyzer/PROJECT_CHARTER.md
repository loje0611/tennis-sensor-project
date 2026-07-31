# 🎥 비전 AI 테니스 스윙 분석기 — 프로젝트 기획서

> 📌 **이 문서는 `tennis-vision-analyzer` 서브프로젝트 범위의 기획서입니다.**
> 센서·비전 통합을 아우르는 **전체 제품 비전 및 단계별 로드맵(Phase 1~4)**은
> 루트 [`README.md`](../README.md#-제품-비전-및-단계별-로드맵)를 참조하세요.

## 1. 프로젝트 개요

### **가. 프로젝트명**
**`tennis-vision-analyzer`** — 스마트폰 카메라로 촬영한 테니스 영상에서 AI가 관절 포즈를 추출하고 스윙 역학을 정량 분석하는 솔루션. 최종적으로는 기존 안드로이드 앱에 통합됩니다.

### **나. 프로젝트 목표**
1. 하드웨어(센서/케이스) 없이 **스마트폰으로 촬영한 영상 1개**만으로 테니스 스윙 품질을 분석.
2. 기존 [테니스 센서 프로젝트](file:///home/keunu/personal-project/tennis-sensor-project/README.md)의 **SwingSenseAI 안드로이드 앱**에 새로운 기능 탭으로 최종 통합.
3. 향후 센서(IMU) 데이터와 비전(관절) 데이터를 타임스탬프로 동기화하는 **Multi-Modal Sensor Fusion**의 기반 마련.

---

## 2. 개발 전략 (Python 프로토타입)

효율적인 알고리즘 검증과 빠른 반복 개발을 위해 **2단계 전략(Python 검증 → Android 포팅)**을 채택합니다. 이 모듈(`tennis-vision-analyzer`)은 그중 **1단계(Python 프로토타입)**를 담당합니다.

```text
[ 단계 1: Python 프로토타입 (tennis-vision-analyzer) ← 이 모듈 ]
  빠른 알고리즘 검증과 파라미터 튜닝을 위한 샌드박스 환경
  MP4 영상 ─▶ MediaPipe (Python) ─▶ 관절 역학 분석 ─▶ Streamlit 그래프/영상 출력

                             ▼ (알고리즘 확정 후 SwingSenseAI 앱으로 이식)

[ 단계 2: SwingSenseAI 앱 통합 — 전체 로드맵은 루트 README 참조 ]
```

> 최종 제품 구조(센서 탭 + 비전 탭 + Fusion 탭)와 Phase 2~4 로드맵은 루트 [`README.md`](../README.md#-제품-비전-및-단계별-로드맵)에서 관리합니다.

---

## 3. 핵심 분석 기능 상세

### **기능 1: 스윙 관절 각도 분석 (Joint Angle Analysis)**
* 3개의 관절 좌표(예: 어깨-팔꿈치-손목)로 이루는 3D 각도를 프레임 단위 계산.
  * **팔 펴짐 각도**: 임팩트 시 팔이 충분히 뻗어 있는지 (어깨 → 팔꿈치 → 손목)
  * **무릎 굽힘 각도**: 하체 무게 중심 이동 및 파워 생성 (골반 → 무릎 → 발목)
  * **몸통 회전 각도**: 상체 회전(Rotation) 크기 (좌측 어깨 → 골반 중앙 → 우측 어깨)

### **기능 2: 운동 체인 (Kinetic Chain) 역학 분석**
* **올바른 체인**: 하체(골반) ➔ 몸통(어깨) ➔ 상완(팔꿈치) ➔ 전완/손목(라켓) 순서로 각속도 피크가 발생해야 함.
* 각 관절의 **각속도(Angular Velocity) 피크 타이밍**을 비교하여 올바른 체인 형성 여부 검증 및 경고 알림.

### **기능 3: 손목 궤적 (Swing Path) 3D 분석 및 샷 분류**
* 손목 좌표의 프레임별 누적 이동 궤적을 3D로 추적.
* 궤적 형태에 따라 **탑스핀(상향), 플랫(수평), 슬라이스(하향)** 샷을 자동 분류.

---

## 4. 디렉토리 구조 (Phase 1 프로토타입 기준)

```text
tennis-sensor-project/
├── SwingSenseAI/                               # (기존) Android 앱 (최종 통합 대상)
└── 🆕 tennis-vision-analyzer/                  # [신규] 비전 AI 분석기 (알고리즘 검증용)
    ├── README.md
    ├── requirements.txt
    ├── app.py                                  # Streamlit 메인 앱 (검증 UI)
    ├── src/
    │   ├── pose_extractor.py                   # MediaPipe 포즈 랜드마크 추출
    │   ├── angle_calculator.py                 # 관절 3D 각도 & 각속도 계산
    │   ├── kinetic_chain.py                    # 운동 체인 피크 타이밍 분석
    │   ├── swing_path.py                       # 손목 궤적 추적 & 분류
    │   ├── overlay_renderer.py                 # 스켈레톤 영상 렌더링
    │   └── impact_detector.py                  # 임팩트 순간 자동 감지
    └── sample_videos/                          # 테스트용 샘플 영상
```

---

## 5. 구현 명세 (Software Requirements Specification)

> **이 문서는 프로젝트의 비전·전략·로드맵(기획서)을 담습니다.**
> feature 단위의 상세 구현 요구사항(SRS)과 개발 태스크는 중앙 문서로 분리되어 관리됩니다.
> 아래를 참조하세요 (단일 진실 공급원, SSOT):
>
> - **요구사항 명세(SRS)**: [`docs/specs/`](../docs/specs/) — `TASK-001` ~ `TASK-008` (feature별 SRS)
> - **태스크 현황/의존성**: [`docs/task-board.json`](../docs/task-board.json)
> - **에이전트 개발 워크플로우**: [`docs/AGENT_WORKFLOW.md`](../docs/AGENT_WORKFLOW.md)

---

## 6. 향후 방향

이 모듈에서 검증을 마친 수학 공식·임계값·타이밍 로직은 `SwingSenseAI` 앱으로 포팅되어 "비전 분석 탭"으로 통합될 예정입니다.

전체 제품의 단계별 로드맵(Phase 2 앱 통합 · Phase 3 센서+비전 Fusion · Phase 4 AI 코칭 리포트)은 루트 [`README.md` — 제품 비전 및 단계별 로드맵](../README.md#-제품-비전-및-단계별-로드맵)에서 관리합니다.
