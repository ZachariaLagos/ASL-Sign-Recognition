package aslframework.ui;

import aslframework.game.result.BattleResult;
import aslframework.game.result.GameResult;
import aslframework.game.result.PracticeResult;
import aslframework.game.round.BattleRound;
import aslframework.game.session.*;
import aslframework.model.HandLandmark;
import aslframework.persistence.AttemptRecord;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.LandmarkBridge;
import aslframework.recognition.MediaPipeRecognizer;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.List;

/**
 * Main JavaFX UI for the ASL Recognition Learning Platform.
 *
 * <p>Screens:
 * <ol>
 *   <li>Mode Select — choose Practice or Battle, enter player IDs</li>
 *   <li>Game — live camera feed, target letter, confidence bar, score/status</li>
 *   <li>Results — final summary with scores or battle ranking</li>
 * </ol>
 *
 * <p>Threading model:
 * <ul>
 *   <li>Camera frames are grabbed on {@code cameraThread} and written to
 *       {@code latestFrame} (an AtomicReference). An {@link AnimationTimer}
 *       on the JavaFX thread reads that reference every frame and paints the
 *       {@link Canvas}.</li>
 *   <li>Landmark blocking reads run on {@code landmarkThread}. All UI mutations
 *       are posted back via {@link Platform#runLater}.</li>
 * </ul>
 */
public class GameUI extends Application {

  // ── OpenCV native library ─────────────────────────────────────────────────
  // Loads libopencv_java4130.dylib from the path set via
  // -Djava.library.path in pom.xml (javafx-maven-plugin options).
  static {
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
  }

  // ── Dimensions ───────────────────────────────────────────────────────────
  private static final int WIN_W = 1100;
  private static final int WIN_H = 720;
  private static final int CAM_W = 640;
  private static final int CAM_H = 480;

  // ── Palette ──────────────────────────────────────────────────────────────
  private static final String C_BG      = "#0D0D0F";
  private static final String C_SURFACE = "#16161A";
  private static final String C_BORDER  = "#2A2A32";
  private static final String C_ACCENT  = "#1ABCB8";  // teal — primary logo color
  private static final String C_ACCENT2 = "#F5A800";  // amber — secondary logo color
  private static final String C_WARN    = "#FF6B4A";  // coral
  private static final String C_TEXT    = "#E8E8F0";
  private static final String C_MUTED   = "#6B6B80";

  // ── State ─────────────────────────────────────────────────────────────────
  private Stage          primaryStage;
  private GestureLibrary library;

  // Camera
  private VideoCapture                 camera;
  private final AtomicReference<Image> latestFrame = new AtomicReference<>();
  private ExecutorService              cameraThread;
  private AnimationTimer               cameraTimer;

  // Session
  private GameSession     session;
  private BattleSession   battleSession;
  private LandmarkBridge  bridge;
  private ExecutorService landmarkThread;
  private volatile boolean sessionRunning = false;

  // UI nodes updated from event listener
  private Label       letterLabel;
  private Label       statusLabel;
  private Label       scoreLabel;
  private Label       streakLabel;
  private ProgressBar confidenceBar;
  private Label       confidencePct;
  private Label       progressLabel;
  private VBox        playerStatusBox;  // battle only

  // Guidance image — practice only
  private ImageView guidanceImageView;
  private String    currentGuidanceLetter = "";

  // ── Application entry ────────────────────────────────────────────────────

  @Override
  public void start(Stage stage) {
    this.primaryStage = stage;

    try {
      this.library = new GestureLibrary();
    } catch (Exception e) {
      showFatalError("Failed to load gesture library:\n" + e.getMessage());
      return;
    }

    stage.setTitle("ASL Learning Platform");
    stage.setWidth(WIN_W);
    stage.setHeight(WIN_H);
    stage.setResizable(false);
    stage.setOnCloseRequest(e -> shutdown());

    showModeSelect();
    stage.show();
  }

  @Override
  public void stop() {
    shutdown();
  }

  // ── Screen: Mode Select ──────────────────────────────────────────────────

  private void showModeSelect() {
    stopCamera();
    stopSession();

    // Left panel: branding
    VBox brand = new VBox(12);
    brand.setAlignment(Pos.CENTER);
    brand.setPadding(new Insets(60, 40, 60, 40));
    brand.setStyle("-fx-background-color:" + C_BG + ";");
    brand.setPrefWidth(420);

    Label logo = new Label("ASL");
    logo.setStyle("-fx-font-size:96px; -fx-font-weight:900;"
        + "-fx-text-fill:" + C_ACCENT + "; -fx-font-family:'Courier New';");

    Label sub = new Label("Recognition Platform");
    sub.setStyle("-fx-font-size:16px; -fx-text-fill:" + C_MUTED
        + "; -fx-font-family:'Courier New'; -fx-letter-spacing:4px;");

    Label loaded = new Label(library.size() + " gestures loaded");
    loaded.setStyle("-fx-font-size:13px; -fx-text-fill:" + C_ACCENT
        + "; -fx-font-family:'Courier New';");

    HBox strip = buildLetterStrip();
    brand.getChildren().addAll(logo, sub, loaded, strip);

    // Right panel: mode cards
    VBox right = new VBox(32);
    right.setAlignment(Pos.CENTER);
    right.setPadding(new Insets(60, 50, 60, 50));
    right.setStyle("-fx-background-color:" + C_SURFACE + ";");
    right.setPrefWidth(680);

    Label choose = new Label("CHOOSE MODE");
    choose.setStyle("-fx-font-size:13px; -fx-text-fill:" + C_MUTED
        + "; -fx-font-family:'Courier New'; -fx-letter-spacing:3px;");

    VBox practiceCard = buildModeCard(
        "PRACTICE",
        "Sign missing letters  ·  A/D to aim  ·  3 lives",
        C_ACCENT,
        this::launchWordShooter);

    VBox battleCard = buildModeCard(
        "BATTLE",
        "Elimination  ·  Network  ·  Last one standing",
        C_ACCENT2,
        this::launchNetworkBattle);

    right.getChildren().addAll(choose, practiceCard, battleCard);

    HBox root = new HBox(brand, right);
    HBox.setHgrow(right, Priority.ALWAYS);
    primaryStage.setScene(new Scene(root));
  }

  private HBox buildLetterStrip() {
    HBox strip = new HBox(6);
    strip.setAlignment(Pos.CENTER);
    strip.setPadding(new Insets(24, 0, 0, 0));
    String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    for (int i = 0; i < letters.length(); i++) {
      Label l = new Label(String.valueOf(letters.charAt(i)));
      l.setStyle("-fx-font-size:12px; -fx-text-fill:" + C_BORDER
          + "; -fx-font-family:'Courier New';");
      final int idx = i;
      Timeline t = new Timeline(
          new KeyFrame(Duration.ZERO,
              new KeyValue(l.textFillProperty(), Color.web(C_BORDER))),
          new KeyFrame(Duration.millis(300),
              new KeyValue(l.textFillProperty(), Color.web(C_ACCENT))),
          new KeyFrame(Duration.millis(600),
              new KeyValue(l.textFillProperty(), Color.web(C_BORDER)))
      );
      t.setDelay(Duration.millis(idx * 120));
      t.setCycleCount(Animation.INDEFINITE);
      t.play();
      strip.getChildren().add(l);
    }
    return strip;
  }

  private VBox buildModeCard(String title, String desc, String accent, Runnable onStart) {
    VBox card = new VBox(10);
    card.setPadding(new Insets(28, 32, 28, 32));
    card.setStyle(cardStyle(C_BORDER));
    card.setCursor(javafx.scene.Cursor.HAND);

    Label t = new Label(title);
    t.setStyle("-fx-font-size:22px; -fx-font-weight:700;"
        + "-fx-text-fill:" + accent + "; -fx-font-family:'Courier New';");

    Label d = new Label(desc);
    d.setStyle("-fx-font-size:13px; -fx-text-fill:" + C_MUTED
        + "; -fx-font-family:'Courier New';");

    Label arrow = new Label("START →");
    arrow.setStyle("-fx-font-size:12px; -fx-text-fill:" + accent
        + "; -fx-font-family:'Courier New'; -fx-letter-spacing:2px;");

    card.getChildren().addAll(t, d, arrow);
    card.setOnMouseEntered(e -> card.setStyle(cardStyle(accent)));
    card.setOnMouseExited(e  -> card.setStyle(cardStyle(C_BORDER)));
    card.setOnMouseClicked(e -> onStart.run());
    return card;
  }

  private String cardStyle(String borderColor) {
    return "-fx-background-color:" + C_BG + ";"
        + "-fx-border-color:" + borderColor + ";"
        + "-fx-border-width:1;"
        + "-fx-border-radius:4;"
        + "-fx-background-radius:4;";
  }

  // ── Screen: Practice Setup ───────────────────────────────────────────────

  private void showPracticeSetup() {
    VBox root = buildSetupLayout("PRACTICE MODE");

    TextField nameField = styledTextField("player1");
    Button start = accentButton("START PRACTICE", C_ACCENT);
    start.setOnAction(e -> {
      String id = nameField.getText().trim();
      if (id.isEmpty()) id = "player1";
      launchPractice(id);
    });

    Button back = ghostButton("← BACK");
    back.setOnAction(e -> showModeSelect());

    root.getChildren().addAll(
        styledLabel("Player name:", 13, C_MUTED),
        nameField, spacer(16), start, spacer(8), back);
    primaryStage.setScene(new Scene(root));
  }

  // ── Screen: Battle Setup ─────────────────────────────────────────────────

  private void showBattleSetup() {
    VBox root = buildSetupLayout("BATTLE MODE");

    TextArea area = new TextArea("player1\nplayer2");
    area.setPrefRowCount(5);
    area.setStyle("-fx-background-color:#1E1E26; -fx-text-fill:" + C_TEXT
        + "; -fx-border-color:" + C_BORDER
        + "; -fx-font-family:'Courier New'; -fx-font-size:13px;"
        + "-fx-border-radius:3; -fx-background-radius:3;");

    Button start = accentButton("START BATTLE", C_ACCENT2);
    start.setOnAction(e -> {
      List<String> ids = new ArrayList<>();
      for (String line : area.getText().split("\\n")) {
        String s = line.trim();
        if (!s.isEmpty()) ids.add(s);
      }
      if (ids.size() < 2) {
        showAlert("Battle requires at least 2 players.");
        return;
      }
      launchBattle(ids);
    });

    Button back = ghostButton("← BACK");
    back.setOnAction(e -> showModeSelect());

    root.getChildren().addAll(
        styledLabel("Player names (one per line, min 2):", 13, C_MUTED),
        area, spacer(16), start, spacer(8), back);
    primaryStage.setScene(new Scene(root));
  }

  // ── Screen: Game ─────────────────────────────────────────────────────────

  private Scene buildGameScene(boolean isBattle) {
    Canvas canvas = new Canvas(CAM_W, CAM_H);
    StackPane camPane = new StackPane(canvas);
    camPane.setStyle("-fx-background-color:#000;");
    camPane.setMinSize(CAM_W, CAM_H);
    camPane.setMaxSize(CAM_W, CAM_H);

    // Only start Java OpenCV camera in practice mode.
    // In battle mode the Python LandmarkBridge process owns the camera —
    // opening it from Java too causes a SIGABRT (exit 134) on macOS.
    if (!isBattle) {
      startCamera(canvas);
    } else {
      GraphicsContext gc = canvas.getGraphicsContext2D();
      gc.setFill(Color.web(C_BG));
      gc.fillRect(0, 0, CAM_W, CAM_H);
      gc.setFill(Color.web(C_MUTED));
      gc.setFont(Font.font("Courier New", 13));
      gc.fillText("Camera active via Python bridge", 20, 40);
    }

    if (isBattle) {
      VBox hud = buildHUD(true);
      HBox root = new HBox(camPane, hud);
      HBox.setHgrow(hud, Priority.ALWAYS);
      root.setStyle("-fx-background-color:" + C_BG + ";");
      return new Scene(root, WIN_W, WIN_H);
    } else {
      // Practice layout:
      // Left: camera (640x480) stacked on top of guidance image (640x240)
      // Right: HUD (stats + controls)
      guidanceImageView = new ImageView();
      guidanceImageView.setFitWidth(CAM_W);
      guidanceImageView.setFitHeight(240);
      guidanceImageView.setPreserveRatio(true);
      guidanceImageView.setStyle("-fx-background-color:#000;");

      StackPane guidancePane = new StackPane(guidanceImageView);
      guidancePane.setStyle("-fx-background-color:#111;");
      guidancePane.setMinSize(CAM_W, 240);
      guidancePane.setMaxSize(CAM_W, 240);

      Label guidanceLbl = styledLabel("GESTURE GUIDE", 10, C_MUTED);
      guidanceLbl.setStyle(guidanceLbl.getStyle() + "-fx-letter-spacing:2px;");
      guidanceLbl.setPadding(new Insets(6, 0, 4, 0));

      VBox leftCol = new VBox(0, camPane, guidanceLbl, guidancePane);
      leftCol.setStyle("-fx-background-color:#000;");
      leftCol.setAlignment(Pos.TOP_LEFT);

      VBox hud = buildHUD(false);
      HBox root = new HBox(leftCol, hud);
      HBox.setHgrow(hud, Priority.ALWAYS);
      root.setStyle("-fx-background-color:" + C_BG + ";");
      return new Scene(root, WIN_W, WIN_H + 240 + 24);
    }
  }

  private VBox buildHUD(boolean isBattle) {
    VBox hud = new VBox(0);
    hud.setStyle("-fx-background-color:" + C_SURFACE + ";");
    hud.setPadding(new Insets(32, 36, 32, 36));
    hud.setAlignment(Pos.TOP_LEFT);

    String modeColor = isBattle ? C_ACCENT2 : C_ACCENT;
    Label modeBadge = styledLabel(isBattle ? "BATTLE" : "PRACTICE", 11, modeColor);
    modeBadge.setStyle(modeBadge.getStyle()
        + "-fx-letter-spacing:3px;"
        + "-fx-border-color:" + modeColor + ";"
        + "-fx-border-width:1; -fx-border-radius:2; -fx-padding:3 8 3 8;");

    letterLabel = new Label("?");
    letterLabel.setStyle("-fx-font-size:120px; -fx-font-weight:900;"
        + "-fx-text-fill:" + C_TEXT + "; -fx-font-family:'Courier New';");
    VBox.setMargin(letterLabel, new Insets(12, 0, 0, -6));

    statusLabel = styledLabel("Waiting for hand…", 14, C_MUTED);
    VBox.setMargin(statusLabel, new Insets(4, 0, 20, 0));

    Label confLbl = styledLabel("CONFIDENCE", 10, C_MUTED);
    confLbl.setStyle(confLbl.getStyle() + "-fx-letter-spacing:2px;");

    confidenceBar = new ProgressBar(0);
    confidenceBar.setPrefWidth(Double.MAX_VALUE);
    confidenceBar.setPrefHeight(10);
    confidenceBar.setStyle("-fx-accent:" + C_ACCENT + ";"
        + "-fx-background-color:" + C_BORDER + ";"
        + "-fx-background-radius:2; -fx-border-radius:2;");

    confidencePct = styledLabel("0.0%", 12, C_ACCENT);

    VBox.setMargin(confLbl,       new Insets(0, 0, 6, 0));
    VBox.setMargin(confidenceBar, new Insets(0, 0, 4, 0));

    Separator sep1 = styledSeparator();
    VBox.setMargin(sep1, new Insets(20, 0, 20, 0));

    GridPane stats = new GridPane();
    stats.setHgap(32);
    stats.setVgap(8);
    scoreLabel    = buildStatValue("0");
    streakLabel   = buildStatValue("0");
    progressLabel = buildStatValue("1 / 26");
    stats.add(buildStatLabel("SCORE"),    0, 0);
    stats.add(buildStatLabel("STREAK"),   1, 0);
    stats.add(buildStatLabel("PROGRESS"), 2, 0);
    stats.add(scoreLabel,                 0, 1);
    stats.add(streakLabel,                1, 1);
    stats.add(progressLabel,              2, 1);

    Separator sep2 = styledSeparator();
    VBox.setMargin(sep2, new Insets(20, 0, 20, 0));

    playerStatusBox = new VBox(8);
    playerStatusBox.setVisible(isBattle);
    playerStatusBox.setManaged(isBattle);

    Region spacer = new Region();
    VBox.setVgrow(spacer, Priority.ALWAYS);

    Button quit = ghostButton("QUIT SESSION");
    quit.setOnAction(e -> { stopSession(); showModeSelect(); });

    hud.getChildren().addAll(
        modeBadge, letterLabel, statusLabel,
        confLbl, confidenceBar, confidencePct,
        sep1, stats, sep2,
        playerStatusBox, spacer, quit);

    return hud;
  }

  // ── Launch Practice ──────────────────────────────────────────────────────

  private void launchPractice(String playerId) {
    PracticeSession ps = new PracticeSession(
        new MediaPipeRecognizer(), library, playerId);

    ps.setEventListener(new NoOpGameEventListener() {
      @Override
      public void onAttempt(String letter, AttemptRecord record, int streak) {
        Platform.runLater(() -> {
          double acc = record.getAccuracy();
          confidenceBar.setProgress(acc);
          confidencePct.setText(String.format("%.1f%%", acc * 100));
          confidenceBar.setStyle("-fx-accent:"
              + (record.isPassed() ? C_ACCENT : C_WARN) + ";"
              + "-fx-background-color:" + C_BORDER + ";"
              + "-fx-background-radius:2; -fx-border-radius:2;");
          streakLabel.setText(String.valueOf(streak));
          statusLabel.setText(record.isPassed()
              ? "✓  HIT  —  streak " + streak : "✗  MISS");
          statusLabel.setStyle("-fx-font-size:14px; -fx-font-family:'Courier New';"
              + "-fx-text-fill:" + (record.isPassed() ? C_ACCENT : C_WARN) + ";");
          flashLetter(record.isPassed());

          // Replay guidance video on miss
          if (!record.isPassed()) {
            playGuidanceVideo(letter, true);
          }
        });
      }

      @Override
      public void onLetterCleared(String letter, int letterScore, int total) {
        Platform.runLater(() -> {
          scoreLabel.setText(String.valueOf(total));
          statusLabel.setText("★  CLEARED  +" + letterScore);
          statusLabel.setStyle("-fx-font-size:14px; -fx-font-family:'Courier New';"
              + "-fx-text-fill:" + C_ACCENT + ";");
        });
      }

      @Override
      public void onSessionFinished(GameResult result) {
        Platform.runLater(() -> showPracticeResult((PracticeResult) result));
      }
    });

    this.session = ps;
    primaryStage.setScene(buildGameScene(false));
    updateLetterLabel(ps.getCurrentLetter());
    updateProgress(ps.getCurrentPosition(), ps.getTotalLetters());

    // Play guidance video for the first letter immediately
    playGuidanceVideo(ps.getCurrentLetter(), false);

    startLandmarkLoop(false, null);
  }

  // ── Launch Battle ────────────────────────────────────────────────────────

  private void launchBattle(List<String> playerIds) {
    BattleSession bs = new BattleSession(
        new MediaPipeRecognizer(), library, playerIds);

    bs.setEventListener(new NoOpGameEventListener() {
      @Override
      public void onRoundOpened(BattleRound round, List<String> active) {
        Platform.runLater(() -> {
          updateLetterLabel(round.getTargetLetter());
          progressLabel.setText("Round " + round.getRoundNumber());
          statusLabel.setText("Round " + round.getRoundNumber()
              + "  ·  " + active.size() + " players");
          statusLabel.setStyle("-fx-font-size:14px; -fx-font-family:'Courier New';"
              + "-fx-text-fill:" + C_MUTED + ";");
          rebuildPlayerStatus(active, List.of());
        });
      }

      @Override
      public void onAttempt(String letter, AttemptRecord record, int streak) {
        Platform.runLater(() -> {
          double acc = record.getAccuracy();
          confidenceBar.setProgress(acc);
          confidencePct.setText(String.format("%.1f%%", acc * 100));
          confidenceBar.setStyle("-fx-accent:"
              + (record.isPassed() ? C_ACCENT2 : C_WARN) + ";"
              + "-fx-background-color:" + C_BORDER + ";"
              + "-fx-background-radius:2; -fx-border-radius:2;");
          flashLetter(record.isPassed());
        });
      }

      @Override
      public void onRoundClosed(BattleRound round, List<String> eliminated) {
        Platform.runLater(() ->
            rebuildPlayerStatus(bs.getActivePlayers(), eliminated));
      }

      @Override
      public void onPlayerEliminated(String pid, int roundsCleared) {
        Platform.runLater(() -> {
          statusLabel.setText("✗  " + pid + " eliminated after " + roundsCleared + " rounds");
          statusLabel.setStyle("-fx-font-size:14px; -fx-font-family:'Courier New';"
              + "-fx-text-fill:" + C_WARN + ";");
        });
      }

      @Override
      public void onSessionFinished(GameResult result) {
        Platform.runLater(() -> showBattleResult((BattleResult) result));
      }
    });

    this.session       = bs;
    this.battleSession = bs;
    primaryStage.setScene(buildGameScene(true));
    startLandmarkLoop(true, playerIds);
  }

  // ── Launch Network Battle ────────────────────────────────────────────────

  private void launchNetworkBattle() {
    stopSession();
    stopCamera();
    aslframework.ui.network.NetworkedBattleUI ui =
        new aslframework.ui.network.NetworkedBattleUI(
            primaryStage, library, this::showModeSelect);
    ui.show();
  }

  private void launchWordShooter() {
    stopSession();
    stopCamera();  // GameUI camera not needed — WordShooterGame runs its own
    WordShooterGame game = new WordShooterGame(library, primaryStage, this::showModeSelect);
    game.start();
  }

  // ── Camera ───────────────────────────────────────────────────────────────

  private void startCamera(Canvas canvas) {
    camera = new VideoCapture(0);
    if (!camera.isOpened()) {
      GraphicsContext gc = canvas.getGraphicsContext2D();
      gc.setFill(Color.web(C_BG));
      gc.fillRect(0, 0, CAM_W, CAM_H);
      gc.setFill(Color.web(C_MUTED));
      gc.setFont(Font.font("Courier New", 14));
      gc.fillText("Camera unavailable", 20, 40);
      return;
    }

    cameraThread = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "camera-reader");
      t.setDaemon(true);
      return t;
    });

    cameraThread.submit(() -> {
      Mat frame = new Mat();
      while (!Thread.currentThread().isInterrupted()) {
        if (camera.read(frame) && !frame.empty()) {
          latestFrame.set(matToImage(frame));
        }
      }
      frame.release();
    });

    cameraTimer = new AnimationTimer() {
      @Override public void handle(long now) {
        Image img = latestFrame.get();
        if (img != null) {
          canvas.getGraphicsContext2D().drawImage(img, 0, 0, CAM_W, CAM_H);
        }
      }
    };
    cameraTimer.start();
  }

  /** Converts an OpenCV BGR Mat to a JavaFX Image. */
  private Image matToImage(Mat bgr) {
    Mat rgb = new Mat();
    Imgproc.cvtColor(bgr, rgb, Imgproc.COLOR_BGR2RGB);

    int    w    = rgb.cols();
    int    h    = rgb.rows();
    byte[] data = new byte[w * h * 3];
    rgb.get(0, 0, data);
    rgb.release();

    WritableImage wi = new WritableImage(w, h);
    wi.getPixelWriter().setPixels(
        0, 0, w, h,
        PixelFormat.getByteRgbInstance(),
        data, 0, w * 3);
    return wi;
  }

  private void stopCamera() {
    if (cameraTimer  != null) { cameraTimer.stop();        cameraTimer  = null; }
    if (cameraThread != null) { cameraThread.shutdownNow(); cameraThread = null; }
    if (camera       != null) { camera.release();           camera       = null; }
  }

  // ── Guidance image ────────────────────────────────────────────────────────

  /**
   * Loads the guidance image for the given letter.
   * Called when a new letter appears or when the player misses.
   * Does nothing if no image file exists for that letter.
   */
  void playGuidanceVideo(String letter, boolean replay) {
    if (guidanceImageView == null) return;
    if (!replay && letter.equals(currentGuidanceLetter)) return;
    currentGuidanceLetter = letter;

    File file = new File("assets/guidance/" + letter + "_test.jpg");
    if (!file.exists()) {
      System.out.println("[GameUI] No guidance image for: " + letter);
      guidanceImageView.setImage(null);
      return;
    }

    javafx.scene.image.Image img = new javafx.scene.image.Image(
        file.toURI().toString(), CAM_W, 240, true, true);
    guidanceImageView.setImage(img);
  }

  private void stopGuidanceVideo() {
    if (guidanceImageView != null) guidanceImageView.setImage(null);
    currentGuidanceLetter = "";
  }

  // ── Landmark loop ────────────────────────────────────────────────────────

  private void startLandmarkLoop(boolean isBattle, List<String> playerIds) {
    sessionRunning = true;

    try {
      bridge = new LandmarkBridge();
    } catch (LandmarkBridge.LandmarkBridgeException e) {
      Platform.runLater(() -> showAlert("Camera bridge error:\n" + e.getMessage()));
      return;
    }

    final int[] playerCursor = {0};

    landmarkThread = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "landmark-reader");
      t.setDaemon(true);
      return t;
    });

    landmarkThread.submit(() -> {
      while (sessionRunning && !Thread.currentThread().isInterrupted()) {
        try {
          List<HandLandmark> landmarks = bridge.nextLandmarks();
          if (session == null || session.isFinished()) break;

          if (!isBattle) {
            session.attempt(landmarks);
          } else {
            List<String> active = battleSession.getActivePlayers();
            if (active.isEmpty()) break;
            String pid = active.get(playerCursor[0] % active.size());
            playerCursor[0]++;
            battleSession.submitAttempt(pid, landmarks);
          }

          if (session.isFinished()) break;

          Platform.runLater(() -> {
            if (session != null && !session.isFinished()) {
              updateLetterLabel(session.getCurrentLetter());
              if (!isBattle) {
                PracticeSession ps = (PracticeSession) session;
                updateProgress(ps.getCurrentPosition(), ps.getTotalLetters());
                scoreLabel.setText(String.valueOf(ps.getTotalScore()));
              }
            }
          });

        } catch (LandmarkBridge.LandmarkBridgeException e) {
          if (sessionRunning) {
            Platform.runLater(() ->
                showAlert("Landmark bridge error:\n" + e.getMessage()));
          }
          break;
        }
      }

      // Natural end — call finish() if not already done
      if (session != null && !session.isFinished()) {
        GameResult result = session.finish();
        Platform.runLater(() -> {
          if (result instanceof PracticeResult pr) showPracticeResult(pr);
          else if (result instanceof BattleResult br) showBattleResult(br);
        });
      }
    });
  }

  private void stopSession() {
    sessionRunning = false;
    if (landmarkThread != null) { landmarkThread.shutdownNow(); landmarkThread = null; }
    if (bridge         != null) { bridge.close();               bridge         = null; }
    stopGuidanceVideo();
    session       = null;
    battleSession = null;
  }

  // ── Screen: Practice Result ──────────────────────────────────────────────

  private void showPracticeResult(PracticeResult result) {
    stopCamera();
    stopSession();

    VBox root = new VBox(0);
    root.setStyle("-fx-background-color:" + C_BG + ";");
    root.setAlignment(Pos.TOP_CENTER);
    root.setPadding(new Insets(64, 80, 64, 80));

    Label title = styledLabel(
        result.isCompleted() ? "SESSION COMPLETE" : "SESSION ENDED",
        13, C_ACCENT);
    title.setStyle(title.getStyle() + "-fx-letter-spacing:4px;");

    Label scoreVal = new Label(String.valueOf(result.getTotalScore()));
    scoreVal.setStyle("-fx-font-size:96px; -fx-font-weight:900;"
        + "-fx-text-fill:" + C_TEXT + "; -fx-font-family:'Courier New';");

    Label scoreLbl = styledLabel("TOTAL SCORE", 12, C_MUTED);

    Separator sep = styledSeparator();
    VBox.setMargin(sep, new Insets(32, 0, 32, 0));

    GridPane grid = new GridPane();
    grid.setHgap(48);
    grid.setVgap(10);
    grid.setAlignment(Pos.CENTER);

    long passed = result.getAttempts().stream().filter(AttemptRecord::isPassed).count();
    int  total  = result.getAttempts().size();
    String accStr = total > 0
        ? String.format("%.1f%%", 100.0 * passed / total) : "—";

    grid.add(buildStatLabel("LETTERS CLEARED"), 0, 0);
    grid.add(buildStatLabel("TOTAL ATTEMPTS"),  1, 0);
    grid.add(buildStatLabel("ACCURACY"),        2, 0);
    grid.add(buildStatValue(result.getLettersCleared() + " / " + result.getTotalLetters()), 0, 1);
    grid.add(buildStatValue(String.valueOf(total)), 1, 1);
    grid.add(buildStatValue(accStr), 2, 1);

    Region spacer = new Region();
    VBox.setVgrow(spacer, Priority.ALWAYS);

    Button again = accentButton("PLAY AGAIN", C_ACCENT);
    again.setOnAction(e -> showModeSelect());

    root.getChildren().addAll(title, scoreVal, scoreLbl, sep, grid, spacer, again);
    primaryStage.setScene(new Scene(root, WIN_W, WIN_H));
    animateFadeIn(root);
  }

  // ── Screen: Battle Result ────────────────────────────────────────────────

  private void showBattleResult(BattleResult result) {
    stopCamera();
    stopSession();

    VBox root = new VBox(0);
    root.setStyle("-fx-background-color:" + C_BG + ";");
    root.setAlignment(Pos.TOP_CENTER);
    root.setPadding(new Insets(64, 80, 64, 80));

    Label title = styledLabel("BATTLE COMPLETE", 13, C_ACCENT2);
    title.setStyle(title.getStyle() + "-fx-letter-spacing:4px;");

    String winnersText = result.getWinners().isEmpty()
        ? "No winner" : String.join(", ", result.getWinners());

    Label winnerVal = new Label(winnersText);
    winnerVal.setStyle("-fx-font-size:48px; -fx-font-weight:900;"
        + "-fx-text-fill:" + C_TEXT + "; -fx-font-family:'Courier New';");
    VBox.setMargin(winnerVal, new Insets(12, 0, 4, 0));

    Label winnerLbl = styledLabel(
        result.getWinners().size() > 1 ? "JOINT WINNERS" : "WINNER", 12, C_MUTED);

    Separator sep = styledSeparator();
    VBox.setMargin(sep, new Insets(32, 0, 24, 0));

    Label rankHdr = styledLabel("FINAL RANKINGS", 11, C_MUTED);
    rankHdr.setStyle(rankHdr.getStyle() + "-fx-letter-spacing:3px;");
    VBox.setMargin(rankHdr, new Insets(0, 0, 16, 0));

    VBox rankList = new VBox(10);
    rankList.setAlignment(Pos.CENTER);
    List<String> ranked = result.getRankedPlayers();
    for (int i = 0; i < ranked.size(); i++) {
      String  pid      = ranked.get(i);
      int     rounds   = result.getRoundsClearedPerPlayer().getOrDefault(pid, 0);
      boolean isWinner = result.getWinners().contains(pid);

      HBox row = new HBox(16);
      row.setAlignment(Pos.CENTER_LEFT);
      row.setMaxWidth(400);

      Label rankLbl = buildStatValue(String.valueOf(i + 1));
      rankLbl.setMinWidth(30);
      Label name = styledLabel(pid, 15, isWinner ? C_ACCENT2 : C_TEXT);
      Region sp  = new Region();
      HBox.setHgrow(sp, Priority.ALWAYS);
      Label clrd = styledLabel(rounds + " rounds", 13, C_MUTED);

      if (isWinner) {
        row.getChildren().addAll(rankLbl, name, sp, clrd, styledLabel("★", 15, C_ACCENT2));
      } else {
        row.getChildren().addAll(rankLbl, name, sp, clrd);
      }
      rankList.getChildren().add(row);
    }

    Label totalRounds = styledLabel(result.getTotalRounds() + " rounds played", 12, C_MUTED);
    VBox.setMargin(totalRounds, new Insets(24, 0, 0, 0));

    Region spacer = new Region();
    VBox.setVgrow(spacer, Priority.ALWAYS);

    Button again = accentButton("PLAY AGAIN", C_ACCENT2);
    again.setOnAction(e -> showModeSelect());

    root.getChildren().addAll(
        title, winnerVal, winnerLbl, sep,
        rankHdr, rankList, totalRounds,
        spacer, again);

    primaryStage.setScene(new Scene(root, WIN_W, WIN_H));
    animateFadeIn(root);
  }

  // ── Battle HUD helpers ───────────────────────────────────────────────────

  private void rebuildPlayerStatus(List<String> active, List<String> eliminated) {
    playerStatusBox.getChildren().clear();

    Label hdr = styledLabel("PLAYERS", 10, C_MUTED);
    hdr.setStyle(hdr.getStyle() + "-fx-letter-spacing:2px;");
    playerStatusBox.getChildren().add(hdr);

    for (String pid : active) {
      HBox row = new HBox(8);
      row.setAlignment(Pos.CENTER_LEFT);
      row.getChildren().addAll(
          new Circle(4, Color.web(C_ACCENT2)),
          styledLabel(pid, 13, C_TEXT));
      playerStatusBox.getChildren().add(row);
    }
    for (String pid : eliminated) {
      HBox row = new HBox(8);
      row.setAlignment(Pos.CENTER_LEFT);
      row.getChildren().addAll(
          new Circle(4, Color.web(C_WARN)),
          styledLabel(pid, 13, C_MUTED),
          styledLabel("✗", 11, C_WARN));
      playerStatusBox.getChildren().add(row);
    }
  }

  // ── Animations ───────────────────────────────────────────────────────────

  private void flashLetter(boolean passed) {
    if (letterLabel == null) return;
    new Timeline(
        new KeyFrame(Duration.ZERO,
            new KeyValue(letterLabel.textFillProperty(),
                Color.web(passed ? C_ACCENT : C_WARN))),
        new KeyFrame(Duration.millis(300),
            new KeyValue(letterLabel.textFillProperty(), Color.web(C_TEXT)))
    ).play();
  }

  private void animateFadeIn(javafx.scene.Node node) {
    node.setOpacity(0);
    FadeTransition ft = new FadeTransition(Duration.millis(400), node);
    ft.setFromValue(0);
    ft.setToValue(1);
    ft.play();
  }

  // ── UI helpers ───────────────────────────────────────────────────────────

  private void updateLetterLabel(String letter) {
    if (letterLabel != null && letter != null) {
      String prev = letterLabel.getText();
      letterLabel.setText(letter);
      // Play guidance video when letter advances to a new one
      if (!letter.equals(prev)) {
        playGuidanceVideo(letter, false);
      }
    }
  }

  private void updateProgress(int pos, int total) {
    if (progressLabel != null) progressLabel.setText(pos + " / " + total);
  }

  private Label styledLabel(String text, double size, String color) {
    Label l = new Label(text);
    l.setStyle("-fx-font-size:" + size + "px; -fx-text-fill:" + color
        + "; -fx-font-family:'Courier New';");
    return l;
  }

  private Label buildStatLabel(String text) { return styledLabel(text, 10, C_MUTED); }

  private Label buildStatValue(String text) {
    Label l = styledLabel(text, 22, C_TEXT);
    l.setStyle(l.getStyle() + "-fx-font-weight:700;");
    return l;
  }

  private Separator styledSeparator() {
    Separator s = new Separator();
    s.setStyle("-fx-background-color:" + C_BORDER + "; -fx-border-color:transparent;");
    return s;
  }

  private Button accentButton(String text, String color) {
    Button b = new Button(text);
    b.setStyle("-fx-background-color:" + color + ";"
        + "-fx-text-fill:#000; -fx-font-family:'Courier New';"
        + "-fx-font-size:13px; -fx-font-weight:700;"
        + "-fx-letter-spacing:2px; -fx-padding:12 32 12 32;"
        + "-fx-background-radius:3; -fx-cursor:hand;");
    b.setMaxWidth(Double.MAX_VALUE);
    return b;
  }

  private Button ghostButton(String text) {
    Button b = new Button(text);
    b.setStyle("-fx-background-color:transparent;"
        + "-fx-text-fill:" + C_MUTED + "; -fx-font-family:'Courier New';"
        + "-fx-font-size:12px; -fx-letter-spacing:2px;"
        + "-fx-padding:10 24 10 24; -fx-border-color:" + C_BORDER + ";"
        + "-fx-border-width:1; -fx-border-radius:3; -fx-cursor:hand;");
    b.setMaxWidth(Double.MAX_VALUE);
    return b;
  }

  private TextField styledTextField(String placeholder) {
    TextField tf = new TextField(placeholder);
    tf.setStyle("-fx-background-color:#1E1E26; -fx-text-fill:" + C_TEXT + ";"
        + "-fx-border-color:" + C_BORDER + "; -fx-font-family:'Courier New';"
        + "-fx-font-size:13px; -fx-padding:10 12 10 12;"
        + "-fx-border-radius:3; -fx-background-radius:3;");
    return tf;
  }

  private VBox buildSetupLayout(String title) {
    VBox root = new VBox(14);
    root.setStyle("-fx-background-color:" + C_BG + ";");
    root.setAlignment(Pos.CENTER);
    root.setPadding(new Insets(0, 220, 0, 220));
    root.setPrefSize(WIN_W, WIN_H);

    Label t = new Label(title);
    t.setStyle("-fx-font-size:13px; -fx-text-fill:" + C_ACCENT
        + "; -fx-font-family:'Courier New'; -fx-letter-spacing:4px;");
    VBox.setMargin(t, new Insets(0, 0, 16, 0));
    root.getChildren().add(t);
    return root;
  }

  private Region spacer(double h) {
    Region r = new Region();
    r.setPrefHeight(h);
    return r;
  }

  // ── Alerts ───────────────────────────────────────────────────────────────

  private void showAlert(String msg) {
    Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
    a.setHeaderText(null);
    a.showAndWait();
  }

  private void showFatalError(String msg) {
    showAlert(msg);
    Platform.exit();
  }

  // ── Shutdown ─────────────────────────────────────────────────────────────

  private void shutdown() {
    stopSession();
    stopCamera();
    Platform.exit();
  }

  // ── Entry point ──────────────────────────────────────────────────────────

  public static void main(String[] args) {
    launch(args);
  }
}