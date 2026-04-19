package aslframework.ui;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.LandmarkBridge;
import aslframework.recognition.MediaPipeRecognizer;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.opencv.core.Core;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Splash screen for the ASL Learning Platform.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Plays {@code opening.mp4} once at full window size.</li>
 *   <li>Freezes on the last frame when the video ends.</li>
 *   <li>Shows prompt "Swipe left with your HAND to start exploring." immediately.</li>
 *   <li>Starts the landmark bridge and watches for the trigger gesture.</li>
 *   <li>On gesture detected: plays {@code entering.mp4}, then hands off to {@link GameUI}.</li>
 * </ol>
 *
 * <p>Videos are resolved in this order:
 * <ol>
 *   <li>Classpath: {@code /assets/animation/<filename>}</li>
 *   <li>Working directory: {@code assets/animation/<filename>}</li>
 *   <li>Absolute path using {@code user.dir} system property</li>
 * </ol>
 */
public class Open extends Application {

  // ── OpenCV native lib ─────────────────────────────────────────────────────
  static {
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
  }

  // ── Constants ─────────────────────────────────────────────────────────────
  private static final int    WIN_W             = 1100;
  private static final int    WIN_H             = 720;
  private static final double GESTURE_THRESHOLD = 0.82;
  private static final String TRIGGER_LETTER    = "A";
  private static final String PROMPT_TEXT       =
      "Swipe left with your HAND to start exploring.";

  // ── State ─────────────────────────────────────────────────────────────────
  private Stage            primaryStage;
  private GestureLibrary   library;
  private LandmarkBridge   bridge;
  private ExecutorService  landmarkThread;
  private volatile boolean triggered = false;

  // JavaFX media nodes
  private MediaPlayer openingPlayer;
  private MediaPlayer enteringPlayer;
  private MediaView   mediaView;
  private Label       promptLabel;
  private StackPane   root;
  private Animation   promptPulse;

  // ── Application entry ─────────────────────────────────────────────────────

  @Override
  public void start(Stage stage) throws Exception {
    this.primaryStage = stage;

    try {
      this.library = new GestureLibrary();
    } catch (Exception e) {
      System.err.println("[Open] Failed to load gesture library: " + e.getMessage());
    }

    stage.setTitle("ASL Learning Platform");
    stage.setWidth(WIN_W);
    stage.setHeight(WIN_H);
    stage.setResizable(false);
    stage.setOnCloseRequest(e -> shutdown());

    buildScene();
    stage.show();
    playOpening();
  }

  @Override
  public void stop() {
    shutdown();
  }

  // ── Scene ─────────────────────────────────────────────────────────────────

  private void buildScene() {
    mediaView = new MediaView();
    mediaView.setFitWidth(WIN_W);
    mediaView.setFitHeight(WIN_H);
    mediaView.setPreserveRatio(false);

    promptLabel = new Label(PROMPT_TEXT);
    promptLabel.setFont(Font.font("Courier New", 12));
    promptLabel.setTextFill(Color.web("#1ABCB8"));
    promptLabel.setOpacity(0);  // hidden until opening finishes
    promptLabel.setStyle(
        "-fx-font-family:'Courier New';"
            + "-fx-font-size:12px;"
            + "-fx-text-fill:#1ABCB8;"
            + "-fx-letter-spacing:1px;");

    root = new StackPane(mediaView, promptLabel);
    root.setStyle("-fx-background-color:#000;");
    StackPane.setAlignment(promptLabel, Pos.BOTTOM_CENTER);
    StackPane.setMargin(promptLabel, new javafx.geometry.Insets(0, 0, 40, 0));

    primaryStage.setScene(new Scene(root, WIN_W, WIN_H));
  }

  // ── Opening video ─────────────────────────────────────────────────────────

  private void playOpening() {
    Media media = loadMedia("opening.mp4");
    if (media == null) {
      onOpeningFinished();
      return;
    }

    openingPlayer = new MediaPlayer(media);
    openingPlayer.setCycleCount(1);
    openingPlayer.setOnReady(() -> {
      // Stop exactly one frame before the end to guarantee
      // the last visible frame is held cleanly
      Duration oneFrame = Duration.millis(1000.0 / 30.93); // exact fps from metadata
      openingPlayer.setStopTime(
          openingPlayer.getTotalDuration().subtract(oneFrame));
      openingPlayer.seek(Duration.ZERO);
      openingPlayer.play();
    });
    mediaView.setMediaPlayer(openingPlayer);

    openingPlayer.setOnEndOfMedia(() -> {
      openingPlayer.seek(openingPlayer.getStopTime());
      openingPlayer.pause();
      onOpeningFinished();
    });

    openingPlayer.setOnError(() ->
        System.err.println("[Open] opening.mp4 error: "
            + openingPlayer.getError().getMessage()));
  }

  // ── After opening finishes ────────────────────────────────────────────────

  private void onOpeningFinished() {
    // Show prompt instantly — no fade delay
    promptLabel.setOpacity(1);
    pulsePrompt();
    startGestureListener();
  }

  private void pulsePrompt() {
    FadeTransition ft = new FadeTransition(Duration.millis(1200), promptLabel);
    ft.setFromValue(1.0);
    ft.setToValue(0.5);
    ft.setAutoReverse(true);
    ft.setCycleCount(Animation.INDEFINITE);
    ft.play();
    promptPulse = ft;
  }

  // ── Gesture listener ──────────────────────────────────────────────────────

  private void startGestureListener() {
    if (library == null) return;

    try {
      bridge = new LandmarkBridge();
    } catch (LandmarkBridge.LandmarkBridgeException e) {
      System.err.println("[Open] LandmarkBridge failed: " + e.getMessage());
      return;
    }

    MediaPipeRecognizer recognizer = new MediaPipeRecognizer();

    landmarkThread = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "open-gesture-listener");
      t.setDaemon(true);
      return t;
    });

    landmarkThread.submit(() -> {
      while (!Thread.currentThread().isInterrupted() && !triggered) {
        try {
          List<HandLandmark> landmarks = bridge.nextLandmarks();
          if (landmarks == null || landmarks.isEmpty()) continue;

          List<GestureDefinition> variants =
              library.getGestureVariants(TRIGGER_LETTER);
          if (variants == null || variants.isEmpty()) continue;

          double bestScore = recognizer.recognize(landmarks, variants)
              .getConfidenceScore();

          if (bestScore >= GESTURE_THRESHOLD) {
            triggered = true;
            Platform.runLater(this::playEntering);
          }

        } catch (LandmarkBridge.LandmarkBridgeException e) {
          break;
        }
      }
    });
  }

  // ── Entering video ────────────────────────────────────────────────────────

  private void playEntering() {
    stopGestureListener();

    if (promptPulse != null) { promptPulse.stop(); }
    promptLabel.setOpacity(0);

    Media media = loadMedia("entering.mp4");
    if (media == null) {
      launchGameUI();
      return;
    }

    enteringPlayer = new MediaPlayer(media);
    enteringPlayer.setCycleCount(1);
    enteringPlayer.setMute(true);  // pre-buffer silently

    // Use a second MediaView for entering, stacked on top
    // Only make it visible once it has rendered its first frame
    MediaView enteringView = new MediaView(enteringPlayer);
    enteringView.setFitWidth(WIN_W);
    enteringView.setFitHeight(WIN_H);
    enteringView.setPreserveRatio(false);
    enteringView.setOpacity(0);  // invisible until first frame ready
    root.getChildren().add(enteringView);

    enteringPlayer.setOnReady(() -> {
      enteringPlayer.seek(Duration.ZERO);
      enteringPlayer.setMute(false);
      enteringPlayer.play();
    });

    // Once entering has advanced past frame 0, reveal it and hide opening
    enteringPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal.toMillis() > 50 && enteringView.getOpacity() == 0) {
        Platform.runLater(() -> {
          enteringView.setOpacity(1);
          // Now safe to detach opening player
          if (openingPlayer != null) openingPlayer.pause();
        });
      }
    });

    enteringPlayer.setOnEndOfMedia(this::launchGameUI);

    enteringPlayer.setOnError(() -> {
      System.err.println("[Open] entering.mp4 error: "
          + enteringPlayer.getError().getMessage());
      launchGameUI();
    });
  }

  // ── Hand off to GameUI ────────────────────────────────────────────────────

  private void launchGameUI() {
    if (openingPlayer  != null) { openingPlayer.dispose();  openingPlayer  = null; }
    if (enteringPlayer != null) { enteringPlayer.dispose(); enteringPlayer = null; }

    GameUI gameUI = new GameUI();
    try {
      gameUI.start(primaryStage);
    } catch (Exception e) {
      System.err.println("[Open] Failed to launch GameUI: " + e.getMessage());
    }
  }

  // ── Media loader ──────────────────────────────────────────────────────────

  private Media loadMedia(String filename) {
    // 1. Classpath (src/main/resources/assets/animation/)
    URL resource = getClass().getResource("/assets/animation/" + filename);
    if (resource != null) {
      System.out.println("[Open] Loading from classpath: " + resource);
      return new Media(resource.toExternalForm());
    }

    // 2. Working directory (FinalProject/assets/animation/)
    File file = new File("assets/animation/" + filename);
    System.out.println("[Open] Trying path: " + file.getAbsolutePath()
        + "  exists=" + file.exists());
    if (file.exists()) {
      return new Media(file.toURI().toString());
    }

    // 3. Absolute fallback using user.dir
    File absolute = new File(System.getProperty("user.dir"),
        "assets/animation/" + filename);
    System.out.println("[Open] Trying absolute: " + absolute.getAbsolutePath()
        + "  exists=" + absolute.exists());
    if (absolute.exists()) {
      return new Media(absolute.toURI().toString());
    }

    System.err.println("[Open] Video not found: " + filename);
    return null;
  }

  // ── Shutdown ──────────────────────────────────────────────────────────────

  private void stopGestureListener() {
    if (landmarkThread != null) { landmarkThread.shutdownNow(); landmarkThread = null; }
    if (bridge         != null) { bridge.close();               bridge         = null; }
  }

  private void shutdown() {
    triggered = true;
    stopGestureListener();
    if (openingPlayer  != null) { openingPlayer.dispose();  openingPlayer  = null; }
    if (enteringPlayer != null) { enteringPlayer.dispose(); enteringPlayer = null; }
    Platform.exit();
  }

  // ── Entry point ───────────────────────────────────────────────────────────

  public static void main(String[] args) {
    launch(args);
  }
}