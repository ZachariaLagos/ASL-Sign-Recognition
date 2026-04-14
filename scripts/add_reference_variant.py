"""
add_reference_variant.py

Interactive tool for adding a new hand landmark capture to an existing
reference data JSON file. Allows multiple contributors to add their own
hand captures for any ASL letter without overwriting existing data.

Usage:
    python3 scripts/add_reference_variant.py

Controls:
    SPACE  - capture current hand pose
    R      - retry / discard and recapture
    Q      - quit without saving

Output:
    Appends a new capture entry to scripts/reference_data/<LETTER>.json
"""

import cv2
import json
import os
import sys
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

MODEL_PATH    = "scripts/hand_landmarker.task"
REFERENCE_DIR = "scripts/reference_data"
LETTERS       = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")


def load_existing(path, letter):
    """Load existing JSON file, handling both old and new formats."""
    if not os.path.exists(path):
        # No file yet - create fresh new format
        return {"letter": letter, "captures": []}

    with open(path) as f:
        data = json.load(f)

    # Handle old format (pre-migration) gracefully
    if "landmarks" in data and "captures" not in data:
        print(f"  WARNING: {letter}.json is in old format - run migrate_reference_data.py first")
        sys.exit(1)

    return data


def get_contributor_name():
    """Prompt user for contributor name."""
    while True:
        name = input("Enter contributor name (e.g. tavish, zachary, chester): ").strip().lower()
        if name:
            return name
        print("  Name cannot be empty.")


def get_target_letter():
    """Prompt user to select a letter to capture."""
    while True:
        letter = input("Enter the letter to add a variant for (A-Z): ").strip().upper()
        if letter in LETTERS:
            return letter
        print(f"  Invalid letter '{letter}'. Please enter a single letter A-Z.")


def show_existing_captures(data, letter):
    """Print a summary of existing captures for the letter."""
    captures = data.get("captures", [])
    if not captures:
        print(f"  No existing captures for '{letter}'.")
    else:
        print(f"  Existing captures for '{letter}': {len(captures)}")
        for i, c in enumerate(captures):
            print(f"    [{i+1}] contributor: {c['contributor']}")


def run_capture(landmarker, letter, contributor):
    """
    Opens camera feed and waits for user to capture a hand pose.
    Returns the captured landmarks or None if user quits.
    """
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("ERROR: Could not open camera")
        return None

    print(f"\nCamera ready. Show letter '{letter}' and press SPACE to capture.")
    print("Controls: SPACE = capture | R = retry | Q = quit\n")

    captured_landmarks = None
    confirmed = False

    while not confirmed:
        ret, frame = cap.read()
        if not ret:
            continue

        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image  = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        result    = landmarker.detect(mp_image)

        hand_detected = bool(result.hand_landmarks)

        # Draw landmarks
        if hand_detected:
            for lm in result.hand_landmarks[0]:
                h, w, _ = frame.shape
                cx, cy  = int(lm.x * w), int(lm.y * h)
                cv2.circle(frame, (cx, cy), 5, (0, 255, 0), -1)

        # UI overlay
        h, w, _ = frame.shape
        cv2.rectangle(frame, (0, 0), (w, 90), (20, 20, 20), -1)

        if captured_landmarks is None:
            # Waiting for capture
            cv2.putText(frame, f"Show letter: {letter}",
                        (20, 40), cv2.FONT_HERSHEY_SIMPLEX, 1.2, (255, 255, 255), 2)
            status_color = (0, 255, 0) if hand_detected else (0, 100, 255)
            status_text  = "Hand detected - SPACE to capture" if hand_detected else "No hand detected"
            cv2.putText(frame, status_text,
                        (20, 75), cv2.FONT_HERSHEY_SIMPLEX, 0.6, status_color, 1)
        else:
            # Showing captured result - waiting for confirmation
            cv2.putText(frame, f"Captured! SPACE to confirm | R to retry",
                        (20, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 220, 0), 2)
            cv2.putText(frame, f"Contributor: {contributor}  |  Letter: {letter}",
                        (20, 75), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (180, 180, 180), 1)

        cv2.putText(frame, "Q to quit",
                    (10, h - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (80, 80, 80), 1)

        cv2.imshow(f"Add Variant - {letter} ({contributor})", frame)

        key = cv2.waitKey(1) & 0xFF

        if key == ord('q'):
            print("Quitting without saving.")
            cap.release()
            cv2.destroyAllWindows()
            return None

        elif key == ord('r'):
            if captured_landmarks is not None:
                print("Retrying capture...")
                captured_landmarks = None

        elif key == ord(' '):
            if captured_landmarks is None:
                # First space - capture
                if not hand_detected:
                    print("No hand detected - show your hand first!")
                else:
                    captured_landmarks = [
                        {"x": lm.x, "y": lm.y, "z": lm.z}
                        for lm in result.hand_landmarks[0]
                    ]
                    print(f"Captured {len(captured_landmarks)} landmarks. "
                          f"Press SPACE to confirm or R to retry.")
            else:
                # Second space - confirm
                confirmed = True

    cap.release()
    cv2.destroyAllWindows()
    return captured_landmarks


def save_capture(path, data, contributor, landmarks):
    """Append new capture to the JSON file."""
    data["captures"].append({
        "contributor": contributor,
        "landmarks": landmarks
    })
    with open(path, "w") as f:
        json.dump(data, f, indent=2)


def main():
    os.makedirs(REFERENCE_DIR, exist_ok=True)

    # Configure MediaPipe
    base_options = python.BaseOptions(model_asset_path=MODEL_PATH)
    options = vision.HandLandmarkerOptions(
        base_options=base_options,
        num_hands=1,
        min_hand_detection_confidence=0.5,
        min_hand_presence_confidence=0.5,
        min_tracking_confidence=0.5
    )
    landmarker = vision.HandLandmarker.create_from_options(options)

    print("=== ASL Reference Variant Capture Tool ===\n")

    contributor = get_contributor_name()
    print()

    while True:
        letter = get_target_letter()
        path   = os.path.join(REFERENCE_DIR, f"{letter}.json")
        data   = load_existing(path, letter)

        print()
        show_existing_captures(data, letter)
        print()

        # Check if contributor already has a capture for this letter
        existing_contributors = [c["contributor"] for c in data["captures"]]
        if contributor in existing_contributors:
            overwrite = input(f"  '{contributor}' already has a capture for '{letter}'. "
                              f"Add another? (y/n): ").strip().lower()
            if overwrite != 'y':
                print("Skipping.")
            else:
                landmarks = run_capture(landmarker, letter, contributor)
                if landmarks:
                    save_capture(path, data, contributor, landmarks)
                    print(f"\nSaved! {letter}.json now has "
                          f"{len(data['captures'])} capture(s).")
        else:
            landmarks = run_capture(landmarker, letter, contributor)
            if landmarks:
                save_capture(path, data, contributor, landmarks)
                print(f"\nSaved! {letter}.json now has "
                      f"{len(data['captures'])} capture(s).")

        print()
        again = input("Capture another letter? (y/n): ").strip().lower()
        if again != 'y':
            break

    landmarker.close()
    print("\nDone!")


if __name__ == "__main__":
    main()