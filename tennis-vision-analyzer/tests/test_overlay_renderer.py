"""
TASK-007: Skeleton overlay & tooltip rendering tests.
Uses synthetic short videos + pose_data / impact_frames / swing_feedbacks.
"""
import unittest
import tempfile
import os
import sys
import shutil

import cv2
import numpy as np

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from overlay_renderer import render_overlay, POSE_CONNECTIONS


def _make_video(path, n_frames=5, width=320, height=240, fps=10, color=(40, 40, 40)):
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter(path, fourcc, fps, (width, height))
    for _ in range(n_frames):
        frame = np.full((height, width, 3), color, dtype=np.uint8)
        out.write(frame)
    out.release()
    return fps, width, height, n_frames


def _pose_all_visible(n_frames, n_joints=33):
    """Uniform pose: joints spread; all visibility 1.0."""
    pose = np.zeros((n_frames, n_joints, 4))
    for j in range(n_joints):
        pose[:, j, 0] = 0.2 + 0.6 * (j % 5) / 4.0
        pose[:, j, 1] = 0.2 + 0.6 * (j // 5) / 6.0
        pose[:, j, 2] = 0.0
        pose[:, j, 3] = 1.0
    return pose


def _read_all_frames(path):
    cap = cv2.VideoCapture(path)
    frames = []
    while True:
        ret, f = cap.read()
        if not ret:
            break
        frames.append(f)
    cap.release()
    return frames


class TestOverlayRenderer(unittest.TestCase):

    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()
        self.video_path = os.path.join(self.tmpdir, "sample.mp4")
        self.fps, self.width, self.height, self.n_frames = _make_video(self.video_path)

    def tearDown(self):
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def test_pose_connections_count(self):
        self.assertEqual(len(POSE_CONNECTIONS), 35)

    def test_missing_video_returns_none(self):
        """AC: missing video → None."""
        result = render_overlay(
            os.path.join(self.tmpdir, "nope.mp4"),
            _pose_all_visible(5),
        )
        self.assertIsNone(result)

    def test_default_output_path(self):
        """FR-1: default output `{video}_analyzed{ext}`."""
        pose = _pose_all_visible(self.n_frames)
        out = render_overlay(self.video_path, pose)
        self.assertEqual(out, os.path.join(self.tmpdir, "sample_analyzed.mp4"))
        self.assertTrue(os.path.exists(out))

    def test_green_joints_and_white_bones(self):
        """AC: vis>0.5 joints green, bones white."""
        pose = _pose_all_visible(self.n_frames)
        # Put joint 0 at known pixel for green check
        pose[:, 0, 0] = 0.5
        pose[:, 0, 1] = 0.5
        pose[:, 0, 3] = 1.0
        # Low visibility joint should not draw green at its location
        pose[:, 1, 0] = 0.1
        pose[:, 1, 1] = 0.1
        pose[:, 1, 3] = 0.3

        out = render_overlay(self.video_path, pose, output_path=os.path.join(self.tmpdir, "skel.mp4"))
        frames = _read_all_frames(out)
        self.assertEqual(len(frames), self.n_frames)

        f0 = frames[0]
        cx, cy = self.width // 2, self.height // 2
        # Green channel dominant near joint 0
        patch = f0[cy - 2:cy + 3, cx - 2:cx + 3]
        self.assertTrue(np.any(patch[:, :, 1] > 200), "expected green joint marker")

        # Low-vis joint at ~32,24 should stay near background (not bright green)
        lx, ly = int(0.1 * self.width), int(0.1 * self.height)
        low = f0[ly, lx]
        self.assertFalse(low[1] > 200 and low[0] < 50 and low[2] < 50)

        # White bone: check some white-ish pixels exist (BGR all high)
        white_mask = (f0[:, :, 0] > 240) & (f0[:, :, 1] > 240) & (f0[:, :, 2] > 240)
        self.assertTrue(np.any(white_mask), "expected white skeleton lines")

    def test_impact_red_border_and_text(self):
        """AC: impact frame gets red border and IMPACT!."""
        pose = _pose_all_visible(self.n_frames)
        out = render_overlay(
            self.video_path,
            pose,
            impact_frames=[2],
            output_path=os.path.join(self.tmpdir, "impact.mp4"),
        )
        frames = _read_all_frames(out)
        # Without swing_feedbacks: no pause duplication → same frame count
        self.assertEqual(len(frames), self.n_frames)

        impact = frames[2]
        # Red border thickness 10 at edges (BGR: blue=0, green=0, red=255)
        edge = impact[0:10, self.width // 2]
        self.assertTrue(np.any(edge[:, 2] > 200), "expected red border on impact frame")

        non_impact = frames[0]
        edge0 = non_impact[0:10, self.width // 2]
        self.assertFalse(np.all(edge0[:, 2] > 200))

    def test_pause_frames_fps_times_2_5(self):
        """AC: impact with feedbacks repeats int(fps*2.5) pause frames."""
        pose = _pose_all_visible(self.n_frames)
        feedbacks = {
            1: [{"text": "Good Swing!", "target_joint": 12}],
        }
        # Place target joint valid
        pose[:, 12, 0] = 0.4
        pose[:, 12, 1] = 0.4
        pose[:, 12, 3] = 1.0

        out = render_overlay(
            self.video_path,
            pose,
            impact_frames=[1],
            swing_feedbacks=feedbacks,
            output_path=os.path.join(self.tmpdir, "pause.mp4"),
        )
        frames = _read_all_frames(out)
        pause = int(self.fps * 2.5)
        # normal frames + pause repeats
        self.assertEqual(len(frames), self.n_frames + pause)

    def test_pause_overlay_semi_transparent(self):
        """AC: pause frames use 0.5 dark overlay (darker than normal impact frame)."""
        pose = _pose_all_visible(self.n_frames)
        pose[:, 12, :4] = [0.4, 0.4, 0.0, 1.0]
        feedbacks = {2: [{"text": "Use Hip First", "target_joint": 12}]}

        out = render_overlay(
            self.video_path,
            pose,
            impact_frames=[2],
            swing_feedbacks=feedbacks,
            output_path=os.path.join(self.tmpdir, "overlay.mp4"),
        )
        frames = _read_all_frames(out)
        pause = int(self.fps * 2.5)
        # Frame layout: 0,1,2(normal impact), then pause copies, then 3,4
        normal_impact = frames[2]
        pause_frame = frames[3]  # first pause write
        # Center region should be darker on pause (black 0.5 blend)
        c = (self.height // 2, self.width // 2)
        self.assertLess(
            float(np.mean(pause_frame[c[0] - 20:c[0] + 20, c[1] - 20:c[1] + 20])),
            float(np.mean(normal_impact[c[0] - 20:c[0] + 20, c[1] - 20:c[1] + 20])),
        )
        self.assertEqual(len(frames), self.n_frames + pause)

    def test_tooltip_cyan_styling(self):
        """AC: cyan leader/box, font_scale 1.6, thickness 3, 100px spacing (source + pixels)."""
        # Source contract checks
        src_path = os.path.join(os.path.dirname(__file__), '..', 'src', 'overlay_renderer.py')
        with open(src_path, encoding='utf-8') as f:
            src = f.read()
        self.assertIn("font_scale = 1.6", src)
        self.assertIn("thickness = 3", src)
        self.assertIn("i * 100", src)
        self.assertIn("(0, 255, 255)", src)

        pose = _pose_all_visible(self.n_frames)
        pose[:, 16, :4] = [0.3, 0.3, 0.0, 1.0]
        pose[:, 24, :4] = [0.6, 0.6, 0.0, 1.0]
        feedbacks = {
            1: [
                {"text": "Low Path", "target_joint": 16},
                {"text": "Use Hip First", "target_joint": 24},
            ],
        }
        out = render_overlay(
            self.video_path,
            pose,
            impact_frames=[1],
            swing_feedbacks=feedbacks,
            output_path=os.path.join(self.tmpdir, "tip.mp4"),
        )
        frames = _read_all_frames(out)
        pause_frame = frames[2]  # first pause after frame 1 written at index 1
        # Cyan in BGR is (255, 255, 0)? No — OpenCV BGR cyan (0,255,255) = high G and R
        cyan = (pause_frame[:, :, 0] < 40) & (pause_frame[:, :, 1] > 200) & (pause_frame[:, :, 2] > 200)
        self.assertTrue(np.any(cyan), "expected cyan tooltip/leader graphics")

    def test_nan_joint_skips_skeleton(self):
        """FR-4: NaN on first joint → skeleton omitted (no green markers)."""
        pose = _pose_all_visible(self.n_frames)
        pose[0, 0, :] = np.nan
        out = render_overlay(
            self.video_path,
            pose,
            output_path=os.path.join(self.tmpdir, "nan.mp4"),
        )
        frames = _read_all_frames(out)
        f0 = frames[0]
        # Background was (40,40,40); without skeleton should stay near that
        # Allow small codec noise
        self.assertLess(np.mean(np.abs(f0.astype(np.float32) - 40)), 15)

    def test_nan_target_skips_tooltip_without_crash(self):
        """EH-3: NaN target joint → skip tooltip, still pause."""
        pose = _pose_all_visible(self.n_frames)
        pose[:, 14, 0] = np.nan
        feedbacks = {0: [{"text": "Arm Bent(100)", "target_joint": 14}]}
        out = render_overlay(
            self.video_path,
            pose,
            impact_frames=[0],
            swing_feedbacks=feedbacks,
            output_path=os.path.join(self.tmpdir, "skip_tip.mp4"),
        )
        frames = _read_all_frames(out)
        pause = int(self.fps * 2.5)
        self.assertEqual(len(frames), self.n_frames + pause)

    def test_writer_uses_mp4v(self):
        """FR-9: fourcc mp4v in source."""
        src_path = os.path.join(os.path.dirname(__file__), '..', 'src', 'overlay_renderer.py')
        with open(src_path, encoding='utf-8') as f:
            src = f.read()
        self.assertIn("VideoWriter_fourcc(*'mp4v')", src)


if __name__ == '__main__':
    unittest.main()
