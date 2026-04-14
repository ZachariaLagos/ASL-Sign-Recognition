"""
test_rotations.py

Tests and visualizes the rotation augmentation logic used in GestureLibrary.java.
Validates correctness of landmark rotation and shows a visual overlay of all variants.

Usage:
    python3 test_rotations.py

Requirements:
    pip install matplotlib numpy
    - A reference JSON file at scripts/reference_data/A.json (or any letter)
"""

import json
import math
import os
import sys
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches

# ── Config ─────────────────────────────────────────────────────────────────────
REFERENCE_DIR   = "scripts/reference_data"
ROTATION_ANGLES = [-15, -10, -5, 0, 5, 10, 15]   # mirrors GestureLibrary.java
TOLERANCE       = 1e-9                             # float comparison tolerance

# MediaPipe hand connections (index pairs) for drawing skeleton
HAND_CONNECTIONS = [
  (0,1),(1,2),(2,3),(3,4),          # thumb
  (0,5),(5,6),(6,7),(7,8),          # index
  (0,9),(9,10),(10,11),(11,12),     # middle
  (0,13),(13,14),(14,15),(15,16),   # ring
  (0,17),(17,18),(18,19),(19,20),   # pinky
  (5,9),(9,13),(13,17),             # palm
]


# ── Normalization + Rotation (mirrors LandmarkUtils.java exactly) ──────────────
def normalize(landmarks):
  """Subtract wrist (landmark 0) from all landmarks. Mirrors LandmarkUtils.normalize()."""
  wrist = landmarks[0]
  return [{"x": lm["x"] - wrist["x"],
           "y": lm["y"] - wrist["y"],
           "z": lm["z"] - wrist["z"]} for lm in landmarks]


def compute_centroid(landmarks):
  cx = sum(lm["x"] for lm in landmarks) / len(landmarks)
  cy = sum(lm["y"] for lm in landmarks) / len(landmarks)
  return cx, cy


def rotate_landmarks(landmarks, angle_deg):
  """Rotate landmarks around their centroid in the x/y plane. z is unchanged."""
  angle_rad = math.radians(angle_deg)
  cos_a, sin_a = math.cos(angle_rad), math.sin(angle_rad)
  cx, cy = compute_centroid(landmarks)

  rotated = []
  for lm in landmarks:
    dx, dy = lm["x"] - cx, lm["y"] - cy
    rotated.append({
      "x": cx + dx * cos_a - dy * sin_a,
      "y": cy + dx * sin_a + dy * cos_a,
      "z": lm["z"],
    })
  return rotated


def generate_variants(landmarks):
  return {angle: rotate_landmarks(landmarks, angle) for angle in ROTATION_ANGLES}


def test_normalization(landmarks):
  """After normalization, wrist (landmark 0) must be at origin (0, 0, 0)."""
  normalized = normalize(landmarks)
  assert abs(normalized[0]["x"]) < TOLERANCE, "Wrist x is not 0 after normalization"
  assert abs(normalized[0]["y"]) < TOLERANCE, "Wrist y is not 0 after normalization"
  assert abs(normalized[0]["z"]) < TOLERANCE, "Wrist z is not 0 after normalization"
  print("  PASS  wrist at origin after normalization")


def test_normalization_translation_invariant(landmarks):
  """Normalizing a translated copy must produce identical result to normalizing original."""
  translated = [{"x": lm["x"] + 0.5, "y": lm["y"] + 0.3, "z": lm["z"] + 0.1}
                for lm in landmarks]
  norm_orig  = normalize(landmarks)
  norm_trans = normalize(translated)
  for a, b in zip(norm_orig, norm_trans):
    assert abs(a["x"] - b["x"]) < TOLERANCE, "Translation invariance failed on x"
    assert abs(a["y"] - b["y"]) < TOLERANCE, "Translation invariance failed on y"
    assert abs(a["z"] - b["z"]) < TOLERANCE, "Translation invariance failed on z"
  print("  PASS  normalization is translation-invariant")


# ── Mathematical invariant tests ───────────────────────────────────────────────
def test_centroid_preserved(landmarks):
  """Centroid must not move after rotation."""
  cx0, cy0 = compute_centroid(landmarks)
  for angle in ROTATION_ANGLES:
    rotated = rotate_landmarks(landmarks, angle)
    cx1, cy1 = compute_centroid(rotated)
    assert abs(cx1 - cx0) < TOLERANCE, f"Centroid x shifted at {angle}°"
    assert abs(cy1 - cy0) < TOLERANCE, f"Centroid y shifted at {angle}°"
  print("  PASS  centroid preserved across all rotation angles")


def test_z_unchanged(landmarks):
  """Z coordinate must be identical after rotation."""
  for angle in ROTATION_ANGLES:
    rotated = rotate_landmarks(landmarks, angle)
    for orig, rot in zip(landmarks, rotated):
      assert abs(rot["z"] - orig["z"]) < TOLERANCE, \
        f"Z changed at {angle}°: {orig['z']} -> {rot['z']}"
  print("  PASS  z coordinates unchanged")


def test_distances_preserved(landmarks):
  """Euclidean distances between landmark pairs must be preserved after rotation."""
  def dist(a, b):
    return math.sqrt((a["x"]-b["x"])**2 + (a["y"]-b["y"])**2)

  for angle in ROTATION_ANGLES:
    rotated = rotate_landmarks(landmarks, angle)
    for i in range(len(landmarks)):
      for j in range(i + 1, len(landmarks)):
        d_orig = dist(landmarks[i], landmarks[j])
        d_rot  = dist(rotated[i],   rotated[j])
        assert abs(d_orig - d_rot) < TOLERANCE, \
          f"Distance changed between landmarks {i}-{j} at {angle}°"
  print("  PASS  pairwise distances preserved (rotation is isometric)")


def test_round_trip(landmarks):
  """Rotating +N then -N degrees must return to the original position."""
  for angle in ROTATION_ANGLES:
    forward  = rotate_landmarks(landmarks, angle)
    roundtrip = rotate_landmarks(forward, -angle)
    for orig, rt in zip(landmarks, roundtrip):
      assert abs(rt["x"] - orig["x"]) < TOLERANCE, \
        f"Round-trip x failed at {angle}°"
      assert abs(rt["y"] - orig["y"]) < TOLERANCE, \
        f"Round-trip y failed at {angle}°"
  print("  PASS  round-trip (+angle then -angle) returns to original")


def test_zero_rotation_identity(landmarks):
  """Rotating by 0 degrees must return landmarks identical to the original."""
  rotated = rotate_landmarks(landmarks, 0)
  for orig, rot in zip(landmarks, rotated):
    assert abs(rot["x"] - orig["x"]) < TOLERANCE
    assert abs(rot["y"] - orig["y"]) < TOLERANCE
    assert abs(rot["z"] - orig["z"]) < TOLERANCE
  print("  PASS  0-degree rotation is identity")


def test_landmark_count(landmarks):
  """Every variant must have exactly 21 landmarks."""
  for angle in ROTATION_ANGLES:
    rotated = rotate_landmarks(landmarks, angle)
    assert len(rotated) == 21, \
      f"Expected 21 landmarks at {angle}°, got {len(rotated)}"
  print("  PASS  landmark count is 21 for every variant")


# ── Visualization ──────────────────────────────────────────────────────────────
def draw_hand(ax, landmarks, color, alpha=1.0, lw=1.5, label=None):
  xs = [lm["x"] for lm in landmarks]
  ys = [lm["y"] for lm in landmarks]

  # Connections
  for a, b in HAND_CONNECTIONS:
    ax.plot([xs[a], xs[b]], [ys[a], ys[b]], color=color, alpha=alpha,
            linewidth=lw, solid_capstyle="round")

  # Landmarks
  ax.scatter(xs, ys, color=color, s=18, alpha=alpha, zorder=3)

  # Label on wrist (landmark 0)
  if label:
    ax.text(xs[0], ys[0] + 0.015, label, fontsize=7,
            color=color, ha="center", va="bottom", alpha=alpha)


def visualize_variants(letter, landmarks, variants):
  fig, axes = plt.subplots(1, 2, figsize=(14, 6))
  fig.patch.set_facecolor("#0f0f0f")
  for ax in axes:
    ax.set_facecolor("#0f0f0f")
    ax.tick_params(colors="#555")
    for spine in ax.spines.values():
      spine.set_edgecolor("#333")

  cmap   = plt.cm.plasma
  colors = [cmap(i / (len(ROTATION_ANGLES) - 1)) for i in range(len(ROTATION_ANGLES))]

  # --- Left: overlay of all variants ---
  ax = axes[0]
  for i, angle in enumerate(ROTATION_ANGLES):
    alpha = 0.55 if angle != 0 else 1.0
    lw    = 1.2  if angle != 0 else 2.2
    draw_hand(ax, variants[angle], colors[i], alpha=alpha, lw=lw,
              label=f"{angle:+d}°")

  cx, cy = compute_centroid(landmarks)
  ax.plot(cx, cy, "x", color="white", markersize=8, markeredgewidth=2, zorder=5)
  ax.set_title(f"Letter '{letter}' — All {len(ROTATION_ANGLES)} Rotation Variants",
               color="white", fontsize=12, pad=10)
  ax.set_aspect("equal")
  ax.invert_yaxis()
  ax.set_xlabel("x (normalized)", color="#888", fontsize=9)
  ax.set_ylabel("y (normalized)", color="#888", fontsize=9)

  patches = [mpatches.Patch(color=colors[i], label=f"{a:+d}°")
             for i, a in enumerate(ROTATION_ANGLES)]
  ax.legend(handles=patches, loc="lower right", fontsize=8,
            facecolor="#1a1a1a", edgecolor="#444", labelcolor="white")

  # --- Right: side-by-side extreme angles ---
  ax2 = axes[1]
  draw_hand(ax2, variants[-15], colors[0],  alpha=0.9, lw=2.0, label="-15°")
  draw_hand(ax2, variants[0],   colors[3],  alpha=0.9, lw=2.0, label="  0°")
  draw_hand(ax2, variants[15],  colors[-1], alpha=0.9, lw=2.0, label="+15°")
  ax2.plot(cx, cy, "x", color="white", markersize=8, markeredgewidth=2, zorder=5)
  ax2.set_title("Extremes: -15°, 0°, +15°", color="white", fontsize=12, pad=10)
  ax2.set_aspect("equal")
  ax2.invert_yaxis()
  ax2.set_xlabel("x (normalized)", color="#888", fontsize=9)

  patches2 = [
    mpatches.Patch(color=colors[0],  label="-15°"),
    mpatches.Patch(color=colors[3],  label="  0° (original)"),
    mpatches.Patch(color=colors[-1], label="+15°"),
  ]
  ax2.legend(handles=patches2, loc="lower right", fontsize=8,
             facecolor="#1a1a1a", edgecolor="#444", labelcolor="white")

  plt.suptitle("GestureLibrary Rotation Augmentation — Validation",
               color="white", fontsize=14, y=1.01)
  plt.tight_layout()

  out_path = f"rotation_test_{letter}.png"
  plt.savefig(out_path, dpi=150, bbox_inches="tight", facecolor=fig.get_facecolor())
  print(f"\n  Visualization saved -> {out_path}")
  plt.show()


# ── Main ───────────────────────────────────────────────────────────────────────
def load_reference(letter):
  path = os.path.join(REFERENCE_DIR, f"{letter}.json")
  if not os.path.exists(path):
    return None
  with open(path) as f:
    data = json.load(f)

  # New multi-capture format - use first capture for testing
  if "captures" in data:
    return data["captures"][0]["landmarks"]

  # Old format fallback
  return data["landmarks"]


def main():
  # Pick a letter to test - use first available in reference_data
  test_letter = None
  if os.path.isdir(REFERENCE_DIR):
    for c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
      if os.path.exists(os.path.join(REFERENCE_DIR, f"{c}.json")):
        test_letter = c
        break

  if test_letter is None:
    print("No reference data found. Run collect_reference_data.py first.")
    print("Using synthetic test landmarks instead...\n")
    import random
    random.seed(42)
    test_letter = "SYNTHETIC"
    raw_landmarks = [{"x": 0.5 + random.uniform(-0.2, 0.2),
                      "y": 0.5 + random.uniform(-0.2, 0.2),
                      "z": random.uniform(-0.05, 0.05)} for _ in range(21)]
  else:
    raw_landmarks = load_reference(test_letter)
    print(f"Loaded reference data for letter '{test_letter}' "
          f"({len(raw_landmarks)} landmarks)\n")

  # Normalize first - mirrors GestureLibrary.parseLandmarks() -> LandmarkUtils.normalize()
  landmarks = normalize(raw_landmarks)
  print(f"Normalized landmarks: wrist now at ({landmarks[0]['x']:.4f}, {landmarks[0]['y']:.4f}, {landmarks[0]['z']:.4f})\n")

  # Run tests
  print(f"Running normalization tests for '{test_letter}'...")
  test_normalization(landmarks)
  test_normalization_translation_invariant(raw_landmarks)
  print(f"\nRunning rotation invariant tests for '{test_letter}'...")
  test_landmark_count(landmarks)
  test_zero_rotation_identity(landmarks)
  test_centroid_preserved(landmarks)
  test_z_unchanged(landmarks)
  test_distances_preserved(landmarks)
  test_round_trip(landmarks)
  print(f"\nAll tests passed for '{test_letter}'!\n")

  # Run tests for all available letters
  if os.path.isdir(REFERENCE_DIR):
    all_letters = [c for c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                   if os.path.exists(os.path.join(REFERENCE_DIR, f"{c}.json"))]
    if len(all_letters) > 1:
      print(f"Running tests across all {len(all_letters)} reference letters...")
      for letter in all_letters:
        raw = load_reference(letter)
        lms = normalize(raw)
        test_normalization(lms)
        test_normalization_translation_invariant(raw)
        test_centroid_preserved(lms)
        test_z_unchanged(lms)
        test_distances_preserved(lms)
        test_round_trip(lms)
        test_landmark_count(lms)
        print(f"  -> '{letter}' OK")
      print(f"\nAll tests passed for all {len(all_letters)} letters!\n")

  # Visualize
  variants = generate_variants(landmarks)
  visualize_variants(test_letter, landmarks, variants)


if __name__ == "__main__":
  main()