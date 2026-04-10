"""
landmark_extractor.py

Continuously captures camera frames and extracts 21 hand landmarks using MediaPipe.
Prints landmarks as JSON to stdout whenever a hand is detected.
Runs until the user presses 'q' in the preview window or Ctrl+C in the terminal.

Usage:
    python3 scripts/landmark_extractor.py

Output (stdout):
    JSON array of 21 landmarks per detected frame, each with x, y, z coordinates.
    Example:
    [{"x": 0.123, "y": 0.456, "z": -0.012}, ...]

Exit codes:
    0 - user quit
    2 - camera could not be opened
"""

import sys
import json
import cv2
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
import urllib.request
import os
import ssl

MODEL_PATH = "scripts/hand_landmarker.task"
MODEL_URL = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"

def download_model():
  """Download the MediaPipe hand landmarker model if not already present."""
  if not os.path.exists(MODEL_PATH):
    print("Downloading hand landmarker model...", file=sys.stderr)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    with urllib.request.urlopen(MODEL_URL, context=ctx) as response:
      with open(MODEL_PATH, 'wb') as f:
        f.write(response.read())
    print("Model downloaded.", file=sys.stderr)

def extract_landmarks():
  download_model()

  # Configure the hand landmarker
  base_options = python.BaseOptions(model_asset_path=MODEL_PATH)
  options = vision.HandLandmarkerOptions(
    base_options=base_options,
    num_hands=1,
    min_hand_detection_confidence=0.5,
    min_hand_presence_confidence=0.5,
    min_tracking_confidence=0.5
  )
  landmarker = vision.HandLandmarker.create_from_options(options)

  # Open camera
  cap = cv2.VideoCapture(0)
  if not cap.isOpened():
    print("ERROR: Could not open camera", file=sys.stderr)
    sys.exit(2)

  # Check if running headless (called from Java subprocess)
  headless = "--headless" in sys.argv

  if not headless:
    print("Camera ready - show your hand. Press 'q' to quit.", file=sys.stderr)
  else:
    print("Running in headless mode - streaming landmarks to stdout.", file=sys.stderr)

  try:
    while True:
      ret, frame = cap.read()
      if not ret:
        continue

      # Convert BGR to RGB for MediaPipe
      rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
      mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

      # Run detection
      result = landmarker.detect(mp_image)

      if result.hand_landmarks:
        # Extract 21 landmarks and print as JSON
        landmarks = []
        for landmark in result.hand_landmarks[0]:
          landmarks.append({
            "x": landmark.x,
            "y": landmark.y,
            "z": landmark.z
          })
        print(json.dumps(landmarks))
        sys.stdout.flush()

      if not headless:
        # Draw landmarks on preview window
        if result.hand_landmarks:
          for lm in result.hand_landmarks[0]:
            h, w, _ = frame.shape
            cx, cy = int(lm.x * w), int(lm.y * h)
            cv2.circle(frame, (cx, cy), 5, (0, 255, 0), -1)
          cv2.putText(frame, "Hand detected!", (10, 30),
                      cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
        else:
          cv2.putText(frame, "Show your hand...", (10, 30),
                      cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2)

        cv2.imshow("ASL Landmark Extractor", frame)

        if cv2.waitKey(1) & 0xFF == ord('q'):
          break

  except KeyboardInterrupt:
    pass
  finally:
    cap.release()
    if not headless:
      cv2.destroyAllWindows()
    landmarker.close()

if __name__ == "__main__":
  extract_landmarks()