package aslframework.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;


import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Main JavaFX UI for the ASL learning platform.
 *
 * <p>Single-mode layout:
 * <ul>
 *   <li>Left  – live camera feed via {@link CameraService}</li>
 *   <li>Right – instruction panel with video placeholder (swap out later)</li>
 *   <li>Bottom – scoring dashboard: attempts, avg accuracy, pass count, streak, log</li>
 * </ul>
 *
 * <p>Hold logic: a gesture is only recorded once it has been detected confidently
 * (score >= 0.8) for {@link #HOLD_DURATION_MS} milliseconds without switching letters.
 *
 * <p>All public update methods are safe to call from the background recognition
 * thread — they delegate to {@link Platform#runLater} internally.
 */
public class GameUI extends Application {

  /** How long a gesture must be held before it is recorded (milliseconds). */
  private static final long HOLD_DURATION_MS = 1000;

  // ── Shared instance so Main can push updates after launch ─────────────────────
  private static GameUI instance;
  public static GameUI getInstance() { return instance; }

  // ── Stage reference (needed to swap scenes) ──────────────────────────────────
  private Stage primaryStage;
  private Scene gameScene;
  private Scene gameOverScene;

  // ── Camera ────────────────────────────────────────────────────────────────────
  private CameraService cameraService;

  // ── Live feed ─────────────────────────────────────────────────────────────────
  private ImageView cameraView;

  private Label     detectedLetterLabel;
  private Label     confidenceLabel;

  // ── Instruction panel ─────────────────────────────────────────────────────────
  private Label      targetLetterLabel;
  private StackPane  instructionPane;   // root of instruction panel for overlay
  private Label      successLabel;      // "Correct!" overlay
  private Label      wrongLabel;        // "Wrong!" overlay

  // ── Instruction video ─────────────────────────────────────────────────────────
  private static String VIDEO_DIR = "";   // set via setVideoDir() before launch
  private LoadInstruction loadInstruction;

  /**
   * Sets the instruction video directory before JavaFX is launched.
   * Must be called from {@code Main} before {@code Application.launch()}.
   *
   * @param dir absolute path to the folder containing a.mp4 ... z.mp4
   */
  public static void setVideoDir(String dir) {
    VIDEO_DIR = dir;
  }

  // ── Lives ─────────────────────────────────────────────────────────────────────
  private static final int MAX_LIVES = 3;
  private int   lives     = MAX_LIVES;
  private Label livesValue;             // dashboard tile value label

  // ── Scoring dashboard ─────────────────────────────────────────────────────────
  private Label    totalAttemptsValue;
  private Label    avgAccuracyValue;
  private Label    passedValue;
  private Label    streakValue;
  private TextArea recentLog;

  // ── Session state ─────────────────────────────────────────────────────────────
  private int    totalAttempts = 0;
  private int    totalPassed   = 0;
  private int    streak        = 0;
  private double accuracySum   = 0.0;
  private final Deque<String> logLines = new ArrayDeque<>();
  private static final int MAX_LOG = 50;

  // ── Hold-timer state ──────────────────────────────────────────────────────────
  private String  holdLetter    = null;
  private long    holdStartMs   = 0;
  private boolean holdFired     = false;

  // ── Pending video load delay — cancelled on restart to prevent stale loads ────
  private PauseTransition pendingVideoDelay = null;

  // ── Cooldown — blocks recognition for 2s after a decision is made ─────────────
  private static final long COOLDOWN_MS    = 2000;
  private volatile long     cooldownUntil  = 0;

  // ── Current target letter (source of truth — never read from UI label) ────────
  private static final String[] LETTERS = {
      "A","B","C","D","E","F","G","H","I","J","K","L","M",
      "N","O","P","Q","R","S","T","U","V","W","X","Y","Z"
  };
  private int              currentLetterIndex = 0;
  private volatile String  currentTarget      = "A";

  // ── Style constants ───────────────────────────────────────────────────────────
  private static final String BG_DARK  = "-fx-background-color: #1e1e1e;";
  private static final String BG_PANEL = "-fx-background-color: #141414;";
  private static final String BG_STAT  = "-fx-background-color: #252525;";
  private static final String BORDER   =
      "-fx-border-color: #505050; -fx-border-width: 1; -fx-border-radius: 4;" +
          "-fx-background-radius: 4;";

  // ── JavaFX entry point ────────────────────────────────────────────────────────

  @Override
  public void start(Stage stage) {
    instance     = this;
    primaryStage = stage;

    BorderPane root = new BorderPane();
    root.setStyle(BG_DARK);
    root.setPadding(new Insets(12));

    root.setTop(buildTitleBar());
    root.setCenter(buildCentrePane());
    root.setBottom(buildDashboard());

    gameScene     = new Scene(root, 1050, 740);
    gameOverScene = buildGameOverScene();

    stage.setTitle("ASL Learning Platform");
    stage.setMinWidth(900);
    stage.setMinHeight(640);
    stage.setScene(gameScene);

    stage.setOnShown(e -> {
      startCamera();
      applyLayoutSizes();
    });
    stage.setOnCloseRequest(e -> stopCamera());

    stage.show();
    setTargetLetter("A");
  }

  // ── Game over scene ───────────────────────────────────────────────────────────

  /**
   * Builds the full-screen black game over scene shown when all lives are lost.
   */
  private Scene buildGameOverScene() {
    Label title = new Label("Game Over");
    title.setFont(Font.font("SansSerif", FontWeight.BOLD, 48));
    title.setTextFill(Color.web("#e05050"));

    Label message = new Label("You have used all three lives.\nWould you like to try again?");
    message.setFont(Font.font("SansSerif", 22));
    message.setTextFill(Color.web("#cccccc"));
    message.setTextAlignment(TextAlignment.CENTER);
    message.setWrapText(true);

    Button restartBtn = new Button("Restart");
    restartBtn.setFont(Font.font("SansSerif", FontWeight.BOLD, 18));
    restartBtn.setPrefWidth(180);
    restartBtn.setPrefHeight(50);
    restartBtn.setStyle(
        "-fx-background-color: #64d264;" +
            "-fx-text-fill: #1e1e1e;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
    );
    restartBtn.setOnMouseEntered(e ->
        restartBtn.setStyle(
            "-fx-background-color: #4ec24e;" +
                "-fx-text-fill: #1e1e1e;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;"
        )
    );
    restartBtn.setOnMouseExited(e ->
        restartBtn.setStyle(
            "-fx-background-color: #64d264;" +
                "-fx-text-fill: #1e1e1e;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;"
        )
    );
    restartBtn.setOnAction(e -> {
      restartSession();
      primaryStage.setScene(gameScene);
    });

    VBox layout = new VBox(30, title, message, restartBtn);
    layout.setAlignment(Pos.CENTER);
    layout.setStyle("-fx-background-color: #000000;");
    layout.setPadding(new Insets(60));

    return new Scene(layout, 1050, 740);
  }

  // ── Camera lifecycle ──────────────────────────────────────────────────────────

  /**
   * Starts the camera capture loop and pipes frames into the live feed panel.
   * Silently skips if the native library was not loaded or camera is unavailable.
   */
  private void startCamera() {
    try {
      cameraService = new CameraService();
      cameraService.start(this::updateCameraFrame);
    } catch (Exception e) {
      System.err.println("Camera unavailable: " + e.getMessage());
      // placeholder label remains visible — no crash
    }
  }

  /**
   * Returns the active {@link CameraService} so callers can push landmark data.
   * Returns {@code null} if the camera has not started yet.
   */
  public CameraService getCameraService() {
    return cameraService;
  }

  /** Stops the camera capture loop, releases the webcam, and disposes media resources. */
  private void stopCamera() {
    if (cameraService != null) {
      cameraService.stop();
    }
    if (loadInstruction != null) {
      loadInstruction.dispose();
    }
  }



  // ── Layout builders ───────────────────────────────────────────────────────────

  private HBox buildTitleBar() {
    Label title = new Label("ASL Recognition — Practice Mode");
    title.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
    title.setTextFill(Color.web("#dcdcdc"));

    HBox bar = new HBox(title);
    bar.setAlignment(Pos.CENTER);
    bar.setPadding(new Insets(0, 0, 12, 0));
    bar.setStyle(BG_DARK);
    return bar;
  }

  private VBox feedPanel;
  private VBox instructionPanel;

  private HBox buildCentrePane() {
    feedPanel        = buildFeedPanel();
    instructionPanel = buildInstructionPanel();

    // Use HBox.setHgrow with fixed percentages via minWidth — no binding, no animation
    feedPanel.setMinWidth(0);
    instructionPanel.setMinWidth(0);
    feedPanel.setMaxWidth(Double.MAX_VALUE);
    instructionPanel.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(feedPanel,        Priority.ALWAYS);
    HBox.setHgrow(instructionPanel, Priority.NEVER);

    HBox centre = new HBox(12, feedPanel, instructionPanel);
    centre.setPadding(new Insets(0, 0, 12, 0));
    centre.setStyle(BG_DARK);
    return centre;
  }

  /** Called once after the scene is shown — sets fixed pixel sizes with no animation. */
  private void applyLayoutSizes() {
    double total        = primaryStage.getScene().getWidth() - 24;
    double sceneHeight  = primaryStage.getScene().getHeight();
    double instrWidth   = total * 0.35;

    feedPanel.setPrefWidth(total * 0.65);
    instructionPanel.setPrefWidth(instrWidth);

    // Reserve space for the title label (~40px) and target label (~50px) and padding
    double videoHeight = sceneHeight - 40 - 50 - 200;  // 200px for dashboard + padding
    double videoWidth  = instrWidth - 16;               // 8px padding each side

    // Maintain 16:9 aspect ratio — shrink whichever dimension is the constraint
    double ratioW = videoWidth;
    double ratioH = ratioW * 9.0 / 16.0;
    if (ratioH > videoHeight) {
      ratioH = videoHeight;
      ratioW = ratioH * 16.0 / 9.0;
    }

    loadInstruction.setFixedSize(ratioW, ratioH);


  }

  private VBox buildFeedPanel() {
    cameraView = new ImageView();
    cameraView.setPreserveRatio(false);

    // Plain Pane — cameraView fills it, no canvas overlay needed
    Pane feedArea = new Pane(cameraView);
    feedArea.setStyle(BG_PANEL + BORDER);
    VBox.setVgrow(feedArea, Priority.ALWAYS);

    cameraView.fitWidthProperty().bind(feedArea.widthProperty());
    cameraView.fitHeightProperty().bind(feedArea.heightProperty());

    detectedLetterLabel = new Label("Letter: —");
    detectedLetterLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 18));
    detectedLetterLabel.setTextFill(Color.web("#64d264"));

    confidenceLabel = new Label("Confidence: —");
    confidenceLabel.setFont(Font.font("SansSerif", 14));
    confidenceLabel.setTextFill(Color.web("#b0b0b0"));

    // Landmarks toggle button — delegates to CameraService
    Button landmarkBtn = new Button("Landmarks: OFF");
    landmarkBtn.setFont(Font.font("SansSerif", 12));
    landmarkBtn.setStyle(styleLandmarkBtn(false));
    landmarkBtn.setOnAction(e -> {
      boolean nowOn = landmarkBtn.getText().equals("Landmarks: OFF");
      landmarkBtn.setText(nowOn ? "Landmarks: ON" : "Landmarks: OFF");
      landmarkBtn.setStyle(styleLandmarkBtn(nowOn));
      if (cameraService != null) cameraService.setShowLandmarks(nowOn);
    });

    HBox detectionRow = new HBox(20, detectedLetterLabel, confidenceLabel, landmarkBtn);
    detectionRow.setAlignment(Pos.CENTER_LEFT);
    detectionRow.setPadding(new Insets(6, 0, 0, 0));

    VBox panel = new VBox(6, sectionLabel("Live Camera Feed"), feedArea, detectionRow);
    panel.setStyle(BG_DARK);
    return panel;
  }

  private String styleLandmarkBtn(boolean active) {
    return "-fx-background-color: " + (active ? "#3a7a3a" : "#3a3a3a") + ";" +
        "-fx-text-fill: "        + (active ? "#aaffaa"  : "#aaaaaa") + ";" +
        "-fx-background-radius: 6;" +
        "-fx-cursor: hand;";
  }

  private VBox buildInstructionPanel() {
    targetLetterLabel = new Label("Target: —");
    targetLetterLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 30));
    targetLetterLabel.setTextFill(Color.web("#ffc83c"));

    // Delegate video loading and replay entirely to LoadInstruction
    loadInstruction = new LoadInstruction(VIDEO_DIR);
    StackPane videoArea = loadInstruction.getView();
    // Size is set once via applyLayoutSizes() — no bindings, no auto-grow
    videoArea.setMinHeight(0);
    videoArea.setMaxWidth(Double.MAX_VALUE);
    videoArea.setMaxHeight(Double.MAX_VALUE);

    // ── Success overlay ────────────────────────────────────────────────────
    successLabel = new Label("✓ Correct!  Let's move on to the next letter!");
    successLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
    successLabel.setTextFill(Color.web("#1e1e1e"));
    successLabel.setWrapText(true);
    successLabel.setTextAlignment(TextAlignment.CENTER);
    successLabel.setPadding(new Insets(16, 24, 16, 24));
    successLabel.setStyle(
        "-fx-background-color: #64d264;" +
            "-fx-background-radius: 8;"
    );
    successLabel.setVisible(false);
    successLabel.setOpacity(0.0);

    // ── Wrong overlay ──────────────────────────────────────────────────────
    wrongLabel = new Label("✗ Wrong!  Let's try again!");
    wrongLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
    wrongLabel.setTextFill(Color.web("#1e1e1e"));
    wrongLabel.setWrapText(true);
    wrongLabel.setTextAlignment(TextAlignment.CENTER);
    wrongLabel.setPadding(new Insets(16, 24, 16, 24));
    wrongLabel.setStyle(
        "-fx-background-color: #e05050;" +
            "-fx-background-radius: 8;"
    );
    wrongLabel.setVisible(false);
    wrongLabel.setOpacity(0.0);

    // Stack: video wrapper -> success overlay -> wrong overlay
    instructionPane = new StackPane(videoArea, successLabel, wrongLabel);
    VBox.setVgrow(instructionPane, Priority.ALWAYS);

    VBox panel = new VBox(10,
        sectionLabel("Instructions"),
        targetLetterLabel,
        instructionPane
    );
    panel.setAlignment(Pos.TOP_CENTER);
    panel.setStyle(BG_DARK);
    return panel;
  }

  private VBox buildDashboard() {
    totalAttemptsValue = statValueLabel("0");
    avgAccuracyValue   = statValueLabel("—");
    passedValue        = statValueLabel("0");
    streakValue        = statValueLabel("0");

    livesValue = statValueLabel(heartsDisplay(MAX_LIVES));

    HBox statsRow = new HBox(10,
        buildTile("Lives",        livesValue),
        buildTile("Attempts",     totalAttemptsValue),
        buildTile("Avg Accuracy", avgAccuracyValue),
        buildTile("Passed",       passedValue),
        buildTile("Streak",       streakValue)
    );
    for (javafx.scene.Node n : statsRow.getChildren()) {
      HBox.setHgrow(n, Priority.ALWAYS);
    }
    statsRow.setPadding(new Insets(6, 0, 6, 0));

    recentLog = new TextArea("No attempts yet. Show a letter to the camera to begin.");
    recentLog.setEditable(false);
    recentLog.setWrapText(false);
    recentLog.setPrefHeight(88);
    recentLog.setFont(Font.font("Monospaced", 12));
    recentLog.setStyle(
        "-fx-control-inner-background: #0f0f0f;" +
            "-fx-text-fill: #a0a0a0;" +
            "-fx-border-color: #404040;"
    );

    VBox dashboard = new VBox(4,
        sectionLabel("Scoring Dashboard"),
        statsRow,
        recentLog
    );
    dashboard.setStyle(BG_DARK);
    dashboard.setPadding(new Insets(8, 0, 0, 0));
    return dashboard;
  }

  // ── Public update methods (safe to call from any thread) ─────────────────────

  /**
   * Called on every recognised frame. Updates the live detection readout and
   * manages the hold timer — a gesture is only recorded once the same letter
   * has been held confidently for {@link #HOLD_DURATION_MS} ms.
   *
   * @param letter     best-matching ASL letter for this frame
   * @param confidence confidence score in [0.0, 1.0]
   */
  /**
   * Called when no confident gesture is detected (below the gate threshold).
   * Resets the detection readout to show "Vacant" and clears the hold timer.
   */
  public void showVacant() {
    // Reset hold timer — a vacant frame should not contribute to any hold
    holdLetter  = null;
    holdStartMs = 0;
    holdFired   = false;

    Platform.runLater(() -> {
      detectedLetterLabel.setText("Letter: Vacant");
      detectedLetterLabel.setTextFill(Color.web("#666666"));
      confidenceLabel.setText("Confidence: —");
    });
  }

  public void updateDetection(String letter, double confidence) {
    // Skip processing during cooldown period
    if (System.currentTimeMillis() < cooldownUntil) return;

    // Hold-timer logic runs on calling thread
    if (confidence >= 0.8) {
      if (!letter.equals(holdLetter)) {
        holdLetter  = letter;
        holdStartMs = System.currentTimeMillis();
        holdFired   = false;
      } else if (!holdFired
          && System.currentTimeMillis() - holdStartMs >= HOLD_DURATION_MS) {
        // Held for 1 second — compare against currentTarget field (thread-safe)
        boolean correct = letter.equals(currentTarget);
        cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS;
        holdLetter  = null;
        holdStartMs = 0;
        holdFired   = false;
        recordAttempt(letter, confidence, correct);
      }
    } else {
      holdLetter  = null;
      holdStartMs = 0;
      holdFired   = false;
    }

    Platform.runLater(() -> {
      detectedLetterLabel.setText("Letter: " + letter);
      confidenceLabel.setText(String.format("Confidence: %.1f%%", confidence * 100));
      detectedLetterLabel.setTextFill(
          confidence >= 0.8 ? Color.web("#64d264") : Color.web("#d28c3c"));
    });
  }

  /**
   * Sets the target letter shown in the instruction panel.
   *
   * @param letter the ASL letter the user should attempt next
   */
  public void setTargetLetter(String letter) {
    currentTarget = letter;   // update field immediately on calling thread
    Platform.runLater(() -> {
      targetLetterLabel.setText("Target: " + letter);
      loadInstruction.load(letter);   // load matching video immediately
    });
  }

  /**
   * Records a completed attempt and refreshes the scoring dashboard.
   * Called automatically by the hold timer inside {@link #updateDetection}.
   *
   * @param letter   letter that was attempted
   * @param accuracy accuracy in [0.0, 1.0]
   * @param passed   whether the attempt met the pass threshold
   */
  public void recordAttempt(String letter, double accuracy, boolean passed) {
    Platform.runLater(() -> {
      totalAttempts++;
      accuracySum += accuracy;

      if (passed) {
        totalPassed++;
        streak++;
        showSuccessPrompt();
        advanceToNextLetter();
      } else {
        streak = 0;
        lives--;
        livesValue.setText(heartsDisplay(lives));
        if (lives <= 0) {
          showGameOver();
          return;
        }
        showWrongPrompt();
      }

      totalAttemptsValue.setText(String.valueOf(totalAttempts));
      avgAccuracyValue.setText(String.format("%.1f%%", (accuracySum / totalAttempts) * 100));
      passedValue.setText(totalPassed + "/" + totalAttempts);
      streakValue.setText(String.valueOf(streak));

      String entry = String.format("%-4s  %s  acc=%.1f%%",
          letter, passed ? "✓ PASS" : "✗ FAIL", accuracy * 100);
      logLines.addFirst(entry);
      if (logLines.size() > MAX_LOG) logLines.removeLast();
      recentLog.setText(String.join("\n", logLines));
    });
  }

  /**
   * Briefly shows the success overlay over the instruction panel then fades it out.
   * Sequence: fade in (0.3s) -> hold (2s) -> fade out (0.5s).
   * Must be called on the JavaFX Application Thread.
   */
  private void showSuccessPrompt() {
    successLabel.setVisible(true);
    successLabel.setOpacity(0.0);

    FadeTransition fadeIn = new FadeTransition(Duration.millis(300), successLabel);
    fadeIn.setFromValue(0.0);
    fadeIn.setToValue(1.0);

    PauseTransition hold = new PauseTransition(Duration.millis(2000));

    FadeTransition fadeOut = new FadeTransition(Duration.millis(500), successLabel);
    fadeOut.setFromValue(1.0);
    fadeOut.setToValue(0.0);
    fadeOut.setOnFinished(e -> successLabel.setVisible(false));

    new SequentialTransition(fadeIn, hold, fadeOut).play();
  }

  /**
   * Briefly shows the wrong overlay over the instruction panel then fades it out.
   * Sequence: fade in (0.3s) -> hold (2s) -> fade out (0.5s).
   * The instruction placeholder stays visible underneath.
   */
  private void showWrongPrompt() {
    wrongLabel.setVisible(true);
    wrongLabel.setOpacity(0.0);

    FadeTransition fadeIn = new FadeTransition(Duration.millis(300), wrongLabel);
    fadeIn.setFromValue(0.0);
    fadeIn.setToValue(1.0);

    PauseTransition hold = new PauseTransition(Duration.millis(2000));

    FadeTransition fadeOut = new FadeTransition(Duration.millis(500), wrongLabel);
    fadeOut.setFromValue(1.0);
    fadeOut.setToValue(0.0);
    fadeOut.setOnFinished(e -> wrongLabel.setVisible(false));

    new SequentialTransition(fadeIn, hold, fadeOut).play();
  }

  /**
   * Shows the game over scene when all lives are lost.
   * Called on the JavaFX Application Thread.
   */
  private void showGameOver() {
    cooldownUntil = Long.MAX_VALUE;   // freeze recognition while on game over screen
    primaryStage.setScene(gameOverScene);
  }

  /**
   * Resets all session state and returns to the game scene.
   * Called when the player presses Restart on the game over screen.
   */
  private void restartSession() {
    lives         = MAX_LIVES;
    totalAttempts = 0;
    totalPassed   = 0;
    streak        = 0;
    accuracySum   = 0.0;
    logLines.clear();
    cooldownUntil = 0;

    livesValue.setText(heartsDisplay(MAX_LIVES));
    totalAttemptsValue.setText("0");
    avgAccuracyValue.setText("—");
    passedValue.setText("0");
    streakValue.setText("0");
    recentLog.setText("Session restarted. Show a letter to begin.");

    // Reset hold timer and target
    holdLetter         = null;
    holdStartMs        = 0;
    holdFired          = false;
    // Cancel any pending video load from the previous session
    if (pendingVideoDelay != null) {
      pendingVideoDelay.stop();
      pendingVideoDelay = null;
    }

    currentLetterIndex = 0;
    currentTarget      = "A";
    targetLetterLabel.setText("Target: A");
    loadInstruction.load("A");   // always load A video immediately on restart

    System.out.println("Session restarted — lives reset to " + MAX_LIVES);
  }

  /**
   * Advances currentTarget to the next letter in sequence.
   * Wraps back to A after Z.
   * Must be called on the JavaFX Application Thread.
   */
  private void advanceToNextLetter() {
    currentLetterIndex = (currentLetterIndex + 1) % LETTERS.length;
    currentTarget      = LETTERS[currentLetterIndex];
    holdLetter  = null;
    holdStartMs = 0;
    holdFired   = false;

    targetLetterLabel.setText("Target: " + currentTarget);

    // Cancel any pending video load from a previous advance
    if (pendingVideoDelay != null) {
      pendingVideoDelay.stop();
    }

    // Snapshot the letter so the closure always loads the right video
    // even if currentTarget changes before the delay fires
    final String nextLetter = currentTarget;
    pendingVideoDelay = new PauseTransition(Duration.millis(2000));
    pendingVideoDelay.setOnFinished(e -> {
      // Only load if the target hasn't changed since this delay was created
      if (nextLetter.equals(currentTarget)) {
        loadInstruction.load(nextLetter);
      }
    });
    pendingVideoDelay.play();
  }

  /**
   * Returns a hearts string representing remaining lives (e.g. "❤❤❤", "❤❤♡").
   */
  private String heartsDisplay(int remaining) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < MAX_LIVES; i++) {
      sb.append(i < remaining ? "❤" : "♡");
    }
    return sb.toString();
  }

  /**
   * Displays a live camera frame in the feed panel.
   * Called by {@link CameraService} on each captured frame.
   *
   * @param image the current frame as a JavaFX {@link Image}
   */
  public void updateCameraFrame(Image image) {
    Platform.runLater(() -> cameraView.setImage(image));
  }


  // ── Private helpers ───────────────────────────────────────────────────────────

  private Label sectionLabel(String text) {
    Label l = new Label(text);
    l.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
    l.setTextFill(Color.web("#b4b4b4"));
    return l;
  }

  private Label statValueLabel(String initial) {
    Label l = new Label(initial);
    l.setFont(Font.font("SansSerif", FontWeight.BOLD, 22));
    l.setTextFill(Color.web("#dddddd"));
    return l;
  }

  private VBox buildTile(String heading, Label valueLabel) {
    Label headingLabel = new Label(heading);
    headingLabel.setFont(Font.font("SansSerif", 11));
    headingLabel.setTextFill(Color.web("#888888"));

    VBox tile = new VBox(2, headingLabel, valueLabel);
    tile.setAlignment(Pos.CENTER);
    tile.setPadding(new Insets(8));
    tile.setMaxWidth(Double.MAX_VALUE);
    tile.setStyle(BG_STAT + BORDER);
    return tile;
  }
}