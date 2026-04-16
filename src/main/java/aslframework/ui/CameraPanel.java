package aslframework.ui;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live camera feed panel for the JavaFX UI.
 *
 * <p>Person C owns this class. Launches {@code scripts/camera_stream.py}
 * as a subprocess. Python already has macOS camera permission (used by
 * the recognition pipeline), so this approach works on Apple Silicon
 * where Java webcam libraries fail due to missing ARM64 native binaries.
 *
 * <p>Frames arrive as base64-encoded JPEG lines on stdout, decoded and
 * rendered into a JavaFX {@link ImageView} on the FX Application Thread.
 * No landmark detection, no overlays — plain live camera view only.
 *
 * <p>Usage:
 * <pre>{@code
 * CameraPanel panel = new CameraPanel();
 * leftPanel.getChildren().add(panel.getPane(UIConstants.GREEN));
 * panel.start();
 * // later…
 * panel.stop();
 * }</pre>
 */
public class CameraPanel {

  private static final String SCRIPT_PATH = "scripts/camera_stream.py";

  private final ImageView     imageView;
  private final StackPane     container;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread              thread;
  private Process             process;

  /**
   * Constructs the camera panel. Does not open the camera yet —
   * call {@link #start()} to begin streaming.
   */
  public CameraPanel() {
    imageView = new ImageView();
    imageView.setPreserveRatio(false);
    imageView.setSmooth(true);

    container = new StackPane(imageView);
    container.setPrefSize(UIConstants.CAM_W, UIConstants.CAM_H);
    container.setMinSize(UIConstants.CAM_W, UIConstants.CAM_H);
    container.setMaxSize(UIConstants.CAM_W, UIConstants.CAM_H);
    container.setStyle("-fx-background-color:#0d0d1a; -fx-background-radius:16;");

    // clip so nothing leaks outside the rounded border
    javafx.scene.shape.Rectangle clip =
        new javafx.scene.shape.Rectangle(UIConstants.CAM_W, UIConstants.CAM_H);
    clip.setArcWidth(16);
    clip.setArcHeight(16);
    container.setClip(clip);

    // bind ImageView to always fill the container exactly
    imageView.fitWidthProperty().bind(container.widthProperty());
    imageView.fitHeightProperty().bind(container.heightProperty());
  }

  /**
   * Returns the styled {@link StackPane} with the given accent border colour.
   *
   * @param accentColor hex border colour matching the current game mode
   * @return the camera panel pane ready to add to the scene graph
   */
  public StackPane getPane(String accentColor) {
    container.setStyle(
        "-fx-background-color:#0d0d1a;"
            + "-fx-background-radius:16;"
            + "-fx-border-color:" + accentColor + ";"
            + "-fx-border-width:2;"
            + "-fx-border-radius:16;");
    container.setPrefSize(UIConstants.CAM_W, UIConstants.CAM_H);
    return container;
  }

  /**
   * Starts the Python camera stream subprocess and begins rendering frames.
   * Safe to call multiple times — stops any existing stream first.
   */
  public void start() {
    stop();
    running.set(true);

    thread = new Thread(() -> {
      try {
        ProcessBuilder pb = new ProcessBuilder("python3", SCRIPT_PATH);
        pb.redirectErrorStream(false);
        process = pb.start();

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()));

        String line;
        while (running.get() && (line = reader.readLine()) != null) {
          line = line.trim();
          if (line.isEmpty()) continue;
          try {
            byte[] bytes   = Base64.getDecoder().decode(line);
            Image  fxImage = new Image(new ByteArrayInputStream(bytes));
            Platform.runLater(() -> imageView.setImage(fxImage));
          } catch (IllegalArgumentException ex) {
            // malformed base64 line — skip silently
          }
        }
      } catch (IOException ex) {
        // process failed to start — panel stays blank, no crash
      } finally {
        stopProcess();
      }
    });

    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Stops the camera stream and destroys the Python subprocess.
   * Safe to call when already stopped.
   */
  public void stop() {
    running.set(false);
    stopProcess();
    if (thread != null) {
      thread.interrupt();
      thread = null;
    }
  }

  private void stopProcess() {
    if (process != null) {
      process.destroy();
      process = null;
    }
  }
}