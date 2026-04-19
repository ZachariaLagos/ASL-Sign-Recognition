package aslframework.ui.network;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.LandmarkBridge;
import aslframework.recognition.MediaPipeRecognizer;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * TCP client for a networked battle session.
 *
 * <p>Each player's machine runs one {@code BattleClient}. It:
 * <ol>
 *   <li>Connects to the {@link BattleServer} by IP and port.</li>
 *   <li>Sends a JOIN message with the player's name.</li>
 *   <li>Starts local gesture recognition (camera → MediaPipe → letter).</li>
 *   <li>When a gesture is held for {@link #HOLD_MS} ms, sends an ATTEMPT message.</li>
 *   <li>Forwards all server messages to the registered {@link #onMessage} callback
 *       so {@link NetworkedBattleUI} can update the display.</li>
 * </ol>
 *
 * <p>The host machine also runs a {@code BattleClient} connecting to localhost.
 */
public class BattleClient {

  public static final long HOLD_MS     = 1000;
  public static final long COOLDOWN_MS = 3000;
  public static final double THRESHOLD = 0.82;

  private final String         playerName;
  private final GestureLibrary library;

  private Socket         socket;
  private PrintWriter    out;
  private BufferedReader in;

  private ExecutorService readThread;
  private ExecutorService gestureThread;
  private LandmarkBridge  bridge;

  private volatile boolean connected = false;
  private volatile boolean gameStarted = false;

  // Gesture hold state
  private String heldLetter    = null;
  private long   holdStartMs   = 0;
  private long   lastShotMs    = 0;
  /** 0.0–1.0 charge progress — read by UI for the charge bar. */
  public  volatile double chargeProgress = 0;
  /** Currently recognised letter — read by UI for display. */
  public  volatile String recognisedLetter = null;

  /** Called on the calling thread whenever a message arrives from the server. */
  private Consumer<String> onMessage;
  /** Called with status text (connecting, errors, etc.) */
  private Consumer<String> onStatus;

  public BattleClient(String playerName, GestureLibrary library) {
    this.playerName = playerName;
    this.library    = library;
  }

  // ── Connect ───────────────────────────────────────────────────────────────

  /**
   * Connects to the server and starts the read loop.
   *
   * @param host      server IP address
   * @param port      server port
   * @param onMessage callback fired for every JSON message from the server
   * @param onStatus  callback for connection status text
   */
  public void connect(String host, int port,
      Consumer<String> onMessage,
      Consumer<String> onStatus) {
    this.onMessage = onMessage;
    this.onStatus  = onStatus;

    readThread = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "battle-client-read");
      t.setDaemon(true);
      return t;
    });

    readThread.submit(() -> {
      try {
        status("Connecting to " + host + ":" + port + "...");
        socket = new Socket(host, port);
        out    = new PrintWriter(
            new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        in     = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), "UTF-8"));
        connected = true;
        status("Connected. Sending name: " + playerName);

        // Send JOIN
        send(BattleServer.json("JOIN", "player", playerName));

        // Read loop
        String line;
        while ((line = in.readLine()) != null) {
          final String msg = line;
          if (onMessage != null) onMessage.accept(msg);

          // Start gesture recognition when game begins
          String type = BattleServer.extractString(msg, "type");
          if ("START".equals(type) || "ROUND".equals(type)) {
            if (!gameStarted) {
              gameStarted = true;
              startGestureRecognition();
            }
          }
        }
        status("Disconnected from server.");
      } catch (IOException e) {
        status("Connection error: " + e.getMessage());
      }
    });
  }

  // ── Gesture recognition ───────────────────────────────────────────────────

  private void startGestureRecognition() {
    if (library == null) return;
    try {
      bridge = new LandmarkBridge();
    } catch (LandmarkBridge.LandmarkBridgeException e) {
      status("Camera bridge failed: " + e.getMessage());
      return;
    }

    MediaPipeRecognizer recognizer = new MediaPipeRecognizer();

    gestureThread = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "battle-client-gesture");
      t.setDaemon(true);
      return t;
    });

    gestureThread.submit(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          List<HandLandmark> landmarks = bridge.nextLandmarks();
          if (landmarks == null || landmarks.isEmpty()) {
            recognisedLetter = null;
            chargeProgress   = 0;
            heldLetter       = null;
            continue;
          }

          // Score all letters
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

          if (bestLetter != null && bestScore >= THRESHOLD) {
            recognisedLetter = bestLetter;
            processHold(bestLetter, bestScore);
          } else {
            recognisedLetter = null;
            heldLetter       = null;
            chargeProgress   = 0;
          }

        } catch (LandmarkBridge.LandmarkBridgeException e) {
          break;
        }
      }
    });
  }

  private void processHold(String letter, double confidence) {
    long nowMs = System.currentTimeMillis();

    if (letter.equals(heldLetter)) {
      long held = nowMs - holdStartMs;
      chargeProgress = Math.min(1.0, (double) held / HOLD_MS);

      if (held >= HOLD_MS && nowMs - lastShotMs >= COOLDOWN_MS) {
        // Fire!
        lastShotMs     = nowMs;
        heldLetter     = null;
        chargeProgress = 0;
        sendAttempt(letter, confidence);
      }
    } else {
      heldLetter     = letter;
      holdStartMs    = nowMs;
      chargeProgress = 0;
    }
  }

  private void sendAttempt(String letter, double confidence) {
    if (!connected || !gameStarted) return;
    send(BattleServer.json("ATTEMPT",
        "player",     playerName,
        "letter",     letter,
        "confidence", String.format("%.3f", confidence)));
  }

  // ── Utilities ─────────────────────────────────────────────────────────────

  private void send(String message) {
    if (out != null) out.println(message);
  }

  private void status(String msg) {
    System.out.println("[BattleClient:" + playerName + "] " + msg);
    if (onStatus != null) onStatus.accept(msg);
  }

  public String getPlayerName() { return playerName; }
  public boolean isConnected()  { return connected; }

  public void disconnect() {
    connected   = false;
    gameStarted = false;
    if (gestureThread != null) { gestureThread.shutdownNow(); gestureThread = null; }
    if (bridge        != null) { bridge.close();              bridge        = null; }
    if (readThread    != null) { readThread.shutdownNow();    readThread    = null; }
    try { if (socket  != null) socket.close(); } catch (IOException ignored) {}
  }
}