"""
demo.py

Live ASL recognition demo with camera feed overlay.
Loads reference landmarks from scripts/reference_data/ and matches
live hand poses using Euclidean distance scoring.

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

def load_reference_data():
  gestures = {}
  for letter in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
    path = os.path.join(REFERENCE_DIR, f"{letter}.json")
    if os.path.exists(path):
      with open(path) as f:
        data = json.load(f)
        gestures[letter] = data["landmarks"]
  print(f"Loaded {len(gestures)} reference gestures")
  return gestures

def euclidean_distance(a, b):
  return math.sqrt((a["x"]-b["x"])**2 + (a["y"]-b["y"])**2 + (a["z"]-b["z"])**2)

def recognize(user_landmarks, gestures):
  best_letter = None
  best_score  = -1
  for letter, ref_landmarks in gestures.items():
    if len(user_landmarks) != len(ref_landmarks):
      continue
    total = sum(euclidean_distance(u, r) for u, r in zip(user_landmarks, ref_landmarks))
    avg   = total / len(ref_landmarks)
    score = 1.0 / (1.0 + avg)
    if score > best_score:
      best_score  = score
      best_letter = letter
  return best_letter, best_score

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

  best_letter    = None
  best_score     = 0
  MATCH_THRESHOLD = 0.8

  while True:
    ret, frame = cap.read()
    if not ret:
      continue

    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    mp_image  = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
    result    = landmarker.detect(mp_image)

    if result.hand_landmarks:
      user_landmarks = [{"x": lm.x, "y": lm.y, "z": lm.z}
                        for lm in result.hand_landmarks[0]]

      best_letter, best_score = recognize(user_landmarks, gestures)

      # Draw landmarks
      for lm in result.hand_landmarks[0]:
        h, w, _ = frame.shape
        cx, cy  = int(lm.x * w), int(lm.y * h)
        cv2.circle(frame, (cx, cy), 5, (0, 255, 0), -1)

    # --- UI overlay ---
    h, w, _ = frame.shape

    # Top bar
    cv2.rectangle(frame, (0, 0), (w, 90), (20, 20, 20), -1)

    if best_letter and result.hand_landmarks:
      color      = (0, 220, 0) if best_score >= MATCH_THRESHOLD else (0, 140, 255)
      match_text = "MATCH" if best_score >= MATCH_THRESHOLD else "LOW"

      # Big letter
      cv2.putText(frame, best_letter,
                  (20, 72), cv2.FONT_HERSHEY_SIMPLEX, 2.8, color, 4)

      # Confidence bar background
      cv2.rectangle(frame, (130, 20), (w - 20, 45), (60, 60, 60), -1)
      bar_width = int((w - 150) * best_score)
      cv2.rectangle(frame, (130, 20), (130 + bar_width, 45), color, -1)

      # Confidence text
      conf_text = f"{best_score * 100:.1f}%  {match_text}"
      cv2.putText(frame, conf_text,
                  (130, 72), cv2.FONT_HERSHEY_SIMPLEX, 0.9, color, 2)
    else:
      cv2.putText(frame, "Show your hand...",
                  (20, 55), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (100, 100, 100), 2)

    # Bottom hint
    cv2.putText(frame, "Press Q to quit",
                (10, h - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (80, 80, 80), 1)

    cv2.imshow("ASL Recognition Demo", frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
      break

  cap.release()
  cv2.destroyAllWindows()
  landmarker.close()

if __name__ == "__main__":
  run_demo()