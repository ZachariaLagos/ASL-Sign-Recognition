"""
demo.py

Live ASL recognition demo with camera feed overlay.
Loads reference landmarks from scripts/reference_data/, normalizes them
relative to the wrist, and matches live hand poses using Euclidean distance
scoring - mirroring the Java MediaPipeRecognizer + LandmarkUtils pipeline exactly.

Usage:
    python3 scripts/demo.py

Controls:
    Q - quit
"""

import cv2
import json
import os
import math
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

MODEL_PATH    = "scripts/hand_landmarker.task"
REFERENCE_DIR = "scripts/reference_data"
MATCH_THRESHOLD = 0.8
ROTATION_ANGLES = [-15, -10, -5, 0, 5, 10, 15]  # mirrors GestureLibrary.java


# ── LandmarkUtils (mirrors LandmarkUtils.java) ─────────────────────────────────

def normalize(landmarks):
  wrist = landmarks[0]
  translated = [{"x": lm["x"] - wrist["x"],
                 "y": lm["y"] - wrist["y"],
                 "z": lm["z"] - wrist["z"]} for lm in landmarks]
  mid = translated[9]
  scale = math.sqrt(mid["x"]**2 + mid["y"]**2 + mid["z"]**2)
  if scale < 1e-9:
    return translated
  return [{"x": lm["x"] / scale,
           "y": lm["y"] / scale,
           "z": lm["z"] / scale} for lm in translated]


def rotate_landmarks(landmarks, angle_deg):
  """Rotate landmarks around centroid in x/y plane. Mirrors LandmarkUtils.rotateLandmarks()."""
  angle_rad = math.radians(angle_deg)
  cos_a, sin_a = math.cos(angle_rad), math.sin(angle_rad)
  cx = sum(lm["x"] for lm in landmarks) / len(landmarks)
  cy = sum(lm["y"] for lm in landmarks) / len(landmarks)
  rotated = []
  for lm in landmarks:
    dx, dy = lm["x"] - cx, lm["y"] - cy
    rotated.append({
      "x": cx + dx * cos_a - dy * sin_a,
      "y": cy + dx * sin_a + dy * cos_a,
      "z": lm["z"],
    })
  return rotated


# ── GestureLibrary (mirrors GestureLibrary.java) ───────────────────────────────

def load_reference_data():
  gestures = {}
  for letter in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
    path = os.path.join(REFERENCE_DIR, f"{letter}.json")
    if os.path.exists(path):
      with open(path) as f:
        data = json.load(f)

      variants = []

      # New multi-capture format
      for capture in data.get("captures", []):
        base = normalize(capture["landmarks"])
        for angle in ROTATION_ANGLES:
          variants.append(rotate_landmarks(base, angle))

      if variants:
        gestures[letter] = variants

  print(f"Loaded {len(gestures)} reference gestures "
        f"({len(ROTATION_ANGLES)} variants per capture)")
  return gestures


# ── MediaPipeRecognizer (mirrors MediaPipeRecognizer.java) ─────────────────────

def euclidean_distance(a, b):
  return math.sqrt((a["x"]-b["x"])**2 + (a["y"]-b["y"])**2 + (a["z"]-b["z"])**2)


def recognize(user_landmarks, gestures):
  """
  Normalize user landmarks then compare against all variants of all letters.
  Returns best matching letter and confidence score.
  Mirrors MediaPipeRecognizer.recognize().
  """
  # Normalize user landmarks - mirrors LandmarkUtils.normalize() call in recognize()
  normalized_user = normalize(user_landmarks)

  best_letter = None
  best_score  = -1

  for letter, variants in gestures.items():
    for ref_landmarks in variants:
      if len(normalized_user) != len(ref_landmarks):
        continue
      total = sum(euclidean_distance(u, r)
                  for u, r in zip(normalized_user, ref_landmarks))
      avg   = total / len(ref_landmarks)
      score = 1.0 / (1.0 + avg)
      if score > best_score:
        best_score  = score
        best_letter = letter

  return best_letter, best_score


# ── Demo loop ──────────────────────────────────────────────────────────────────

def run_demo():
  gestures = load_reference_data()
  if not gestures:
    print("No reference data found. Run collect_reference_data.py first.")
    return

  base_options = python.BaseOptions(model_asset_path=MODEL_PATH)
  options = vision.HandLandmarkerOptions(
    base_options=base_options,
    num_hands=1,
    min_hand_detection_confidence=0.5,
    min_hand_presence_confidence=0.5,
    min_tracking_confidence=0.5
  )
  landmarker = vision.HandLandmarker.create_from_options(options)

  cap = cv2.VideoCapture(0)
  if not cap.isOpened():
    print("ERROR: Could not open camera")
    return

  print("Demo running - show ASL letters to the camera. Press Q to quit.\n")

  best_letter = None
  best_score  = 0.0

  while True:
    ret, frame = cap.read()
    if not ret:
      continue

    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    mp_image  = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
    result    = landmarker.detect(mp_image)

    hand_detected = bool(result.hand_landmarks)

    if hand_detected:
      user_landmarks = [{"x": lm.x, "y": lm.y, "z": lm.z}
                        for lm in result.hand_landmarks[0]]
      best_letter, best_score = recognize(user_landmarks, gestures)

      # Draw landmarks
      for lm in result.hand_landmarks[0]:
        h, w, _ = frame.shape
        cx, cy  = int(lm.x * w), int(lm.y * h)
        cv2.circle(frame, (cx, cy), 5, (0, 255, 0), -1)

    # ── UI overlay ──────────────────────────────────────────────────────────
    h, w, _ = frame.shape

    # Top bar
    cv2.rectangle(frame, (0, 0), (w, 90), (20, 20, 20), -1)

    if best_letter and hand_detected:
      color      = (0, 220, 0) if best_score >= MATCH_THRESHOLD else (0, 140, 255)
      match_text = "MATCH" if best_score >= MATCH_THRESHOLD else "LOW"

      # Big letter
      cv2.putText(frame, best_letter,
                  (20, 72), cv2.FONT_HERSHEY_SIMPLEX, 2.8, color, 4)

      # Confidence bar background
      cv2.rectangle(frame, (130, 20), (w - 20, 45), (60, 60, 60), -1)
      bar_width = int((w - 150) * best_score)
      cv2.rectangle(frame, (130, 20), (130 + bar_width, 45), color, -1)

      # Confidence + match status
      conf_text = f"{best_score * 100:.1f}%  {match_text}"
      cv2.putText(frame, conf_text,
                  (130, 72), cv2.FONT_HERSHEY_SIMPLEX, 0.9, color, 2)
    else:
      cv2.putText(frame, "Show your hand...",
                  (20, 55), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (100, 100, 100), 2)

    # Bottom hints
    cv2.putText(frame, f"Threshold: {MATCH_THRESHOLD*100:.0f}% | Variants: {len(ROTATION_ANGLES)} rotations | Q to quit",
                (10, h - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (80, 80, 80), 1)

    cv2.imshow("ASL Recognition Demo", frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
      break

  cap.release()
  cv2.destroyAllWindows()
  landmarker.close()


if __name__ == "__main__":
  run_demo()