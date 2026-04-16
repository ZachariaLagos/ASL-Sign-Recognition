"""
camera_stream.py

Streams live camera frames to stdout as base64-encoded JPEG lines.
Each frame is centre-cropped to TARGET_W x TARGET_H so it fills
the JavaFX CameraPanel rectangle exactly — no black bars, no stretching.

Consumed by CameraFrameBridge / CameraPanel in the JavaFX UI.
"""

import sys
import base64
import cv2

# Must match UIConstants.CAM_W and UIConstants.CAM_H
TARGET_W = 380
TARGET_H = 460

def centre_crop(frame, target_w, target_h):
    """Crop the largest possible target_w:target_h region from the centre."""
    h, w = frame.shape[:2]
    target_ratio = target_w / target_h
    src_ratio    = w / h

    if src_ratio > target_ratio:
        # frame is wider than needed — crop sides
        new_w = int(h * target_ratio)
        x0    = (w - new_w) // 2
        frame = frame[:, x0:x0 + new_w]
    else:
        # frame is taller than needed — crop top/bottom
        new_h = int(w / target_ratio)
        y0    = (h - new_h) // 2
        frame = frame[y0:y0 + new_h, :]

    return cv2.resize(frame, (target_w, target_h), interpolation=cv2.INTER_LINEAR)


def run():
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("ERROR: Could not open camera", file=sys.stderr)
        sys.exit(2)

    # request a reasonably high resolution from the camera
    cap.set(cv2.CAP_PROP_FRAME_WIDTH,  1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

    print("Camera stream ready.", file=sys.stderr)

    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                continue

            frame = centre_crop(frame, TARGET_W, TARGET_H)

            ok, buf = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
            if ok:
                sys.stdout.write(base64.b64encode(buf.tobytes()).decode('ascii') + '\n')
                sys.stdout.flush()

    except KeyboardInterrupt:
        pass
    finally:
        cap.release()


if __name__ == "__main__":
    run()