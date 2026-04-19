package aslframework.ui.network;

import aslframework.recognition.GestureLibrary;
import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;

import com.sun.net.httpserver.HttpServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Serves the mobile battle page over HTTP and accepts WebSocket connections
 * from phones.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Starts an HTTP server on {@link #HTTP_PORT} serving {@code mobile_game.html}
 *       with the reference landmark data and WebSocket URL injected.</li>
 *   <li>Starts a WebSocket server on {@link #WS_PORT} accepting phone clients.</li>
 *   <li>Phone clients behave identically to Java TCP clients from the
 *       {@link BattleServer}'s perspective — they send JOIN and ATTEMPT messages.</li>
 * </ol>
 *
 * <p>The host scans the QR code at {@code http://<localIP>:<HTTP_PORT>} to
 * open the page on their phone.
 */
public class MobileServer {

  public static final int HTTP_PORT = 8080;
  public static final int WS_PORT   = 8081;

  private final GestureLibrary        library;
  private final Consumer<String>      onMessage;   // forward to BattleServer
  private final Consumer<String>      onStatus;

  private HttpServer                  httpServer;
  private MobileWebSocketServer       wsServer;
  private final List<WebSocket>       mobileClients = new ArrayList<>();

  public MobileServer(GestureLibrary library,
      Consumer<String> onMessage,
      Consumer<String> onStatus) {
    this.library   = library;
    this.onMessage = onMessage;
    this.onStatus  = onStatus;
  }

  // ── Start ─────────────────────────────────────────────────────────────────

  public void start() throws Exception {
    startHttpServer();
    startWebSocketServer();
    onStatus.accept("Mobile server ready at http://"
        + BattleServer.getLocalIP() + ":" + HTTP_PORT);
  }

  // ── HTTP server ───────────────────────────────────────────────────────────

  private void startHttpServer() throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);

    httpServer.createContext("/", exchange -> {
      byte[] page = buildMobilePage();
      exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
      exchange.sendResponseHeaders(200, page.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(page);
      }
    });

    httpServer.setExecutor(Executors.newCachedThreadPool());
    httpServer.start();
    onStatus.accept("HTTP server started on port " + HTTP_PORT);
  }

  /**
   * Loads {@code mobile_game.html} and injects:
   * <ul>
   *   <li>The WebSocket host/port constants</li>
   *   <li>The reference landmark data as a JS object (REFERENCE_DATA)</li>
   * </ul>
   */
  private byte[] buildMobilePage() throws IOException {
    // Load the HTML template
    String html = loadHtmlTemplate();

    // Build REFERENCE_DATA JS object from gesture library
    String refData = buildReferenceDataJS();

    // Inject constants just before </script>
    String injection = "\nconst WS_HOST = \"" + BattleServer.getLocalIP() + "\";\n"
        + "const WS_PORT = " + WS_PORT + ";\n"
        + refData + "\n";

    // Insert after <script type="module">
    html = html.replace("<script type=\"module\">",
        "<script type=\"module\">\n" + injection);

    return html.getBytes(StandardCharsets.UTF_8);
  }

  private String loadHtmlTemplate() throws IOException {
    // Try project resources first, then working directory
    File file = new File("src/main/resources/mobile_game.html");
    if (file.exists()) return Files.readString(file.toPath());

    file = new File("mobile_game.html");
    if (file.exists()) return Files.readString(file.toPath());

    // Fall back to classpath
    try (InputStream is = getClass().getResourceAsStream("/mobile_game.html")) {
      if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    throw new IOException("mobile_game.html not found. Place it in "
        + "src/main/resources/ or the project root.");
  }

  private String buildReferenceDataJS() {
    if (library == null) return "const REFERENCE_DATA = {};";

    StringBuilder sb = new StringBuilder("const REFERENCE_DATA = {\n");
    boolean first = true;

    for (char c = 'A'; c <= 'Z'; c++) {
      String letter   = String.valueOf(c);
      var    variants = library.getGestureVariants(letter);
      if (variants == null || variants.isEmpty()) continue;

      // Use first variant's landmarks
      List<HandLandmark> landmarks = getLandmarks(variants.get(0));
      if (landmarks == null || landmarks.isEmpty()) continue;

      if (!first) sb.append(",\n");
      first = false;

      sb.append("  \"").append(letter).append("\": [");
      for (int i = 0; i < landmarks.size(); i++) {
        HandLandmark lm = landmarks.get(i);
        if (i > 0) sb.append(",");
        sb.append("[")
            .append(lm.getX()).append(",")
            .append(lm.getY()).append(",")
            .append(lm.getZ()).append("]");
      }
      sb.append("]");
    }

    sb.append("\n};");
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private List<HandLandmark> getLandmarks(GestureDefinition def) {
    try {
      var method = def.getClass().getMethod("getReferenceLandmarks");
      return (List<HandLandmark>) method.invoke(def);
    } catch (Exception e) {
      return null;
    }
  }

  // ── WebSocket server ──────────────────────────────────────────────────────

  private void startWebSocketServer() throws InterruptedException {
    wsServer = new MobileWebSocketServer(new InetSocketAddress(WS_PORT));
    wsServer.start();
    onStatus.accept("WebSocket server started on port " + WS_PORT);
  }

  /** Sends a message to all connected mobile clients. */
  public void broadcastToMobile(String message) {
    synchronized (mobileClients) {
      for (WebSocket ws : mobileClients) {
        if (ws.isOpen()) ws.send(message);
      }
    }
  }

  // ── Stop ──────────────────────────────────────────────────────────────────

  public void stop() {
    if (httpServer != null) httpServer.stop(0);
    try { if (wsServer != null) wsServer.stop(1000); }
    catch (InterruptedException ignored) {}
  }

  // ── WebSocket server implementation ───────────────────────────────────────

  private class MobileWebSocketServer extends WebSocketServer {

    MobileWebSocketServer(InetSocketAddress address) {
      super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
      synchronized (mobileClients) { mobileClients.add(conn); }
      onStatus.accept("Phone connected: " + conn.getRemoteSocketAddress());
      // Send waiting message
      conn.send(BattleServer.json("WAITING",
          "message", "Connected! Waiting for game to start..."));
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
      // Forward to BattleServer message handler
      if (onMessage != null) onMessage.accept(message);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
      synchronized (mobileClients) { mobileClients.remove(conn); }
      onStatus.accept("Phone disconnected.");
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
      onStatus.accept("WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
      onStatus.accept("WebSocket server accepting connections.");
    }
  }
}