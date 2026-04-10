"""
collect_reference_data.py

Interactive tool for capturing ASL reference hand landmarks using MediaPipe.
Shows a live camera feed and captures 21 hand landmarks for each letter A-Z
when SPACE is pressed. Saves each letter's landmarks to a JSON file.

Usage:
    python3 scripts/collect_reference_data.py

Controls:
    SPACE  - capture current letter
    S      - skip current letter
    Q      - quit

Output:
    scripts/reference_data/A.json ... Z.json
"""

import cv2
import json
import os
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

MODEL_PATH = "scripts/hand_landmarker.task"
OUTPUT_DIR = "scripts/reference_data"
LETTERS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")

def collect_reference_data():
  os.makedirs(OUTPUT_DIR, exist_ok=True)

  # Configure MediaPipe hand landmarker
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

  letter_index = 0
  captured = {}

  # Skip already captured letters
  while letter_index < len(LETTERS):
    path = os.path.join(OUTPUT_DIR, f"{LETTERS[letter_index]}.json")
    if os.path.exists(path):
      print(f"Skipping {LETTERS[letter_index]} - already captured")
      letter_index += 1
    else:
      break

  print(f"\nStarting from letter: {LETTERS[letter_index] if letter_index < len(LETTERS) else 'DONE'}")
  print("Controls: SPACE = capture | S = skip | Q = quit\n")

  while letter_index < len(LETTERS):
    current_letter = LETTERS[letter_index]
    ret, frame = cap.read()
    if not ret:
      continue

    # Run MediaPipe detection
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
    result = landmarker.detect(mp_image)

    hand_detected = bool(result.hand_landmarks)

    # Draw landmarks if detected
    if hand_detected:
      for lm in result.hand_landmarks[0]:
        h, w, _ = frame.shape
        cx, cy = int(lm.x * w), int(lm.y * h)
        cv2.circle(frame, (cx, cy), 5, (0, 255, 0), -1)

    # Draw UI overlay
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (frame.shape[1], 80), (0, 0, 0), -1)
    cv2.addWeighted(overlay, 0.6, frame, 0.4, 0, frame)

    # Letter prompt
    cv2.putText(frame, f"Show letter: {current_letter}",
                (10, 35), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)

    # Progress
    progress = f"{letter_index + 1}/{len(LETTERS)}"
    cv2.putText(frame, progress,
                (frame.shape[1] - 100, 35), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (200, 200, 200), 2)

    # Hand status
    if hand_detected:
      cv2.putText(frame, "Hand detected - SPACE to capture",
                  (10, 68), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 1)
    else:
      cv2.putText(frame, "No hand detected",
                  (10, 68), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 100, 255), 1)

    # Already captured letters
    done_letters = " ".join([l for l in LETTERS[:letter_index]])
    if done_letters:
      cv2.putText(frame, f"Done: {done_letters}",
                  (10, frame.shape[0] - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (150, 150, 150), 1)

    cv2.imshow("ASL Reference Data Collector", frame)

    key = cv2.waitKey(1) & 0xFF

    if key == ord('q'):
      print("Quitting...")
      break

    elif key == ord('s'):
      print(f"Skipped {current_letter}")
      letter_index += 1

    elif key == ord(' '):
      if not hand_detected:
        print(f"No hand detected - show your hand first!")
      else:
        # Save landmarks
        landmarks = []
        for lm in result.hand_landmarks[0]:
          landmarks.append({"x": lm.x, "y": lm.y, "z": lm.z})

        output_path = os.path.join(OUTPUT_DIR, f"{current_letter}.json")
        with open(output_path, "w") as f:
          json.dump({"letter": current_letter, "landmarks": landmarks}, f, indent=2)

        captured[current_letter] = True
        print(f"Captured {current_letter} -> {output_path}")
        letter_index += 1

  cap.release()
  cv2.destroyAllWindows()
  landmarker.close()

  print(f"\nDone! Captured {len(captured)} letters.")
  print(f"Reference data saved to: {OUTPUT_DIR}/")

if __name__ == "__main__":
  collect_reference_data()