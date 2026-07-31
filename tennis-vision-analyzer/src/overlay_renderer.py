import cv2
import numpy as np
import os

# MediaPipe 33-point 랜드마크 연결 정의 (수동 정의로 의존성 제거)
POSE_CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 7), (0, 4), (4, 5), (5, 6), (6, 8), (9, 10),
    (11, 12), (11, 13), (13, 15), (15, 17), (15, 19), (15, 21), (17, 19),
    (12, 14), (14, 16), (16, 18), (16, 20), (16, 22), (18, 20),
    (11, 23), (12, 24), (23, 24),
    (23, 25), (24, 26), (25, 27), (26, 28), (27, 29), (28, 30),
    (29, 31), (30, 32), (27, 31), (28, 32)
]

MIN_GAP = 10
FONT_FACE = cv2.FONT_HERSHEY_SIMPLEX
BASE_FONT_SCALE = 1.6
MIN_FONT_SCALE = 0.6
FONT_THICKNESS = 3
BOX_PAD = 15
IMPACT_FONT_SCALE = 1.5
IMPACT_THICKNESS = 4
IMPACT_ORG = (50, 80)


def impact_banner_rect():
    """IMPACT! 배너가 차지하는 픽셀 사각형 (x1, y1, x2, y2)."""
    (tw, th), baseline = cv2.getTextSize(
        "IMPACT!", FONT_FACE, IMPACT_FONT_SCALE, IMPACT_THICKNESS
    )
    x, y = IMPACT_ORG
    # putText baseline 기준 + 두께 여유
    margin = IMPACT_THICKNESS + 2
    return (
        x - margin,
        y - th - margin,
        x + tw + margin,
        y + baseline + margin,
    )


def _text_box_rect(text_x, text_y, tw, th, pad=BOX_PAD):
    """텍스트 baseline-left (text_x, text_y)에 대한 배경 박스 사각형."""
    return (
        text_x - pad,
        text_y - th - pad,
        text_x + tw + pad,
        text_y + pad,
    )


def _rect_inside(rect, width, height):
    x1, y1, x2, y2 = rect
    return x1 >= 0 and y1 >= 0 and x2 <= width and y2 <= height


def _rects_separated(a, b, gap=MIN_GAP):
    """두 사각형이 gap 이상 떨어져 있으면 True (교집합 면적 0 + MIN_GAP)."""
    ax1, ay1, ax2, ay2 = a
    bx1, by1, bx2, by2 = b
    return ax2 + gap <= bx1 or bx2 + gap <= ax1 or ay2 + gap <= by1 or by2 + gap <= ay1


def _rect_center(rect):
    x1, y1, x2, y2 = rect
    return ((x1 + x2) / 2.0, (y1 + y2) / 2.0)


def _distance(p, q):
    return ((p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2) ** 0.5


def _clamp_text_pos(text_x, text_y, tw, th, width, height, pad=BOX_PAD):
    """박스가 프레임 안에 들어오도록 텍스트 앵커를 클램프."""
    text_x = int(min(max(text_x, pad), width - tw - pad))
    text_y = int(min(max(text_y, th + pad), height - pad))
    return text_x, text_y


def _candidate_text_positions(tx, ty, tw, th, width, height):
    """타겟 근처 후보 텍스트 앵커를 거리 순(결정적)으로 생성."""
    pad = BOX_PAD
    preferred = [
        (tx + 50, ty - 50),
        (tx + 50, ty + th + 20),
        (tx - tw - 50, ty - 50),
        (tx - tw - 50, ty + th + 20),
        (tx + 50, ty - th - 60),
        (tx - tw // 2, ty - th - 60),
        (tx + 50, ty + 80),
        (tx - tw - 50, ty + 80),
        (tx + 80, ty),
        (tx - tw - 80, ty),
        (tx, ty - th - 80),
        (tx, ty + 100),
    ]

    # 격자 후보 추가 (결정적 순서)
    step = 20
    grid = []
    for gy in range(th + pad, max(th + pad + 1, height - pad), step):
        for gx in range(pad, max(pad + 1, width - tw - pad), step):
            grid.append((gx, gy))

    seen = set()
    scored = []
    for raw in preferred + grid:
        text_x, text_y = _clamp_text_pos(raw[0], raw[1], tw, th, width, height)
        key = (text_x, text_y)
        if key in seen:
            continue
        seen.add(key)
        rect = _text_box_rect(text_x, text_y, tw, th)
        if not _rect_inside(rect, width, height):
            continue
        dist = _distance(_rect_center(rect), (tx, ty))
        scored.append((dist, text_x, text_y, rect))

    scored.sort(key=lambda t: (t[0], t[1], t[2]))
    return scored


def compute_tooltip_layout(texts, targets, width, height, banner_rect=None):
    """
    영상 I/O 없이 툴팁 텍스트 박스 배치를 계산한다.

    Args:
        texts: list[str]
        targets: list[tuple[int,int]] 타겟 마커 픽셀 좌표 (tx, ty)
        width, height: 프레임 크기
        banner_rect: IMPACT! 영역 (x1,y1,x2,y2). None이면 자동 산출.

    Returns:
        list[dict]: 배치된 각 툴팁
          {text, tx, ty, text_x, text_y, rect, font_scale, thickness}
        배치 불가 항목은 결과에 포함하지 않는다 (생략).
    """
    if banner_rect is None:
        banner_rect = impact_banner_rect()

    n = len(texts)
    if n == 0:
        return []

    # INV-5: 배율 하향 후, 그래도 안 되면 뒤에서부터 생략
    scales = []
    s = BASE_FONT_SCALE
    while s >= MIN_FONT_SCALE - 1e-9:
        scales.append(round(s, 2))
        s -= 0.2

    for font_scale in scales:
        for keep in range(n, 0, -1):
            layout = _try_place(
                texts[:keep],
                targets[:keep],
                width,
                height,
                banner_rect,
                font_scale,
            )
            if layout is not None:
                return layout

    return []


def _try_place(texts, targets, width, height, banner_rect, font_scale):
    """모든 항목을 탐욕 배치. 실패 시 None."""
    placed = []
    occupied = []

    for text, (tx, ty) in zip(texts, targets):
        (tw, th), _ = cv2.getTextSize(text, FONT_FACE, font_scale, FONT_THICKNESS)
        candidates = _candidate_text_positions(tx, ty, tw, th, width, height)
        chosen = None
        for _dist, text_x, text_y, rect in candidates:
            if not _rects_separated(rect, banner_rect, gap=MIN_GAP):
                continue
            if any(not _rects_separated(rect, other, gap=MIN_GAP) for other in occupied):
                continue
            chosen = {
                "text": text,
                "tx": int(tx),
                "ty": int(ty),
                "text_x": int(text_x),
                "text_y": int(text_y),
                "rect": tuple(int(v) for v in rect),
                "font_scale": float(font_scale),
                "thickness": FONT_THICKNESS,
                "tw": int(tw),
                "th": int(th),
            }
            break
        if chosen is None:
            return None
        placed.append(chosen)
        occupied.append(chosen["rect"])

    return placed


def _draw_tooltip(frame, item):
    """배치 결과 한 건을 프레임에 그린다."""
    tx, ty = item["tx"], item["ty"]
    text_x, text_y = item["text_x"], item["text_y"]
    font_scale = item["font_scale"]
    thickness = item["thickness"]
    text = item["text"]
    x1, y1, x2, y2 = item["rect"]
    cyan = (0, 255, 255)

    cv2.line(frame, (tx, ty), (text_x, text_y), cyan, 2)
    cv2.circle(frame, (tx, ty), 8, cyan, -1)
    cv2.circle(frame, (tx, ty), 15, cyan, 2)
    cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 0, 0), -1)
    cv2.rectangle(frame, (x1, y1), (x2, y2), cyan, 2)
    cv2.putText(
        frame, text, (text_x, text_y), FONT_FACE, font_scale, cyan, thickness, cv2.LINE_AA
    )


def render_overlay(video_path, pose_data, impact_frames=None, swing_feedbacks=None, output_path=None):
    """
    원본 영상 위에 3D 스켈레톤 포즈를 오버레이하여 새로운 영상으로 저장합니다.
    임팩트 프레임이 주어지면 붉은색 테두리와 'IMPACT!' 텍스트 효과를 추가합니다.
    swing_feedbacks가 제공되면 해당 프레임에서 영상을 일시정지(프레임 반복)하고 툴팁을 표시합니다.
    """
    if not os.path.exists(video_path):
        print(f"Error: Video file not found at {video_path}")
        return None

    if output_path is None:
        base, ext = os.path.splitext(video_path)
        output_path = base + "_analyzed" + ext

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print("Error: Could not open video for rendering.")
        return None

    fps = cap.get(cv2.CAP_PROP_FPS)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))
    banner = impact_banner_rect()

    current_frame = 0
    while True:
        ret, frame = cap.read()
        if not ret:
            break

        base_frame = frame.copy()
        frame_joints = None

        if current_frame < len(pose_data):
            frame_joints = pose_data[current_frame]

            if not np.any(np.isnan(frame_joints[0])):
                points = []
                for idx, joint in enumerate(frame_joints):
                    if np.isnan(joint[0]) or np.isnan(joint[1]):
                        points.append(None)
                        continue

                    px = int(joint[0] * width)
                    py = int(joint[1] * height)
                    points.append((px, py))

                    vis = joint[3] if len(joint) > 3 else 1.0
                    if vis > 0.5:
                        cv2.circle(base_frame, (px, py), 4, (0, 255, 0), -1)

                for connection in POSE_CONNECTIONS:
                    idx1, idx2 = connection
                    if idx1 < len(points) and idx2 < len(points):
                        if points[idx1] is None or points[idx2] is None:
                            continue

                        vis1 = frame_joints[idx1][3] if len(frame_joints[idx1]) > 3 else 1.0
                        vis2 = frame_joints[idx2][3] if len(frame_joints[idx2]) > 3 else 1.0

                        if vis1 > 0.5 and vis2 > 0.5:
                            cv2.line(base_frame, points[idx1], points[idx2], (255, 255, 255), 2)

        normal_frame = base_frame.copy()
        if impact_frames is not None and current_frame in impact_frames:
            cv2.rectangle(normal_frame, (0, 0), (width, height), (0, 0, 255), 10)
            cv2.putText(
                normal_frame,
                "IMPACT!",
                IMPACT_ORG,
                FONT_FACE,
                IMPACT_FONT_SCALE,
                (0, 0, 255),
                IMPACT_THICKNESS,
                cv2.LINE_AA,
            )

        out.write(normal_frame)

        if (
            impact_frames is not None
            and current_frame in impact_frames
            and swing_feedbacks
            and current_frame in swing_feedbacks
        ):
            pause_frames = int(fps * 2.5)
            feedbacks = swing_feedbacks[current_frame]

            pause_frame = normal_frame.copy()
            overlay = pause_frame.copy()
            cv2.rectangle(overlay, (0, 0), (width, height), (0, 0, 0), -1)
            cv2.addWeighted(overlay, 0.5, pause_frame, 0.5, 0, pause_frame)

            texts = []
            targets = []
            for fb in feedbacks:
                target_idx = fb["target_joint"]
                if frame_joints is None:
                    continue
                if np.isnan(frame_joints[target_idx][0]) or np.isnan(frame_joints[target_idx][1]):
                    continue  # EH-3: 해당 툴팁만 스킵
                tx = int(frame_joints[target_idx][0] * width)
                ty = int(frame_joints[target_idx][1] * height)
                texts.append(fb["text"])
                targets.append((tx, ty))

            layout = compute_tooltip_layout(texts, targets, width, height, banner)
            for item in layout:
                _draw_tooltip(pause_frame, item)

            for _ in range(pause_frames):
                out.write(pause_frame)

        current_frame += 1

        if current_frame % 50 == 0:
            print(f"Rendering frame {current_frame}/{total_frames}...")

    cap.release()
    out.release()
    print(f"Rendered video saved successfully to {output_path}")

    return output_path


if __name__ == "__main__":
    print("This module is intended to be imported. For full pipeline, see app.py")
