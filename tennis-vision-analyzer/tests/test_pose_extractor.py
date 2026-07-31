import unittest
from unittest.mock import MagicMock, patch
import numpy as np
import sys
import os
import tempfile
import shutil

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

from pose_extractor import process_video

# patch("pose_extractor.os.path.exists") replaces the shared os.path.exists;
# keep a real reference for fallthrough checks inside side_effects.
_REAL_EXISTS = os.path.exists


def _make_landmark(x, y, z, visibility=0.9):
    lm = MagicMock()
    lm.x = x
    lm.y = y
    lm.z = z
    lm.visibility = visibility
    return lm


def _make_pose_landmarks(person_count=1, visibility=0.9):
    """Create person_count lists of 33 landmarks each."""
    people = []
    for p in range(person_count):
        landmarks = [
            _make_landmark(0.1 * (p + 1), 0.2 * (p + 1), 0.3 * (p + 1), visibility)
            for _ in range(33)
        ]
        people.append(landmarks)
    return people


class TestPoseExtractor(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.video_path = os.path.join(self.tmp_dir, "sample.mp4")
        # Touch a fake video path so exists checks can pass when we control them
        open(self.video_path, "wb").close()
        self.model_path = os.path.join(self.tmp_dir, "pose_landmarker_full.task")
        open(self.model_path, "wb").close()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir, ignore_errors=True)

    def _mock_video_capture(self, frames):
        """frames: list of ndarray-like BGR frames."""
        cap = MagicMock()
        cap.isOpened.return_value = True
        cap.get.side_effect = lambda prop: {
            5: 30.0,   # CAP_PROP_FPS
            7: len(frames),  # CAP_PROP_FRAME_COUNT
            3: 64,     # CAP_PROP_FRAME_WIDTH
            4: 64,     # CAP_PROP_FRAME_HEIGHT
            0: 0,      # CAP_PROP_POS_MSEC — force FR-4 path for frame 2+
        }.get(prop, 0)

        frame_iter = iter(frames)
        def read():
            try:
                return True, next(frame_iter)
            except StopIteration:
                return False, None
        cap.read.side_effect = read
        return cap

    def _patch_pipeline(self, frames, pose_landmarks_per_frame, model_exists=True, video_exists=True):
        """
        pose_landmarks_per_frame: list of pose_landmarks results (or None for empty).
        Each item is what detection_result.pose_landmarks should be.
        """
        detector = MagicMock()
        results = []
        for lm in pose_landmarks_per_frame:
            result = MagicMock()
            result.pose_landmarks = lm
            results.append(result)
        detector.detect_for_video.side_effect = results

        cap = self._mock_video_capture(frames)

        def exists_side_effect(path):
            if path == self.model_path or path.endswith("pose_landmarker_full.task"):
                return model_exists
            if path == self.video_path:
                return video_exists
            return _REAL_EXISTS(path)

        return detector, cap, exists_side_effect

    @patch("pose_extractor.vision.PoseLandmarker")
    @patch("pose_extractor.mp.Image")
    @patch("pose_extractor.cv2.cvtColor")
    @patch("pose_extractor.cv2.VideoCapture")
    @patch("pose_extractor.os.path.exists")
    def test_returns_frames_33_4_shape(
        self, mock_exists, mock_cap_cls, mock_cvt, mock_image, mock_landmarker
    ):
        """AC: (Frames, 33, 4) 배열을 반환한다."""
        frames = [np.zeros((64, 64, 3), dtype=np.uint8) for _ in range(3)]
        landmarks = [_make_pose_landmarks(1) for _ in range(3)]
        detector, cap, exists_fn = self._patch_pipeline(frames, landmarks)
        mock_exists.side_effect = exists_fn
        mock_cap_cls.return_value = cap
        mock_cvt.side_effect = lambda f, _: f
        mock_landmarker.create_from_options.return_value = detector

        result = process_video(self.video_path, model_path=self.model_path)

        self.assertIsNotNone(result)
        self.assertEqual(result.shape, (3, 33, 4))

    @patch("pose_extractor.vision.PoseLandmarker")
    @patch("pose_extractor.mp.Image")
    @patch("pose_extractor.cv2.cvtColor")
    @patch("pose_extractor.cv2.VideoCapture")
    @patch("pose_extractor.os.path.exists")
    def test_landmark_xyz_visibility(
        self, mock_exists, mock_cap_cls, mock_cvt, mock_image, mock_landmarker
    ):
        """AC: 각 랜드마크가 [x, y, z, visibility]를 갖는다."""
        frames = [np.zeros((64, 64, 3), dtype=np.uint8)]
        landmarks = [_make_pose_landmarks(1, visibility=0.75)]
        detector, cap, exists_fn = self._patch_pipeline(frames, landmarks)
        mock_exists.side_effect = exists_fn
        mock_cap_cls.return_value = cap
        mock_cvt.side_effect = lambda f, _: f
        mock_landmarker.create_from_options.return_value = detector

        result = process_video(self.video_path, model_path=self.model_path)

        self.assertEqual(result.shape[-1], 4)
        # person 0 landmarks use 0.1, 0.2, 0.3, 0.75
        np.testing.assert_allclose(result[0, 0], [0.1, 0.2, 0.3, 0.75])
        self.assertTrue(np.all(result[0, :, 3] == 0.75))

    @patch("pose_extractor.vision.PoseLandmarker")
    @patch("pose_extractor.mp.Image")
    @patch("pose_extractor.cv2.cvtColor")
    @patch("pose_extractor.cv2.VideoCapture")
    @patch("pose_extractor.os.path.exists")
    def test_uses_primary_person_only(
        self, mock_exists, mock_cap_cls, mock_cvt, mock_image, mock_landmarker
    ):
        """AC: 다중 인물에서 주 인물(pose_landmarks[0])만 사용한다."""
        frames = [np.zeros((64, 64, 3), dtype=np.uint8)]
        # person0: x=0.1, person1: x=0.2
        landmarks = [_make_pose_landmarks(person_count=2)]
        detector, cap, exists_fn = self._patch_pipeline(frames, landmarks)
        mock_exists.side_effect = exists_fn
        mock_cap_cls.return_value = cap
        mock_cvt.side_effect = lambda f, _: f
        mock_landmarker.create_from_options.return_value = detector

        result = process_video(self.video_path, model_path=self.model_path)

        # Primary person coords (0.1, 0.2, 0.3), not secondary (0.2, 0.4, 0.6)
        np.testing.assert_allclose(result[0, 0, :3], [0.1, 0.2, 0.3])
        self.assertFalse(np.allclose(result[0, 0, :3], [0.2, 0.4, 0.6]))

    @patch("pose_extractor.vision.PoseLandmarker")
    @patch("pose_extractor.mp.Image")
    @patch("pose_extractor.cv2.cvtColor")
    @patch("pose_extractor.cv2.VideoCapture")
    @patch("pose_extractor.os.path.exists")
    def test_undetected_frame_filled_with_nan(
        self, mock_exists, mock_cap_cls, mock_cvt, mock_image, mock_landmarker
    ):
        """AC: 미감지 프레임이 NaN으로 채워진다."""
        frames = [
            np.zeros((64, 64, 3), dtype=np.uint8),
            np.zeros((64, 64, 3), dtype=np.uint8),
        ]
        # Frame 0 detected, frame 1 empty
        landmarks = [_make_pose_landmarks(1), []]
        detector, cap, exists_fn = self._patch_pipeline(frames, landmarks)
        mock_exists.side_effect = exists_fn
        mock_cap_cls.return_value = cap
        mock_cvt.side_effect = lambda f, _: f
        mock_landmarker.create_from_options.return_value = detector

        result = process_video(self.video_path, model_path=self.model_path)

        self.assertEqual(result.shape, (2, 33, 4))
        self.assertFalse(np.isnan(result[0]).any())
        self.assertTrue(np.isnan(result[1]).all())

    @patch("pose_extractor.vision.PoseLandmarker")
    @patch("pose_extractor.mp.Image")
    @patch("pose_extractor.cv2.cvtColor")
    @patch("pose_extractor.cv2.VideoCapture")
    @patch("pose_extractor.os.path.exists")
    def test_saves_pose_npy(
        self, mock_exists, mock_cap_cls, mock_cvt, mock_image, mock_landmarker
    ):
        """AC: _pose.npy가 저장된다."""
        frames = [np.zeros((64, 64, 3), dtype=np.uint8)]
        landmarks = [_make_pose_landmarks(1)]
        detector, cap, exists_fn = self._patch_pipeline(frames, landmarks)
        mock_exists.side_effect = exists_fn
        mock_cap_cls.return_value = cap
        mock_cvt.side_effect = lambda f, _: f
        mock_landmarker.create_from_options.return_value = detector

        result = process_video(self.video_path, model_path=self.model_path)

        expected_npy = os.path.splitext(self.video_path)[0] + "_pose.npy"
        self.assertTrue(_REAL_EXISTS(expected_npy))
        loaded = np.load(expected_npy)
        np.testing.assert_array_equal(loaded, result)

    @patch("pose_extractor.os.path.exists")
    def test_missing_model_returns_none(self, mock_exists):
        """AC: 모델 부재 시 None을 반환한다."""
        mock_exists.return_value = False
        result = process_video(self.video_path, model_path="/nonexistent/model.task")
        self.assertIsNone(result)

    @patch("pose_extractor.vision.PoseLandmarker")
    @patch("pose_extractor.cv2.VideoCapture")
    @patch("pose_extractor.os.path.exists")
    def test_missing_video_returns_none(self, mock_exists, mock_cap_cls, mock_landmarker):
        """AC: 영상 부재 시 None을 반환한다."""
        def exists_fn(path):
            if path == self.model_path or path.endswith("pose_landmarker_full.task"):
                return True
            if path == "/nonexistent/video.mp4":
                return False
            return False

        mock_exists.side_effect = exists_fn
        result = process_video("/nonexistent/video.mp4", model_path=self.model_path)
        self.assertIsNone(result)
        mock_landmarker.create_from_options.assert_not_called()

    @patch("pose_extractor.vision.PoseLandmarker")
    @patch("pose_extractor.mp.Image")
    @patch("pose_extractor.cv2.cvtColor")
    @patch("pose_extractor.cv2.VideoCapture")
    @patch("pose_extractor.os.path.exists")
    def test_visibility_defaults_to_one_when_missing(
        self, mock_exists, mock_cap_cls, mock_cvt, mock_image, mock_landmarker
    ):
        """FR-5: visibility 미제공 시 1.0."""
        frames = [np.zeros((64, 64, 3), dtype=np.uint8)]
        lm = MagicMock(spec=["x", "y", "z"])  # no visibility attr
        lm.x, lm.y, lm.z = 0.5, 0.5, 0.1
        # hasattr(landmark, 'visibility') — MagicMock with spec=[] won't have visibility
        # but MagicMock(spec=["x","y","z"]) means visibility access creates AttributeError
        # Actually hasattr on MagicMock with spec: False for missing attrs
        people = [[lm] * 33]
        detector, cap, exists_fn = self._patch_pipeline(frames, [people])
        mock_exists.side_effect = exists_fn
        mock_cap_cls.return_value = cap
        mock_cvt.side_effect = lambda f, _: f
        mock_landmarker.create_from_options.return_value = detector

        result = process_video(self.video_path, model_path=self.model_path)

        self.assertAlmostEqual(result[0, 0, 3], 1.0)

    @patch("pose_extractor.vision.PoseLandmarker")
    @patch("pose_extractor.mp.Image")
    @patch("pose_extractor.cv2.cvtColor")
    @patch("pose_extractor.cv2.VideoCapture")
    @patch("pose_extractor.os.path.exists")
    def test_timestamp_retry_on_duplicate(
        self, mock_exists, mock_cap_cls, mock_cvt, mock_image, mock_landmarker
    ):
        """FR-4 / EH-2: 타임스탬프 중복 예외 시 +1 재시도."""
        frames = [
            np.zeros((64, 64, 3), dtype=np.uint8),
            np.zeros((64, 64, 3), dtype=np.uint8),
        ]
        landmarks = [_make_pose_landmarks(1), _make_pose_landmarks(1)]
        detector, cap, exists_fn = self._patch_pipeline(frames, landmarks)

        call_count = {"n": 0}
        def detect_side_effect(img, ts):
            call_count["n"] += 1
            if call_count["n"] == 2:
                raise RuntimeError("timestamp must be monotonically increasing")
            result = MagicMock()
            result.pose_landmarks = landmarks[min(call_count["n"] - 1, 1)]
            return result

        detector.detect_for_video.side_effect = detect_side_effect
        mock_exists.side_effect = exists_fn
        mock_cap_cls.return_value = cap
        mock_cvt.side_effect = lambda f, _: f
        mock_landmarker.create_from_options.return_value = detector

        result = process_video(self.video_path, model_path=self.model_path)

        self.assertIsNotNone(result)
        self.assertEqual(result.shape, (2, 33, 4))
        # First call + failed second + retry = at least 3 invocations
        self.assertGreaterEqual(detector.detect_for_video.call_count, 3)


if __name__ == "__main__":
    unittest.main()
