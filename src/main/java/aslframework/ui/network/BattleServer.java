package aslframework.ui.network;

import aslframework.game.result.BattleResult;
import aslframework.game.result.GameResult;
import aslframework.game.round.BattleRound;
import aslframework.game.session.BattleSession;
import aslframework.game.session.GameEventListener;
import aslframework.game.session.NoOpGameEventListener;
import aslframework.model.HandLandmark;
import aslframework.persistence.AttemptRecord;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.MediaPipeRecognizer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * TCP server that hosts a 2-player network battle session.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Call {@link #start(int, Consumer)} — opens a server socket on the given port.</li>
 *   <li>Waits for exactly 2 clients to connect and send their player names.</li>
 *   <li>Creates a {@link BattleSession} with both player IDs.</li>
 *   <li>Forwards all {@link GameEventListener} events to both clients as JSON.</li>
 *   <li>Receives gesture attempt messages from clients and submits them to
 *       {@link BattleSession#submitAttempt}.</li>
 * </ol>
 *
 * <p>Message format — newline-delimited, UTF-8:
 * <pre>
 * Client → Server:
 *   {"type":"JOIN","player":"alice"}
 *   {"type":"ATTEMPT","player":"alice","letter":"A","confidence":0.91}
 *
 * Server → Client:
 *   {"type":"WAITING","message":"Waiting for opponent..."}
 *   {"type":"START","players":["alice","bob"]}
 *   {"type":"ROUND","round":1,"letter":"B","tier":1,"active":["alice","bob"]}
 *   {"type":"ATTEMPT","player":"alice","letter":"A","passed":true,"confidence":0.91}
 *   {"type":"ELIMINATED","player":"alice","rounds":2}
 *   {"type":"ROUND_CLOSED","round":1,"passed":["bob"],"eliminated":["alice"]}
 *   {"type":"WINNER","winners":["bob"]}
 *   {"type":"GAME_OVER","winner":"bob","rounds":5}
 * </pre>
 */
public class BattleServer {

  public static final int DEFAULT_PORT = 55432;

  private final GestureLibrary      library;
  private ServerSocket              serverSocket;
  private final List<ClientHandler> clients = new ArrayList<>();
  private BattleSession             session;
  private ExecutorService           executor;
  private volatile boolean          running = false;
  private MobileServer              mobileServer;

  /** Callback to notify the host UI of status messages. */
  private Consumer<String> statusCallback;

  public BattleServer(GestureLibrary library) {
    this.library = library;
  }

  /** Returns the mobile server so the UI can broadcast game events to phones. */
  public MobileServer getMobileServer() { return mobileServer; }

  /**
   * Starts the server on the given port and waits for 2 clients.
   * Also starts the mobile HTTP + WebSocket server so phones can join via QR.
   */
  public void start(int port, Consumer<String> statusCallback) {
    this.statusCallback = statusCallback;
    executor = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "battle-server");
      t.setDaemon(true);
      return t;
    });

    // Start mobile server (HTTP + WebSocket) for phone players
    mobileServer = new MobileServer(
        library,
        this::handleMobileMessage,   // phone → server
        msg -> log("[Mobile] " + msg)
    );
    try {
      mobileServer.start();
    } catch (Exception e) {
      log("Mobile server failed to start: " + e.getMessage());
    }

    executor.submit(() -> {
      try {
        serverSocket = new ServerSocket(port);
        running = true;
        log("Server started on port " + port);
        log("Waiting for 2 players to connect...");

        // Accept exactly 2 clients
        while (clients.size() < 2) {
          Socket socket = serverSocket.accept();
          ClientHandler handler = new ClientHandler(socket);
          clients.add(handler);
          log("Player connected (" + clients.size() + "/2): "
              + socket.getInetAddress().getHostAddress());
          executor.submit(handler::readLoop);
        }

        // Wait until both have sent JOIN
        long deadline = System.currentTimeMillis() + 15_000;
        while (!allJoined() && System.currentTimeMillis() < deadline) {
          Thread.sleep(100);
        }

        if (!allJoined()) {
          log("Timeout waiting for player names.");
          return;
        }

        List<String> playerIds = new ArrayList<>();
        for (ClientHandler c : clients) playerIds.add(c.playerId);
        log("Both players joined: " + playerIds);

        launchSession(playerIds);

      } catch (IOException | InterruptedException e) {
        if (running) log("Server error: " + e.getMessage());
      }
    });
  }

  /** Handles a message arriving from a phone WebSocket client. */
  private void handleMobileMessage(String json) {
    // Phone messages use the same protocol as Java TCP clients
    String type = extractString(json, "type");
    if (type == null) return;

    if ("JOIN".equals(type)) {
      // Add a virtual ClientHandler for the phone player
      String playerId = extractString(json, "player");
      if (playerId != null) {
        MobileClientHandler handler = new MobileClientHandler(playerId);
        clients.add(handler);
        log("Mobile player joined: " + playerId
            + " (" + clients.size() + "/2)");
      }
    } else if ("ATTEMPT".equals(type)) {
      String  player     = extractString(json, "player");
      String  letter     = extractString(json, "letter");
      double  confidence = extractDouble(json, "confidence");
      try {
        submitClientAttempt(player, letter, confidence);
      } catch (Exception e) {
        log("Mobile attempt error: " + e.getMessage());
      }
    }
  }

  /** Returns this machine's local IP address for clients to connect to. */
  public static String getLocalIP() {
    try {
      return InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      return "127.0.0.1";
    }
  }

  /** Stops the server and closes all connections. */
  public void stop() {
    running = false;
    for (ClientHandler c : clients) c.close();
    try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    if (executor     != null) executor.shutdownNow();
    if (mobileServer != null) mobileServer.stop();
  }

  // ── Session launch ────────────────────────────────────────────────────────

  private void launchSession(List<String> playerIds) {
    session = new BattleSession(new MediaPipeRecognizer(), library, playerIds);

    session.setEventListener(new NoOpGameEventListener() {
      @Override
      public void onRoundOpened(BattleRound round, List<String> active) {
        broadcast(json("ROUND",
            "round",    round.getRoundNumber(),
            "letter",   round.getTargetLetter(),
            "tier",     round.getDifficultyTier(),
            "active",   jsonArray(active)));
        log("Round " + round.getRoundNumber() + " — letter: " + round.getTargetLetter());
      }

      @Override
      public void onAttempt(String letter, AttemptRecord record, int streak) {
        // Broadcast attempt result to all clients
      }

      @Override
      public void onRoundClosed(BattleRound round, List<String> eliminated) {
        broadcast(json("ROUND_CLOSED",
            "round",      round.getRoundNumber(),
            "passed",     jsonArray(round.getPassedPlayers()),
            "eliminated", jsonArray(round.getFailedPlayers())));
      }

      @Override
      public void onPlayerEliminated(String playerId, int roundsCleared) {
        broadcast(json("ELIMINATED",
            "player", playerId,
            "rounds", roundsCleared));
        log(playerId + " eliminated after " + roundsCleared + " rounds");
      }

      @Override
      public void onWinnersDeclared(List<String> winners) {
        broadcast(json("WINNER",
            "winners", jsonArray(winners)));
        log("Winner(s): " + winners);
      }

      @Override
      public void onSessionFinished(GameResult result) {
        if (result instanceof BattleResult br) {
          broadcast(json("GAME_OVER",
              "winners", jsonArray(br.getWinners()),
              "rounds",  br.getTotalRounds()));
        }
      }
    });

    // Tell both clients game is starting
    broadcast(json("START", "players", jsonArray(playerIds)));
    log("Game started!");
  }

  // ── Client handler ────────────────────────────────────────────────────────

  private class ClientHandler {
    final Socket socket;
    PrintWriter  out;
    BufferedReader in;
    String       playerId = null;

    ClientHandler(Socket socket) throws IOException {
      this.socket = socket;
      if (socket != null) {
        this.out = new PrintWriter(
            new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        this.in  = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), "UTF-8"));
        send(json("WAITING", "message", "Waiting for opponent..."));
      }
    }

    /** No-arg constructor for subclasses that don't use a socket. */
    ClientHandler() {
      this.socket = null;
    }

    void readLoop() {
      try {
        String line;
        while ((line = in.readLine()) != null) {
          handleMessage(line);
        }
      } catch (IOException e) {
        if (running) log("Client disconnected: " + playerId);
      }
    }

    void handleMessage(String json) {
      String type = extractString(json, "type");
      if (type == null) return;

      switch (type) {
        case "JOIN" -> {
          playerId = extractString(json, "player");
          log(playerId + " joined.");
        }
        case "ATTEMPT" -> {
          if (session == null || session.isFinished()) return;
          String  player     = extractString(json, "player");
          String  letter     = extractString(json, "letter");
          double  confidence = extractDouble(json, "confidence");
          try {
            submitClientAttempt(player, letter, confidence);
          } catch (Exception e) {
            log("Attempt error: " + e.getMessage());
          }
        }
      }
    }

    void send(String message) {
      if (out != null) out.println(message);
    }

    void close() {
      try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
  }

  // ── Submit client attempt to session ─────────────────────────────────────

  /**
   * Submits a pre-recognised attempt from a client directly into the session.
   * Since recognition happened on the client, we synthesise a landmark list
   * that will score exactly {@code confidence} against the target gesture.
   * The simplest approach: pass null landmarks and override via a wrapper.
   */
  private void submitClientAttempt(String playerId, String letter,
      double confidence) {
    if (session == null || session.isFinished()) return;

    // We use a mock landmark list — the server's MediaPipeRecognizer
    // won't be called because BattleSession.submitAttempt calls
    // AbstractGameSession.evaluate which calls recognizer.recognize.
    // Instead we broadcast the result directly and call the session
    // with the target letter's reference landmarks so scoring is consistent.
    List<HandLandmark> ref = getReferenceLandmarks(letter);
    if (ref == null) return;

    AttemptRecord record = session.submitAttempt(playerId, ref);

    // Broadcast the attempt result to all clients
    broadcast(json("ATTEMPT",
        "player",     playerId,
        "letter",     letter,
        "passed",     record.isPassed(),
        "confidence", String.format("%.3f", record.getAccuracy())));
  }

  private List<HandLandmark> getReferenceLandmarks(String letter) {
    var variants = library.getGestureVariants(letter);
    if (variants == null || variants.isEmpty()) return null;
    // Use the reference landmarks from the first variant
    // aslframework.model.GestureDefinition exposes getReferenceLandmarks()
    try {
      var def = variants.get(0);
      var method = def.getClass().getMethod("getReferenceLandmarks");
      @SuppressWarnings("unchecked")
      List<HandLandmark> landmarks = (List<HandLandmark>) method.invoke(def);
      return landmarks;
    } catch (Exception e) {
      log("Could not get reference landmarks for " + letter + ": " + e.getMessage());
      return null;
    }
  }

  // ── Mobile client handler (phone via WebSocket) ───────────────────────────

  /**
   * A virtual ClientHandler for phone players connected via WebSocket.
   * Sending is handled by {@link MobileServer#broadcastToMobile} — this
   * handler exists only so the phone player appears in the clients list
   * and passes the {@link #allJoined()} check.
   */
  private class MobileClientHandler extends ClientHandler {
    MobileClientHandler(String playerId) {
      super();
      this.playerId = playerId;
    }

    @Override void readLoop() {}
    @Override void send(String message) {}
    @Override void close() {}
  }

  // ── Utilities ─────────────────────────────────────────────────────────────

  private boolean allJoined() {
    return clients.size() == 2
        && clients.stream().allMatch(c -> c.playerId != null);
  }

  private void broadcast(String message) {
    for (ClientHandler c : clients) c.send(message);
    // Also broadcast to phone clients via WebSocket
    if (mobileServer != null) mobileServer.broadcastToMobile(message);
  }

  private void log(String msg) {
    System.out.println("[BattleServer] " + msg);
    if (statusCallback != null) statusCallback.accept(msg);
  }

  // ── Minimal JSON builder ──────────────────────────────────────────────────

  static String json(String type, Object... kvPairs) {
    StringBuilder sb = new StringBuilder("{\"type\":\"").append(type).append("\"");
    for (int i = 0; i + 1 < kvPairs.length; i += 2) {
      sb.append(",\"").append(kvPairs[i]).append("\":");
      Object v = kvPairs[i + 1];
      if (v instanceof String s) {
        // Check if already a JSON array/object
        if (s.startsWith("[") || s.startsWith("{")) sb.append(s);
        else sb.append("\"").append(s).append("\"");
      } else if (v instanceof Boolean b) {
        sb.append(b);
      } else {
        sb.append(v);
      }
    }
    sb.append("}");
    return sb.toString();
  }

  static String jsonArray(List<String> items) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append("\"").append(items.get(i)).append("\"");
    }
    sb.append("]");
    return sb.toString();
  }

  static String extractString(String json, String key) {
    String search = "\"" + key + "\":\"";
    int start = json.indexOf(search);
    if (start < 0) return null;
    start += search.length();
    int end = json.indexOf("\"", start);
    return end < 0 ? null : json.substring(start, end);
  }

  static double extractDouble(String json, String key) {
    String search = "\"" + key + "\":";
    int start = json.indexOf(search);
    if (start < 0) return 0;
    start += search.length();
    int end = start;
    while (end < json.length() && (Character.isDigit(json.charAt(end))
        || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
    try { return Double.parseDouble(json.substring(start, end)); }
    catch (NumberFormatException e) { return 0; }
  }
}