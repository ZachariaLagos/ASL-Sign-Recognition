package aslframework.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Captures live frames from the default webcam using OpenCV and converts
 * them to JavaFX {@link Image} objects for display in {@link GameUI}.
 *
 * <p>Usage:
 * <pre>{@code
 * CameraService camera = new CameraService();
 * camera.start(frame -> ui.updateCameraFrame(frame));
 * // later...
 * camera.stop();
 * }</pre>
 *
 * <p>The native OpenCV library must be loaded before constructing this class.
 * Call {@link #loadNativeLibrary(String)} once at application startup, passing
 * the path to {@code libopencv_java4130.dylib} on your machine:
 * <pre>{@code
 * CameraService.loadNativeLibrary(
 *     System.getProperty("user.home") + "/opencv-4.13.0/build/lib/libopencv_java4130.dylib");
 * }</pre>
 */
public class CameraService {

  /** Target frame rate for the capture loop. */
  private static final int FPS = 30;

  private final VideoCapture capture;
  private ScheduledExecutorService executor;
  private Consumer<Image> frameCallback;

  /**
   * Loads the OpenCV native library from the given absolute path.
   * Must be called once before any OpenCV class is used.
   *
   * @param nativeLibPath absolute path to libopencv_java4130.dylib
   */
  public static void loadNativeLibrary(String nativeLibPath) {
    System.load(nativeLibPath);
  }

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

  /**
   * Starts the capture loop. The given callback is invoked on a background
   * thread with each new frame as a JavaFX {@link Image}.
   *
   * @param onFrame callback that receives each frame — pass {@code ui::updateCameraFrame}
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
   * Returns whether the camera is currently open.
   *
   * @return true if the camera is open and capturing
   */
  public boolean isOpen() {
    return capture.isOpened();
  }

  // ── Private ───────────────────────────────────────────────────────────────────

  /**
   * Reads one frame from the webcam, flips it horizontally (mirror view),
   * converts it to a JavaFX Image, and passes it to the frame callback.
   */
  private void captureFrame() {
    if (frameCallback == null) return;

    Mat frame = new Mat();
    if (!capture.read(frame) || frame.empty()) return;

    // Mirror the frame so it feels natural (like a selfie camera)
    Core.flip(frame, frame, 1);

    Image image = matToJavaFXImage(frame);
    if (image != null) {
      frameCallback.accept(image);
    }

    frame.release();
  }

  /**
   * Converts an OpenCV {@link Mat} to a JavaFX {@link Image}.
   * Encodes the mat as PNG bytes then decodes into a JavaFX Image.
   *
   * @param mat the OpenCV frame to convert
   * @return the converted JavaFX Image, or null if conversion fails
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