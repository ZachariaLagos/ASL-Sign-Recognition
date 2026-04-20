package aslframework.ui;

import aslframework.model.HandLandmark;
import javafx.scene.image.Image;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Captures live frames from the default webcam using OpenCV and converts
 * them to JavaFX {@link Image} objects for display in {@link GameUI}.
 *
 * <p>When landmark display is enabled via {@link #setShowLandmarks(boolean)},
 * the 21 hand landmarks are drawn directly onto the {@link Mat} frame using
 * OpenCV <em>before</em> it is converted to a JavaFX Image. This means the
 * dots are always correctly positioned regardless of how the ImageView is
 * scaled or stretched in the UI.
 */
public class CameraService {

  /** Target frame rate for the capture loop. */
  private static final int FPS = 30;

  /** Radius of each landmark dot in pixels. */
  private static final int DOT_RADIUS = 6;

  /** Colour of landmark dots (BGR — OpenCV uses BGR not RGB). */
  private static final Scalar DOT_COLOR    = new Scalar(0, 255, 136);   // bright green
  private static final Scalar BONE_COLOR   = new Scalar(255, 255, 255); // white connections

  // MediaPipe hand skeleton — pairs of landmark indices to connect
  private static final int[][] CONNECTIONS = {
      {0,1},{1,2},{2,3},{3,4},         // thumb
      {0,5},{5,6},{6,7},{7,8},         // index
      {0,9},{9,10},{10,11},{11,12},    // middle
      {0,13},{13,14},{14,15},{15,16},  // ring
      {0,17},{17,18},{18,19},{19,20},  // pinky
      {5,9},{9,13},{13,17}             // palm
  };

  private final VideoCapture capture;
  private ScheduledExecutorService executor;
  private Consumer<Image> frameCallback;

  /** Latest landmarks — updated atomically from the recognition thread. */
  private final AtomicReference<List<HandLandmark>> latestLandmarks =
      new AtomicReference<>(null);

  /** Timestamp of the last landmark update in ms. Used to auto-clear stale dots. */
  private volatile long lastLandmarkTime = 0;

  /** How long (ms) to keep showing landmarks after the last update before clearing. */
  private static final long LANDMARK_TIMEOUT_MS = 200;

  /** Whether to draw landmarks on frames. */
  private final AtomicBoolean showLandmarks = new AtomicBoolean(false);

  // ── Static loader ─────────────────────────────────────────────────────────────

  /**
   * Loads the OpenCV native library from the given absolute path.
   * Must be called once before any OpenCV class is used.
   *
   * @param nativeLibPath absolute path to libopencv_java4130.dylib
   */
  public static void loadNativeLibrary(String nativeLibPath) {
    System.load(nativeLibPath);
  }

  // ── Constructor ───────────────────────────────────────────────────────────────

  /**
   * Constructs a CameraService and opens the default webcam (index 0).
   *
   * @throws IllegalStateException if the camera cannot be opened
   */
  public CameraService() {
    capture = new VideoCapture(0);
    if (!capture.isOpened()) {
      throw new IllegalStateException(
          "Could not open camera. Make sure no other process is using it.");
    }
  }

  // ── Public API ────────────────────────────────────────────────────────────────

  /**
   * Starts the capture loop. The given callback is invoked on a background
   * thread with each new frame as a JavaFX {@link Image}.
   *
   * @param onFrame callback that receives each frame
   */
  public void start(Consumer<Image> onFrame) {
    this.frameCallback = onFrame;
    executor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "camera-capture");
      t.setDaemon(true);
      return t;
    });
    executor.scheduleAtFixedRate(this::captureFrame, 0, 1000 / FPS, TimeUnit.MILLISECONDS);
  }

  /**
   * Stops the capture loop and releases the camera.
   */
  public void stop() {
    if (executor != null && !executor.isShutdown()) {
      executor.shutdown();
      try {
        executor.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (capture.isOpened()) {
      capture.release();
    }
  }

  /**
   * Updates the landmark list to draw on the next frame.
   * Safe to call from any thread.
   *
   * @param landmarks 21 hand landmarks with normalised [0,1] coordinates
   */
  public void setLandmarks(List<HandLandmark> landmarks) {
    latestLandmarks.set(landmarks);
    if (landmarks != null) lastLandmarkTime = System.currentTimeMillis();
  }

  /**
   * Enables or disables landmark drawing on frames.
   * Safe to call from any thread.
   *
   * @param show true to draw landmarks, false to hide them
   */
  public void setShowLandmarks(boolean show) {
    showLandmarks.set(show);
    if (!show) latestLandmarks.set(null);  // clear stale dots when turned off
  }

  /**
   * Returns whether the camera is currently open.
   *
   * @return true if the camera is open
   */
  public boolean isOpen() {
    return capture.isOpened();
  }

  // ── Private ───────────────────────────────────────────────────────────────────

  /**
   * Reads one frame, flips it, optionally draws landmarks via OpenCV,
   * then converts to JavaFX Image and calls the callback.
   */
  private void captureFrame() {
    if (frameCallback == null) return;

    Mat frame = new Mat();
    if (!capture.read(frame) || frame.empty()) return;

    // Mirror the frame (selfie view)
    Core.flip(frame, frame, 1);

    // Draw landmarks directly on the Mat if enabled
    if (showLandmarks.get()) {
      // Auto-clear if no new landmarks arrived within the timeout
      if (System.currentTimeMillis() - lastLandmarkTime > LANDMARK_TIMEOUT_MS) {
        latestLandmarks.set(null);
      }
      List<HandLandmark> lms = latestLandmarks.get();
      if (lms != null && !lms.isEmpty()) {
        drawLandmarks(frame, lms);
      }
    }

    Image image = matToJavaFXImage(frame);
    if (image != null) {
      frameCallback.accept(image);
    }

    frame.release();
  }

  /**
   * Draws 21 landmark dots and skeleton connections onto the given Mat.
   * Landmark coordinates are normalised [0,1] — scaled to frame pixel size here.
   * X is already mirrored to match the flipped frame.
   */
  private void drawLandmarks(Mat frame, List<HandLandmark> landmarks) {
    int w = frame.cols();
    int h = frame.rows();

    // Convert normalised coords to pixel points
    Point[] pts = new Point[landmarks.size()];
    for (int i = 0; i < landmarks.size(); i++) {
      HandLandmark lm = landmarks.get(i);
      // Frame is already flipped — mirror X to match
      int px = (int) ((1.0 - lm.getX()) * w);
      int py = (int) (lm.getY() * h);
      pts[i] = new Point(px, py);
    }

    // Draw skeleton connections first (underneath dots)
    for (int[] conn : CONNECTIONS) {
      if (conn[0] < pts.length && conn[1] < pts.length) {
        Imgproc.line(frame, pts[conn[0]], pts[conn[1]], BONE_COLOR, 1, Imgproc.LINE_AA, 0);
      }
    }

    // Draw dots on top
    for (Point pt : pts) {
      Imgproc.circle(frame, pt, DOT_RADIUS, DOT_COLOR, -1, Imgproc.LINE_AA, 0);
    }
  }

  /**
   * Converts an OpenCV {@link Mat} to a JavaFX {@link Image}.
   */
  private Image matToJavaFXImage(Mat mat) {
    try {
      MatOfByte buffer = new MatOfByte();
      Imgcodecs.imencode(".png", mat, buffer);
      byte[] bytes = buffer.toArray();
      buffer.release();
      return new Image(new ByteArrayInputStream(bytes));
    } catch (Exception e) {
      System.err.println("Frame conversion error: " + e.getMessage());
      return null;
    }
  }
}