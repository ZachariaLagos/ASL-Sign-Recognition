package aslframework.ui.network;

import aslframework.recognition.GestureLibrary;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * JavaFX UI for the networked battle mode.
 *
 * <p>Shows two screens depending on the role:
 * <ul>
 *   <li><b>Setup screen</b> — player enters name, chooses Host or Join.</li>
 *   <li><b>Game screen</b> — live camera feed, current round letter, both
 *       players' status, charge bar, round history.</li>
 * </ul>
 *
 * <p>The host starts a {@link BattleServer} and also connects as a client.
 * The joining player connects directly as a client.
 */
public class NetworkedBattleUI {

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final String C_BG      = "#0D0D0F";
  private static final String C_SURFACE = "#16161A";
  private static final String C_BORDER  = "#2A2A32";
  private static final String C_ACCENT  = "#1ABCB8";
  private static final String C_ACCENT2 = "#F5A800";
  private static final String C_WARN    = "#FF6B4A";
  private static final String C_TEXT    = "#E8E8F0";
  private static final String C_MUTED   = "#6B6B80";

  // ── Dimensions ────────────────────────────────────────────────────────────
  private static final int W      = 1100;
  private static final int H      = 720;
  private static final int CAM_W  = 400;
  private static final int CAM_H  = 300;

  // ── State ─────────────────────────────────────────────────────────────────
  private final Stage          stage;
  private final GestureLibrary library;
  private final Runnable       onExit;

  private BattleServer server;    // only on host
  private BattleClient client;

  // Game state received from server
  private String       currentLetter  = "?";
  private int          currentRound   = 0;
  private List<String> activePlayers  = new ArrayList<>();
  private List<String> allPlayers     = new ArrayList<>();
  private final List<String> log      = new ArrayList<>();
  private boolean      gameOver       = false;
  private String       winnerText     = "";

  // Camera
  private VideoCapture                 camera;
  private final AtomicReference<Image> latestFrame = new AtomicReference<>();
  private ExecutorService              cameraThread;

  // UI nodes
  private Label        letterLabel;
  private Label        roundLabel;
  private Label        statusLabel;
  private ProgressBar  chargeBar;
  private Label        chargeLetter;
  private VBox         playerList;
  private VBox         logBox;
  private Canvas       camCanvas;

  // Game loop
  private AnimationTimer uiTimer;

  // ─────────────────────────────────────────────────────────────────────────

  public NetworkedBattleUI(Stage stage, GestureLibrary library, Runnable onExit) {
    this.stage   = stage;
    this.library = library;
    this.onExit  = onExit;
  }

  // ── Entry point ───────────────────────────────────────────────────────────

  public void show() {
    stage.setWidth(W);
    stage.setHeight(H);
    showSetup();
  }

  // ── Setup screen ──────────────────────────────────────────────────────────

  private void showSetup() {
    VBox root = new VBox(20);
    root.setStyle("-fx-background-color:" + C_BG + ";");
    root.setAlignment(Pos.CENTER);
    root.setPadding(new Insets(60, 160, 60, 160));

    Label title = label("NETWORK BATTLE", 13, C_ACCENT);
    title.setStyle(title.getStyle() + "-fx-letter-spacing:4px;");

    Label nameLbl = label("Your name:", 13, C_MUTED);
    TextField nameField = styledTextField("Player1");

    Label roleLbl = label("Role:", 13, C_MUTED);

    // Host section
    VBox hostBox = new VBox(10);
    hostBox.setPadding(new Insets(16));
    hostBox.setStyle("-fx-background-color:" + C_SURFACE + ";"
        + "-fx-border-color:" + C_BORDER + ";"
        + "-fx-border-width:1; -fx-border-radius:4; -fx-background-radius:4;");

    Label hostTitle = label("HOST", 14, C_ACCENT);
    Label hostDesc  = label("Start a server. Share your IP with the other player.", 12, C_MUTED);
    Label ipLabel   = label("Your IP:  " + BattleServer.getLocalIP()
        + "  port " + BattleServer.DEFAULT_PORT, 12, C_ACCENT);

    Button hostBtn = accentButton("START AS HOST", C_ACCENT);
    hostBtn.setOnAction(e -> {
      String name = nameField.getText().trim();
      if (name.isEmpty()) name = "Player1";
      startAsHost(name);
    });

    hostBox.getChildren().addAll(hostTitle, hostDesc, ipLabel, hostBtn);

    // Join section
    VBox joinBox = new VBox(10);
    joinBox.setPadding(new Insets(16));
    joinBox.setStyle("-fx-background-color:" + C_SURFACE + ";"
        + "-fx-border-color:" + C_BORDER + ";"
        + "-fx-border-width:1; -fx-border-radius:4; -fx-background-radius:4;");

    Label joinTitle = label("JOIN", 14, C_ACCENT2);
    Label joinDesc  = label("Enter the host's IP address to join.", 12, C_MUTED);
    TextField ipField = styledTextField("192.168.1.x");

    Button joinBtn = accentButton("JOIN GAME", C_ACCENT2);
    joinBtn.setOnAction(e -> {
      String name = nameField.getText().trim();
      if (name.isEmpty()) name = "Player2";
      String ip = ipField.getText().trim();
      if (ip.isEmpty()) ip = "127.0.0.1";
      startAsClient(name, ip);
    });

    joinBox.getChildren().addAll(joinTitle, joinDesc, ipField, joinBtn);

    // Back
    Button back = ghostButton("← BACK");
    back.setOnAction(e -> onExit.run());

    root.getChildren().addAll(title, spacer(10), nameLbl, nameField,
        spacer(10), roleLbl, hostBox, joinBox, spacer(8), back);

    stage.setScene(new Scene(root, W, H));
  }

  // ── Host ──────────────────────────────────────────────────────────────────

  private void startAsHost(String playerName) {
    String mobileUrl = "http://" + BattleServer.getLocalIP()
        + ":" + MobileServer.HTTP_PORT;

    showWaitingWithQR(
        "Server started  ·  waiting for opponent\n"
            + "Java client: " + BattleServer.getLocalIP()
            + "  port " + BattleServer.DEFAULT_PORT + "\n"
            + "Phone: scan QR code",
        mobileUrl);

    server = new BattleServer(library);
    server.start(BattleServer.DEFAULT_PORT, msg ->
        Platform.runLater(() -> appendLog("[Server] " + msg)));

    // Host also connects as a client to localhost
    client = new BattleClient(playerName, library);
    new Thread(() -> {
      try { Thread.sleep(600); } catch (InterruptedException ignored) {}
      Platform.runLater(() ->
          client.connect("127.0.0.1", BattleServer.DEFAULT_PORT,
              this::onServerMessage,
              msg -> Platform.runLater(() -> appendLog("[Client] " + msg))));
    }, "host-client-connect").start();
  }

  // ── Client ────────────────────────────────────────────────────────────────

  private void startAsClient(String playerName, String hostIp) {
    showWaiting("Connecting to " + hostIp + "...");
    client = new BattleClient(playerName, library);
    client.connect(hostIp, BattleServer.DEFAULT_PORT,
        this::onServerMessage,
        msg -> Platform.runLater(() -> appendLog("[Client] " + msg)));
  }

  // ── Message handler ───────────────────────────────────────────────────────

  private void onServerMessage(String json) {
    Platform.runLater(() -> {
      String type = BattleServer.extractString(json, "type");
      if (type == null) return;

      switch (type) {
        case "WAITING" -> {
          String msg = BattleServer.extractString(json, "message");
          appendLog(msg != null ? msg : "Waiting...");
        }
        case "START" -> {
          allPlayers  = parseArray(json, "players");
          activePlayers = new ArrayList<>(allPlayers);
          appendLog("Game started! Players: " + allPlayers);
          showGameScreen();
        }
        case "ROUND" -> {
          currentLetter = BattleServer.extractString(json, "letter");
          currentRound  = (int) BattleServer.extractDouble(json, "round");
          activePlayers = parseArray(json, "active");
          appendLog("Round " + currentRound + " — sign: " + currentLetter);
          updateGameUI();
        }
        case "ATTEMPT" -> {
          String player = BattleServer.extractString(json, "player");
          String letter = BattleServer.extractString(json, "letter");
          boolean passed = json.contains("\"passed\":true");
          appendLog((passed ? "✓ " : "✗ ") + player + " signed " + letter);
          updateGameUI();
        }
        case "ELIMINATED" -> {
          String player = BattleServer.extractString(json, "player");
          appendLog("💀 " + player + " eliminated!");
          if (activePlayers != null) activePlayers.remove(player);
          updateGameUI();
        }
        case "ROUND_CLOSED" -> {
          updateGameUI();
        }
        case "WINNER" -> {
          List<String> winners = parseArray(json, "winners");
          winnerText = "Winner: " + String.join(", ", winners);
          appendLog("🏆 " + winnerText);
          gameOver = true;
          updateGameUI();
        }
        case "GAME_OVER" -> {
          gameOver = true;
          updateGameUI();
        }
      }
    });
  }

  // ── Waiting screen with QR ────────────────────────────────────────────────

  private void showWaitingWithQR(String message, String qrUrl) {
    VBox root = new VBox(20);
    root.setStyle("-fx-background-color:" + C_BG + ";");
    root.setAlignment(Pos.CENTER);
    root.setPadding(new Insets(40));

    Label title = label("NETWORK BATTLE", 13, C_ACCENT);
    title.setStyle(title.getStyle() + "-fx-letter-spacing:4px;");

    Label msg = label(message, 13, C_TEXT);
    msg.setWrapText(true);
    msg.setTextAlignment(TextAlignment.CENTER);

    // QR code
    javafx.scene.image.WritableImage qr =
        QRCodeGenerator.generate(qrUrl, 220);
    javafx.scene.image.ImageView qrView =
        new javafx.scene.image.ImageView(qr);
    qrView.setFitWidth(220);
    qrView.setFitHeight(220);

    Label qrLabel = label("Scan with phone to join", 11, C_MUTED);
    Label urlLabel = label(qrUrl, 11, C_ACCENT);

    logBox = new VBox(4);
    logBox.setMaxWidth(500);
    ScrollPane scroll = new ScrollPane(logBox);
    scroll.setMaxWidth(500);
    scroll.setMaxHeight(150);
    scroll.setStyle("-fx-background-color:transparent; -fx-background:transparent;");

    Button cancel = ghostButton("CANCEL");
    cancel.setOnAction(e -> { stopAll(); onExit.run(); });

    root.getChildren().addAll(title, msg, qrView, qrLabel, urlLabel,
        scroll, cancel);
    stage.setScene(new Scene(root, W, H));
  }

  private void showWaiting(String message) {
    VBox root = new VBox(24);
    root.setStyle("-fx-background-color:" + C_BG + ";");
    root.setAlignment(Pos.CENTER);
    root.setPadding(new Insets(80));

    Label title = label("NETWORK BATTLE", 13, C_ACCENT);
    title.setStyle(title.getStyle() + "-fx-letter-spacing:4px;");

    Label msg = label(message, 14, C_TEXT);
    msg.setWrapText(true);
    msg.setTextAlignment(TextAlignment.CENTER);

    // Spinner simulation via animated label
    Label spinner = label("◌", 32, C_ACCENT);
    javafx.animation.Timeline spin = new javafx.animation.Timeline(
        new javafx.animation.KeyFrame(javafx.util.Duration.millis(600),
            e -> spinner.setText(spinner.getText().equals("◌") ? "●" : "◌")));
    spin.setCycleCount(javafx.animation.Animation.INDEFINITE);
    spin.play();

    logBox = new VBox(4);
    logBox.setMaxWidth(600);
    logBox.setAlignment(Pos.CENTER_LEFT);

    ScrollPane scroll = new ScrollPane(logBox);
    scroll.setMaxWidth(600);
    scroll.setMaxHeight(200);
    scroll.setStyle("-fx-background-color:transparent; -fx-background:transparent;");

    Button cancel = ghostButton("CANCEL");
    cancel.setOnAction(e -> { stopAll(); onExit.run(); });

    root.getChildren().addAll(title, spinner, msg, scroll, cancel);
    stage.setScene(new Scene(root, W, H));
  }

  // ── Game screen ───────────────────────────────────────────────────────────

  private void showGameScreen() {
    // ── Left: camera + charge bar ─────────────────────────────────────
    camCanvas = new Canvas(CAM_W, CAM_H);
    StackPane camPane = new StackPane(camCanvas);
    camPane.setStyle("-fx-background-color:#000;");
    camPane.setMinSize(CAM_W, CAM_H);
    camPane.setMaxSize(CAM_W, CAM_H);

    Label camLabel = label("YOUR CAMERA", 10, C_MUTED);
    camLabel.setStyle(camLabel.getStyle() + "-fx-letter-spacing:2px;");
    camLabel.setPadding(new Insets(6, 0, 4, 0));

    Label chargeLbl = label("CHARGE", 10, C_MUTED);
    chargeLbl.setStyle(chargeLbl.getStyle() + "-fx-letter-spacing:2px;");

    chargeBar = new ProgressBar(0);
    chargeBar.setPrefWidth(CAM_W);
    chargeBar.setPrefHeight(12);
    chargeBar.setStyle("-fx-accent:" + C_ACCENT2 + ";"
        + "-fx-background-color:" + C_BORDER + ";"
        + "-fx-background-radius:2;");

    chargeLetter = label("", 28, C_ACCENT2);
    chargeLetter.setStyle(chargeLetter.getStyle() + "-fx-font-weight:700;");

    HBox chargeRow = new HBox(12, chargeLbl, chargeBar, chargeLetter);
    chargeRow.setAlignment(Pos.CENTER_LEFT);

    VBox leftCol = new VBox(0, camLabel, camPane, chargeRow);
    leftCol.setStyle("-fx-background-color:#000;");
    leftCol.setPadding(new Insets(12, 12, 12, 12));

    // ── Center: round info ────────────────────────────────────────────
    roundLabel = label("Round 0", 12, C_MUTED);
    roundLabel.setStyle(roundLabel.getStyle() + "-fx-letter-spacing:2px;");

    letterLabel = new Label("?");
    letterLabel.setStyle("-fx-font-size:100px; -fx-font-weight:900;"
        + "-fx-text-fill:" + C_TEXT + "; -fx-font-family:'Courier New';");

    statusLabel = label("Waiting for round...", 13, C_MUTED);

    Label signLbl = label("SIGN THIS LETTER", 11, C_MUTED);
    signLbl.setStyle(signLbl.getStyle() + "-fx-letter-spacing:3px;");

    VBox centerCol = new VBox(8);
    centerCol.setAlignment(Pos.CENTER);
    centerCol.setPadding(new Insets(40, 32, 40, 32));
    centerCol.setStyle("-fx-background-color:" + C_SURFACE + ";");
    VBox.setVgrow(centerCol, Priority.ALWAYS);
    centerCol.getChildren().addAll(roundLabel, letterLabel, signLbl, statusLabel);
    HBox.setHgrow(centerCol, Priority.ALWAYS);

    // ── Right: player list + log ──────────────────────────────────────
    playerList = new VBox(10);
    playerList.setPadding(new Insets(12));

    Label playersTitle = label("PLAYERS", 11, C_MUTED);
    playersTitle.setStyle(playersTitle.getStyle() + "-fx-letter-spacing:3px;");

    Separator sep = new Separator();
    sep.setStyle("-fx-background-color:" + C_BORDER + ";");

    logBox = new VBox(4);
    logBox.setPadding(new Insets(8, 0, 0, 0));
    Label logTitle = label("LOG", 10, C_MUTED);
    logTitle.setStyle(logTitle.getStyle() + "-fx-letter-spacing:2px;");

    ScrollPane logScroll = new ScrollPane(logBox);
    logScroll.setFitToWidth(true);
    logScroll.setPrefHeight(200);
    logScroll.setStyle("-fx-background-color:transparent; -fx-background:transparent;");
    VBox.setVgrow(logScroll, Priority.ALWAYS);

    Region spacer = new Region();
    VBox.setVgrow(spacer, Priority.ALWAYS);

    Button quit = ghostButton("QUIT");
    quit.setOnAction(e -> { stopAll(); onExit.run(); });

    VBox rightCol = new VBox(10);
    rightCol.setPadding(new Insets(16));
    rightCol.setPrefWidth(260);
    rightCol.setStyle("-fx-background-color:" + C_SURFACE + ";");
    rightCol.getChildren().addAll(
        playersTitle, playerList, sep, logTitle, logScroll, spacer, quit);

    // ── Root ─────────────────────────────────────────────────────────
    HBox root = new HBox(leftCol, centerCol, rightCol);
    root.setStyle("-fx-background-color:" + C_BG + ";");

    // Game over overlay (hidden until gameOver)
    StackPane overlay = new StackPane(root);
    stage.setScene(new Scene(overlay, W, H));

    startCamera();
    startUITimer();
    rebuildPlayerList();
  }

  // ── UI timer ──────────────────────────────────────────────────────────────

  private void startUITimer() {
    uiTimer = new AnimationTimer() {
      @Override public void handle(long now) {
        // Update camera frame
        Image img = latestFrame.get();
        if (img != null && camCanvas != null) {
          camCanvas.getGraphicsContext2D().drawImage(img, 0, 0, CAM_W, CAM_H);
        }
        // Update charge bar from client
        if (client != null) {
          if (chargeBar  != null) chargeBar.setProgress(client.chargeProgress);
          if (chargeLetter != null) {
            String rec = client.recognisedLetter;
            chargeLetter.setText(rec != null ? rec : "");
          }
        }
      }
    };
    uiTimer.start();
  }

  // ── Camera ────────────────────────────────────────────────────────────────

  private void startCamera() {
    camera = new VideoCapture(0);
    if (!camera.isOpened()) return;

    cameraThread = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "networked-cam");
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
        javafx.scene.image.PixelFormat.getByteRgbInstance(), data, 0, w * 3);
    return wi;
  }

  private void stopCamera() {
    if (cameraThread != null) { cameraThread.shutdownNow(); cameraThread = null; }
    if (camera       != null) { camera.release();           camera       = null; }
  }

  // ── UI update helpers ─────────────────────────────────────────────────────

  private void updateGameUI() {
    if (letterLabel  != null) letterLabel.setText(currentLetter != null ? currentLetter : "?");
    if (roundLabel   != null) roundLabel.setText("ROUND  " + currentRound);
    if (statusLabel  != null) {
      if (gameOver) {
        statusLabel.setText(winnerText);
        statusLabel.setStyle("-fx-font-size:13px; -fx-font-family:'Courier New';"
            + "-fx-text-fill:" + C_ACCENT + ";");
      } else {
        statusLabel.setText(activePlayers != null
            ? activePlayers.size() + " players active" : "");
      }
    }
    rebuildPlayerList();
  }

  private void rebuildPlayerList() {
    if (playerList == null) return;
    playerList.getChildren().clear();
    for (String p : allPlayers) {
      boolean active = activePlayers != null && activePlayers.contains(p);
      HBox row = new HBox(8);
      row.setAlignment(Pos.CENTER_LEFT);
      javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4,
          active ? Color.web(C_ACCENT) : Color.web(C_WARN));
      Label name = label(p, 13, active ? C_TEXT : C_MUTED);
      if (!active) {
        Label x = label("eliminated", 11, C_WARN);
        row.getChildren().addAll(dot, name, x);
      } else {
        row.getChildren().addAll(dot, name);
      }
      playerList.getChildren().add(row);
    }
  }

  private void appendLog(String msg) {
    if (logBox == null) return;
    Label l = label(msg, 11, C_MUTED);
    l.setWrapText(true);
    logBox.getChildren().add(0, l);   // newest at top
    if (logBox.getChildren().size() > 40)
      logBox.getChildren().remove(40, logBox.getChildren().size());
    log.add(msg);
  }

  // ── Cleanup ───────────────────────────────────────────────────────────────

  private void stopAll() {
    if (uiTimer != null) { uiTimer.stop(); uiTimer = null; }
    stopCamera();
    if (client != null) { client.disconnect(); client = null; }
    if (server != null) { server.stop();       server = null; }
  }

  // ── Style helpers ─────────────────────────────────────────────────────────

  private Label label(String text, double size, String color) {
    Label l = new Label(text);
    l.setStyle("-fx-font-size:" + size + "px; -fx-text-fill:" + color
        + "; -fx-font-family:'Courier New';");
    return l;
  }

  private Button accentButton(String text, String color) {
    Button b = new Button(text);
    b.setStyle("-fx-background-color:" + color + ";"
        + "-fx-text-fill:#000; -fx-font-family:'Courier New';"
        + "-fx-font-size:13px; -fx-font-weight:700;"
        + "-fx-padding:12 32 12 32; -fx-background-radius:3; -fx-cursor:hand;");
    b.setMaxWidth(Double.MAX_VALUE);
    return b;
  }

  private Button ghostButton(String text) {
    Button b = new Button(text);
    b.setStyle("-fx-background-color:transparent; -fx-text-fill:" + C_MUTED + ";"
        + "-fx-font-family:'Courier New'; -fx-font-size:12px;"
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

  private Region spacer(double h) {
    Region r = new Region();
    r.setPrefHeight(h);
    return r;
  }

  // ── JSON array parser ─────────────────────────────────────────────────────

  private List<String> parseArray(String json, String key) {
    List<String> result = new ArrayList<>();
    String search = "\"" + key + "\":[";
    int start = json.indexOf(search);
    if (start < 0) return result;
    start += search.length();
    int end = json.indexOf("]", start);
    if (end < 0) return result;
    String content = json.substring(start, end);
    for (String part : content.split(",")) {
      String s = part.trim().replace("\"", "");
      if (!s.isEmpty()) result.add(s);
    }
    return result;
  }
}