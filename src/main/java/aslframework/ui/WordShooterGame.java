package aslframework.ui;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.LandmarkBridge;
import aslframework.recognition.MediaPipeRecognizer;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Word Shooter — arcade-style ASL practice game.
 *
 * <p>Layout:
 * <pre>
 * ┌─────────────────────────┬──────────────────┐
 * │                         │  Camera feed     │
 * │   Game canvas           │  (live OpenCV)   │
 * │   Word falls here       │                  │
 * │   Console at bottom     ├──────────────────┤
 * │                         │  Guidance JPG    │
 * │                         │  (missing letter)│
 * └─────────────────────────┴──────────────────┘
 * </pre>
 *
 * <p>Gesture rules:
 * <ul>
 *   <li>A gesture must be held for {@link #HOLD_MS} ms before it fires.</li>
 *   <li>After firing, a {@link #COOLDOWN_MS} ms cooldown prevents re-firing.</li>
 * </ul>
 */
public class WordShooterGame {

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color C_BG      = Color.web("#0D0D0F");
  private static final Color C_SURFACE = Color.web("#16161A");
  private static final Color C_BORDER  = Color.web("#2A2A32");
  private static final Color C_ACCENT  = Color.web("#1ABCB8");
  private static final Color C_ACCENT2 = Color.web("#F5A800");
  private static final Color C_WARN    = Color.web("#FF6B4A");
  private static final Color C_TEXT    = Color.web("#E8E8F0");
  private static final Color C_MUTED   = Color.web("#6B6B80");

  // ── Dimensions ────────────────────────────────────────────────────────────
  private static final int    WIN_W         = 1100;
  private static final int    WIN_H         = 720;
  private static final int    GAME_W        = 700;   // game canvas width
  private static final int    PANEL_W       = WIN_W - GAME_W;  // right panel
  private static final int    CAM_H         = 360;
  private static final int    GUIDE_H       = WIN_H - CAM_H;
  private static final int    CONSOLE_W     = 110;
  private static final int    CONSOLE_H     = 18;
  private static final int    CONSOLE_Y     = WIN_H - 90;
  private static final int    CONSOLE_SPEED = 5;
  private static final double WORD_SPEED    = 0.35;
  private static final double BULLET_SPEED  = 3.5;
  private static final double THRESHOLD     = 0.82;

  // ── Gesture timing ────────────────────────────────────────────────────────
  /** How long the same letter must be held before it fires (ms). */
  private static final long HOLD_MS     = 1000;
  /** Minimum time between shots (ms). */
  private static final long COOLDOWN_MS = 3000;

  // ── Word list ─────────────────────────────────────────────────────────────
  private static final String[] WORDS = {
      "APPLE", "BRAVE", "CRANE", "DREAM", "EAGLE",
      "FLAME", "GRACE", "HEART", "INPUT", "JEWEL",
      "KNACK", "LIGHT", "MAGIC", "NERVE", "OCEAN",
      "PRISM", "QUEST", "RIVER", "SPARK", "TIGER",
      "ULTRA", "VIVID", "WEAVE", "XENON", "YIELD", "ZEBRA",
      "BLOOM", "CHESS", "DWELL", "FROZE", "GLEAM",
      "HATCH", "IVORY", "JOUST", "KARMA", "LEMON"
  };

  // ── Game state ────────────────────────────────────────────────────────────
  private String  currentWord   = "";
  private int     blankIndex    = 0;
  private double  wordX         = GAME_W / 2.0;
  private double  wordY         = 60;
  private double  consoleX      = GAME_W / 2.0 - CONSOLE_W / 2.0;
  private int     score         = 0;
  private int     lives         = 3;
  private boolean gameOver      = false;
  private String  feedbackText  = "";
  private long    feedbackUntil = 0;

  // Keyboard
  private boolean keyA = false;
  private boolean keyD = false;

  // Bullets
  private final List<Bullet> bullets = new ArrayList<>();

  // ── Gesture hold / cooldown state ─────────────────────────────────────────
  /** Letter currently being held. */
  private String heldLetter      = null;
  /** When we first saw this held letter (ms). */
  private long   holdStartMs     = 0;
  /** When the last shot was fired (ms). */
  private long   lastShotMs      = 0;
  /** Letter queued to fire after hold confirmed (set on FX thread). */
  private String pendingFire     = null;
  /** Current best-recognised letter from background thread. */
  private volatile String recognisedLetter = null;
  /** Charge progress 0.0–1.0 shown in UI. */
  private double chargeProgress  = 0.0;

  // ── FX nodes ─────────────────────────────────────────────────────────────
  private Canvas        gameCanvas;
  private AnimationTimer gameLoop;
  private Stage         stage;
  private Runnable      onExit;
  private ImageView     guidanceView;

  // ── Camera ────────────────────────────────────────────────────────────────
  private VideoCapture                 camera;
  private final AtomicReference<Image> latestFrame = new AtomicReference<>();
  private ExecutorService              cameraThread;
  private AnimationTimer               cameraTimer;
  private Canvas                       camCanvas;

  // ── Recognition ──────────────────────────────────────────────────────────
  private GestureLibrary  library;
  private LandmarkBridge  bridge;
  private ExecutorService gestureThread;
  private final Random    rng = new Random();

  // ── Fonts ─────────────────────────────────────────────────────────────────
  private static final Font F_WORD     = Font.font("Courier New", FontWeight.BOLD,   46);
  private static final Font F_HUD      = Font.font("Courier New", FontWeight.BOLD,   15);
  private static final Font F_SMALL    = Font.font("Courier New", FontWeight.NORMAL, 12);
  private static final Font F_BULLET   = Font.font("Courier New", FontWeight.BOLD,   20);
  private static final Font F_OVER     = Font.font("Courier New", FontWeight.BOLD,   52);
  private static final Font F_FEEDBACK = Font.font("Courier New", FontWeight.BOLD,   22);
  private static final Font F_HELD     = Font.font("Courier New", FontWeight.BOLD,   32);

  // ─────────────────────────────────────────────────────────────────────────

  public WordShooterGame(GestureLibrary library, Stage stage, Runnable onExit) {
    this.library = library;
    this.stage   = stage;
    this.onExit  = onExit;
  }

  // ── Build & start ─────────────────────────────────────────────────────────

  public void start() {
    // Game canvas (left)
    gameCanvas = new Canvas(GAME_W, WIN_H);
    StackPane gamePane = new StackPane(gameCanvas);
    gamePane.setMinSize(GAME_W, WIN_H);
    gamePane.setMaxSize(GAME_W, WIN_H);

    // Camera canvas (right top)
    camCanvas = new Canvas(PANEL_W, CAM_H);
    StackPane camPane = new StackPane(camCanvas);
    camPane.setStyle("-fx-background-color:#000;");
    camPane.setMinSize(PANEL_W, CAM_H);
    camPane.setMaxSize(PANEL_W, CAM_H);

    // Guidance image (right bottom)
    guidanceView = new ImageView();
    guidanceView.setFitWidth(PANEL_W);
    guidanceView.setFitHeight(GUIDE_H);
    guidanceView.setPreserveRatio(true);

    StackPane guidePane = new StackPane(guidanceView);
    guidePane.setStyle("-fx-background-color:#111;");
    guidePane.setAlignment(Pos.CENTER);
    guidePane.setMinSize(PANEL_W, GUIDE_H);
    guidePane.setMaxSize(PANEL_W, GUIDE_H);

    // Right panel label
    javafx.scene.control.Label guideLabel =
        new javafx.scene.control.Label("GESTURE GUIDE");
    guideLabel.setStyle("-fx-font-family:'Courier New'; -fx-font-size:10px;"
        + "-fx-text-fill:#6B6B80; -fx-letter-spacing:2px;");
    guideLabel.setPadding(new Insets(4, 0, 2, 0));

    VBox rightPanel = new VBox(0, camPane, guideLabel, guidePane);
    rightPanel.setStyle("-fx-background-color:#16161A;");
    rightPanel.setMinWidth(PANEL_W);
    rightPanel.setMaxWidth(PANEL_W);

    HBox root = new HBox(gamePane, rightPanel);
    root.setStyle("-fx-background-color:#0D0D0F;");

    Scene scene = new Scene(root, WIN_W, WIN_H);
    stage.setWidth(WIN_W);
    stage.setHeight(WIN_H);
    stage.setScene(scene);

    wireKeyboard(scene);
    spawnWord();
    startCamera();
    startGestureListener();
    startGameLoop();
  }

  // ── Game loop ─────────────────────────────────────────────────────────────

  private void startGameLoop() {
    gameLoop = new AnimationTimer() {
      @Override public void handle(long now) {
        if (!gameOver) update();
        renderGame();
        renderCamera();
      }
    };
    gameLoop.start();
  }

  private void update() {
    long nowMs = System.currentTimeMillis();

    // Console movement
    if (keyA) consoleX = Math.max(0, consoleX - CONSOLE_SPEED);
    if (keyD) consoleX = Math.min(GAME_W - CONSOLE_W, consoleX + CONSOLE_SPEED);

    // ── Gesture hold logic ────────────────────────────────────────────────
    String rec = recognisedLetter;
    if (rec != null) {
      if (rec.equals(heldLetter)) {
        // Same letter — check if hold duration met
        long held = nowMs - holdStartMs;
        chargeProgress = Math.min(1.0, (double) held / HOLD_MS);
        if (held >= HOLD_MS && nowMs - lastShotMs >= COOLDOWN_MS) {
          // Fire!
          pendingFire    = rec;
          heldLetter     = null;
          chargeProgress = 0;
          lastShotMs     = nowMs;
        }
      } else {
        // New letter — reset hold
        heldLetter     = rec;
        holdStartMs    = nowMs;
        chargeProgress = 0;
      }
    } else {
      heldLetter     = null;
      chargeProgress = 0;
    }

    // Fire queued bullet
    if (pendingFire != null) {
      fireBullet(pendingFire);
      pendingFire = null;
    }

    // Word falls
    wordY += WORD_SPEED;
    if (wordY > CONSOLE_Y - 20) {
      loseLife("MISSED!");
      return;
    }

    // Bullets travel up
    List<Bullet> toRemove = new ArrayList<>();
    for (Bullet b : bullets) {
      b.y -= BULLET_SPEED;
      if (b.y < 0) { toRemove.add(b); continue; }
      // Hit test
      if (b.y <= wordY + 26 && b.y >= wordY - 26) {
        double blankX = getBlankScreenX();
        if (Math.abs(b.x - blankX) < 30) {
          if (b.letter.equals(String.valueOf(currentWord.charAt(blankIndex)))) {
            score += 100 * currentWord.length();
            showFeedback("✓  +" + (100 * currentWord.length()), true);
            toRemove.add(b);
            spawnWord();
          } else {
            showFeedback("✗  WRONG", false);
            toRemove.add(b);
          }
        }
      }
    }
    bullets.removeAll(toRemove);
  }

  // ── Rendering ─────────────────────────────────────────────────────────────

  private void renderGame() {
    GraphicsContext gc = gameCanvas.getGraphicsContext2D();

    // Background
    gc.setFill(C_BG);
    gc.fillRect(0, 0, GAME_W, WIN_H);

    // Grid
    gc.setStroke(Color.web("#1A1A22"));
    gc.setLineWidth(1);
    for (int x = 0; x < GAME_W; x += 55) gc.strokeLine(x, 0, x, WIN_H);
    for (int y = 0; y < WIN_H; y += 55) gc.strokeLine(0, y, GAME_W, y);

    if (!gameOver) {
      renderWord(gc);
      renderBullets(gc);
      renderConsole(gc);
      renderHeldLetter(gc);
      renderHUD(gc);
      renderFeedback(gc);
    } else {
      renderGameOver(gc);
    }
  }

  private void renderWord(GraphicsContext gc) {
    if (currentWord.isEmpty()) return;
    gc.setFont(F_WORD);
    gc.setTextAlign(TextAlignment.CENTER);

    double charW  = 34;
    double startX = wordX - (currentWord.length() * charW) / 2.0 + charW / 2.0;

    for (int i = 0; i < currentWord.length(); i++) {
      double cx = startX + i * charW;
      if (i == blankIndex) {
        // Glowing blank slot
        gc.setFill(C_ACCENT.deriveColor(0, 1, 1, 0.12));
        gc.fillRoundRect(cx - 18, wordY - 40, 36, 48, 6, 6);
        gc.setStroke(C_ACCENT);
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(cx - 18, wordY - 40, 36, 48, 6, 6);
        gc.setFill(C_ACCENT);
        gc.fillText("_", cx, wordY);
      } else {
        gc.setFill(C_TEXT);
        gc.fillText(String.valueOf(currentWord.charAt(i)), cx, wordY);
      }
    }
  }

  private void renderBullets(GraphicsContext gc) {
    gc.setFont(F_BULLET);
    gc.setTextAlign(TextAlignment.CENTER);
    for (Bullet b : bullets) {
      gc.setFill(C_ACCENT2.deriveColor(0, 1, 1, 0.25));
      gc.fillOval(b.x - 9, b.y + 2, 18, 20);
      gc.setFill(C_ACCENT2);
      gc.fillText(b.letter, b.x, b.y + 2);
      gc.setStroke(C_ACCENT2.deriveColor(0, 1, 1, 0.35));
      gc.setLineWidth(2);
      gc.strokeLine(b.x, b.y + 22, b.x, b.y + 40);
    }
  }

  private void renderConsole(GraphicsContext gc) {
    double cx = consoleX;
    double cy = CONSOLE_Y;

    gc.setFill(C_SURFACE);
    gc.fillRoundRect(cx, cy, CONSOLE_W, CONSOLE_H, 6, 6);
    gc.setStroke(C_ACCENT);
    gc.setLineWidth(2);
    gc.strokeRoundRect(cx, cy, CONSOLE_W, CONSOLE_H, 6, 6);

    // Barrel
    double bx = cx + CONSOLE_W / 2.0;
    gc.setStroke(C_ACCENT);
    gc.setLineWidth(4);
    gc.strokeLine(bx, cy, bx, cy - 20);

    // Charge bar above barrel
    if (chargeProgress > 0) {
      double barW = CONSOLE_W * chargeProgress;
      gc.setFill(C_ACCENT.deriveColor(0, 1, 1, 0.3));
      gc.fillRoundRect(cx, cy - 10, CONSOLE_W, 6, 3, 3);
      gc.setFill(C_ACCENT);
      gc.fillRoundRect(cx, cy - 10, barW, 6, 3, 3);
    }

    // Nozzle glow
    gc.setFill(chargeProgress >= 1.0 ? C_ACCENT2 : C_ACCENT);
    gc.fillOval(bx - 5, cy - 26, 10, 10);

    // A / D labels
    gc.setFont(F_SMALL);
    gc.setTextAlign(TextAlignment.LEFT);
    gc.setFill(C_MUTED);
    gc.fillText("A", cx + 6, cy + 13);
    gc.setTextAlign(TextAlignment.RIGHT);
    gc.fillText("D", cx + CONSOLE_W - 6, cy + 13);

    // Ground line
    gc.setStroke(C_BORDER);
    gc.setLineWidth(1);
    gc.strokeLine(0, CONSOLE_Y + CONSOLE_H + 12, GAME_W, CONSOLE_Y + CONSOLE_H + 12);
  }

  private void renderHeldLetter(GraphicsContext gc) {
    // Show currently held letter with cooldown indicator
    long nowMs    = System.currentTimeMillis();
    long cooldown = nowMs - lastShotMs;
    boolean onCooldown = cooldown < COOLDOWN_MS;

    gc.setFont(F_SMALL);
    gc.setTextAlign(TextAlignment.LEFT);

    if (heldLetter != null && !onCooldown) {
      // Big held letter above console
      gc.setFont(F_HELD);
      gc.setTextAlign(TextAlignment.CENTER);
      gc.setFill(C_ACCENT2.deriveColor(0, 1, 1, 0.6 + 0.4 * chargeProgress));
      gc.fillText(heldLetter, consoleX + CONSOLE_W / 2.0, CONSOLE_Y - 40);
    }

    if (onCooldown) {
      double remaining = (COOLDOWN_MS - cooldown) / 1000.0;
      gc.setFont(F_SMALL);
      gc.setTextAlign(TextAlignment.CENTER);
      gc.setFill(C_WARN.deriveColor(0, 1, 1, 0.7));
      gc.fillText(String.format("%.1fs", remaining),
          consoleX + CONSOLE_W / 2.0, CONSOLE_Y - 30);
    }
  }

  private void renderHUD(GraphicsContext gc) {
    // Top-left HUD box
    gc.setFill(C_SURFACE.deriveColor(0, 1, 1, 0.85));
    gc.fillRoundRect(14, 14, 180, 120, 8, 8);
    gc.setStroke(C_BORDER);
    gc.setLineWidth(1);
    gc.strokeRoundRect(14, 14, 180, 120, 8, 8);

    gc.setTextAlign(TextAlignment.LEFT);

    gc.setFont(F_SMALL);
    gc.setFill(C_MUTED);
    gc.fillText("SCORE", 30, 42);
    gc.setFont(F_HUD);
    gc.setFill(C_ACCENT);
    gc.fillText(String.valueOf(score), 30, 62);

    gc.setFont(F_SMALL);
    gc.setFill(C_MUTED);
    gc.fillText("LIVES", 30, 88);
    StringBuilder hearts = new StringBuilder();
    for (int i = 0; i < lives; i++) hearts.append("♥ ");
    gc.setFont(F_HUD);
    gc.setFill(C_WARN);
    gc.fillText(hearts.toString().trim(), 30, 108);

    // Controls hint at bottom
    gc.setFont(F_SMALL);
    gc.setFill(C_MUTED);
    gc.setTextAlign(TextAlignment.CENTER);
    gc.fillText("A ◄  ► D     ESC quit", GAME_W / 2.0, WIN_H - 18);
  }

  private void renderFeedback(GraphicsContext gc) {
    if (feedbackText.isEmpty()) return;
    if (System.currentTimeMillis() > feedbackUntil) { feedbackText = ""; return; }
    gc.setFont(F_FEEDBACK);
    gc.setTextAlign(TextAlignment.CENTER);
    gc.setFill(feedbackText.startsWith("✓") ? C_ACCENT : C_WARN);
    gc.fillText(feedbackText, GAME_W / 2.0, WIN_H / 2.0 - 40);
  }

  private void renderGameOver(GraphicsContext gc) {
    gc.setFill(Color.color(0, 0, 0, 0.78));
    gc.fillRect(0, 0, GAME_W, WIN_H);
    gc.setTextAlign(TextAlignment.CENTER);
    gc.setFont(F_OVER);
    gc.setFill(C_WARN);
    gc.fillText("GAME OVER", GAME_W / 2.0, WIN_H / 2.0 - 60);
    gc.setFont(F_HUD);
    gc.setFill(C_TEXT);
    gc.fillText("SCORE: " + score, GAME_W / 2.0, WIN_H / 2.0);
    gc.setFont(F_SMALL);
    gc.setFill(C_MUTED);
    gc.fillText("Press ENTER to return to menu", GAME_W / 2.0, WIN_H / 2.0 + 50);
  }

  private void renderCamera() {
    Image img = latestFrame.get();
    if (img == null) return;
    GraphicsContext gc = camCanvas.getGraphicsContext2D();
    gc.drawImage(img, 0, 0, PANEL_W, CAM_H);
  }

  // ── Camera ────────────────────────────────────────────────────────────────

  private void startCamera() {
    camera = new VideoCapture(0);
    if (!camera.isOpened()) return;

    cameraThread = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "shooter-cam");
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
  }

  private Image matToImage(Mat bgr) {
    Mat rgb = new Mat();
    Imgproc.cvtColor(bgr, rgb, Imgproc.COLOR_BGR2RGB);
    int w = rgb.cols(), h = rgb.rows();
    byte[] data = new byte[w * h * 3];
    rgb.get(0, 0, data);
    rgb.release();
    WritableImage wi = new WritableImage(w, h);
    wi.getPixelWriter().setPixels(0, 0, w, h,
        PixelFormat.getByteRgbInstance(), data, 0, w * 3);
    return wi;
  }

  private void stopCamera() {
    if (cameraThread != null) { cameraThread.shutdownNow(); cameraThread = null; }
    if (camera       != null) { camera.release();           camera       = null; }
  }

  // ── Guidance image ────────────────────────────────────────────────────────

  private void loadGuidanceImage(String letter) {
    File file = new File("assets/guidance/" + letter + "_test.jpg");
    if (file.exists()) {
      Image img = new Image(file.toURI().toString(),
          PANEL_W, GUIDE_H, true, true);
      guidanceView.setImage(img);
    } else {
      guidanceView.setImage(null);
    }
  }

  // ── Game mechanics ────────────────────────────────────────────────────────

  private void spawnWord() {
    currentWord = WORDS[rng.nextInt(WORDS.length)];
    blankIndex  = rng.nextInt(currentWord.length());
    wordX       = 80 + rng.nextInt(GAME_W - 160);
    wordY       = 60;
    bullets.clear();
    heldLetter     = null;
    chargeProgress = 0;
    // Load guidance image for the missing letter
    Platform.runLater(() ->
        loadGuidanceImage(String.valueOf(currentWord.charAt(blankIndex))));
  }

  private void fireBullet(String letter) {
    double bx = consoleX + CONSOLE_W / 2.0;
    bullets.add(new Bullet(bx, CONSOLE_Y - 24, letter));
  }

  private double getBlankScreenX() {
    double charW  = 34;
    double startX = wordX - (currentWord.length() * charW) / 2.0 + charW / 2.0;
    return startX + blankIndex * charW;
  }

  private void loseLife(String reason) {
    lives--;
    showFeedback(reason, false);
    if (lives <= 0) gameOver = true;
    else spawnWord();
  }

  private void showFeedback(String text, boolean good) {
    feedbackText  = text;
    feedbackUntil = System.currentTimeMillis() + 900;
  }

  // ── Keyboard ─────────────────────────────────────────────────────────────

  private void wireKeyboard(Scene scene) {
    scene.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.A)     keyA = true;
      if (e.getCode() == KeyCode.D)     keyD = true;
      if (e.getCode() == KeyCode.ESCAPE) stop();
      if (e.getCode() == KeyCode.ENTER && gameOver) stop();
    });
    scene.setOnKeyReleased(e -> {
      if (e.getCode() == KeyCode.A) keyA = false;
      if (e.getCode() == KeyCode.D) keyD = false;
    });
  }

  // ── Gesture recognition ───────────────────────────────────────────────────

  private void startGestureListener() {
    if (library == null) return;
    try {
      bridge = new LandmarkBridge();
    } catch (LandmarkBridge.LandmarkBridgeException e) {
      System.err.println("[WordShooter] Bridge failed: " + e.getMessage());
      return;
    }

    MediaPipeRecognizer recognizer = new MediaPipeRecognizer();

    gestureThread = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "shooter-gesture");
      t.setDaemon(true);
      return t;
    });

    gestureThread.submit(() -> {
      while (!Thread.currentThread().isInterrupted() && !gameOver) {
        try {
          List<HandLandmark> landmarks = bridge.nextLandmarks();
          if (landmarks == null || landmarks.isEmpty()) {
            recognisedLetter = null;
            continue;
          }

          String bestLetter = null;
          double bestScore  = 0;

          for (char c = 'A'; c <= 'Z'; c++) {
            String letter = String.valueOf(c);
            List<GestureDefinition> variants =
                library.getGestureVariants(letter);
            if (variants == null || variants.isEmpty()) continue;
            double s = recognizer.recognize(landmarks, variants)
                .getConfidenceScore();
            if (s > bestScore) { bestScore = s; bestLetter = letter; }
          }

          recognisedLetter = (bestLetter != null && bestScore >= THRESHOLD)
              ? bestLetter : null;

        } catch (LandmarkBridge.LandmarkBridgeException e) {
          break;
        }
      }
    });
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  public void stop() {
    if (gameLoop     != null) { gameLoop.stop();              gameLoop     = null; }
    if (gestureThread!= null) { gestureThread.shutdownNow();  gestureThread= null; }
    if (bridge       != null) { bridge.close();               bridge       = null; }
    stopCamera();
    if (onExit != null) Platform.runLater(onExit);
  }

  // ── Inner: Bullet ─────────────────────────────────────────────────────────

  private static class Bullet {
    double x, y;
    String letter;
    Bullet(double x, double y, String letter) {
      this.x = x; this.y = y; this.letter = letter;
    }
  }
}