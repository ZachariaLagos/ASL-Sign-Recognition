package aslframework.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * Animated AI guide figure for the instruction panel.
 *
 * <p>Person C owns this class. Displays a simple drawn character that
 * reacts to game events with four states:
 * <ul>
 *   <li>{@link State#IDLE}        — gently bobs, waiting for the player</li>
 *   <li>{@link State#TALKING}     — introduces the next letter</li>
 *   <li>{@link State#HAPPY}       — celebrates a correct match</li>
 *   <li>{@link State#ENCOURAGING} — gently prompts to try again</li>
 * </ul>
 *
 * <p>Call {@link #setState(State, String)} from {@link PracticeScreen}
 * or {@link BattleScreen} whenever the game state changes.
 *
 * <p>Usage:
 * <pre>{@code
 * InstructionPanel panel = new InstructionPanel();
 * instrCol.getChildren().add(panel.getPane(UIConstants.GREEN));
 * panel.setState(InstructionPanel.State.TALKING, "Show me the letter A!");
 * }</pre>
 */
public class InstructionPanel {

  // ── State ─────────────────────────────────────────────────────────────────

  public enum State { IDLE, TALKING, HAPPY, ENCOURAGING }

  // ── Dimensions ────────────────────────────────────────────────────────────
  private static final double W = UIConstants.CAM_W;
  private static final double H = UIConstants.CAM_H;

  // figure centre x, baseline y
  private static final double FIG_CX = W / 2;
  private static final double FIG_Y  = H * 0.52;

  // ── Colours ───────────────────────────────────────────────────────────────
  private static final Color COL_SKIN   = Color.web("#f5c9a0");
  private static final Color COL_BODY   = Color.web(UIConstants.ACCENT);
  private static final Color COL_EYE    = Color.web("#1a1a2e");
  private static final Color COL_MOUTH  = Color.web("#c0392b");
  private static final Color COL_BUBBLE = Color.web("#16213e");
  private static final Color COL_BORDER = Color.web(UIConstants.GREEN);

  // ── Fields ────────────────────────────────────────────────────────────────
  private final Canvas  canvas;
  private final Label   speechLabel;
  private final VBox    container;
  private final StackPane pane;

  private State   state      = State.IDLE;
  private String  speech     = "Hi! I'm your ASL guide.\nLet's practice together!";
  private double  tick       = 0;          // animation counter
  private Timeline animLoop;

  // happy/encouraging flash timer
  private int     stateFrames = 0;

  /**
   * Constructs the instruction panel and starts the idle animation loop.
   */
  public InstructionPanel() {
    canvas = new Canvas(W, H * 0.72);

    speechLabel = new Label(speech);
    speechLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
    speechLabel.setTextFill(Color.web(UIConstants.TEXT_WHITE));
    speechLabel.setWrapText(true);
    speechLabel.setTextAlignment(TextAlignment.CENTER);
    speechLabel.setMaxWidth(W - 40);
    speechLabel.setAlignment(Pos.CENTER);

    // speech bubble container
    VBox bubble = new VBox(speechLabel);
    bubble.setAlignment(Pos.CENTER);
    bubble.setPadding(new Insets(10, 16, 10, 16));
    bubble.setMaxWidth(W - 20);
    bubble.setStyle(
        "-fx-background-color:" + UIConstants.BG_CARD + ";"
            + "-fx-background-radius:12;"
            + "-fx-border-color:" + UIConstants.GREEN + ";"
            + "-fx-border-width:1.5;"
            + "-fx-border-radius:12;");

    container = new VBox(0, canvas, bubble);
    container.setAlignment(Pos.TOP_CENTER);
    container.setPadding(new Insets(10, 10, 10, 10));

    pane = new StackPane(container);
    pane.setPrefSize(W, H);
    pane.setMinSize(W, H);
    pane.setMaxSize(W, H);
    pane.setStyle(
        "-fx-background-color:#0d0d1a;"
            + "-fx-background-radius:16;"
            + "-fx-border-color:" + UIConstants.GREEN + ";"
            + "-fx-border-width:2;"
            + "-fx-border-radius:16;");

    startAnimLoop();
  }

  /**
   * Returns the panel pane with the given accent border colour.
   *
   * @param accentColor hex border colour
   * @return styled {@link StackPane}
   */
  public StackPane getPane(String accentColor) {
    pane.setStyle(
        "-fx-background-color:#0d0d1a;"
            + "-fx-background-radius:16;"
            + "-fx-border-color:" + accentColor + ";"
            + "-fx-border-width:2;"
            + "-fx-border-radius:16;");
    bubble().setStyle(
        "-fx-background-color:" + UIConstants.BG_CARD + ";"
            + "-fx-background-radius:12;"
            + "-fx-border-color:" + accentColor + ";"
            + "-fx-border-width:1.5;"
            + "-fx-border-radius:12;");
    return pane;
  }

  /**
   * Updates the figure state and speech bubble text.
   *
   * @param newState the new animation state
   * @param text     the speech bubble message
   */
  public void setState(State newState, String text) {
    this.state       = newState;
    this.speech      = text;
    this.stateFrames = 0;
    speechLabel.setText(text);
  }

  /** Stops the animation loop. Call when navigating away. */
  public void stop() {
    if (animLoop != null) animLoop.stop();
  }

  /** Restarts the animation loop. Call when returning to the screen. */
  public void resume() {
    startAnimLoop();
  }

  // ── Animation loop ────────────────────────────────────────────────────────

  private void startAnimLoop() {
    if (animLoop != null) animLoop.stop();
    animLoop = new Timeline(new KeyFrame(Duration.millis(40), e -> {
      tick += 0.06;
      stateFrames++;
      // auto-return to IDLE after HAPPY/ENCOURAGING plays for ~2.5 s
      if ((state == State.HAPPY || state == State.ENCOURAGING) && stateFrames > 62) {
        state = State.IDLE;
      }
      draw();
    }));
    animLoop.setCycleCount(Timeline.INDEFINITE);
    animLoop.play();
  }

  // ── Drawing ───────────────────────────────────────────────────────────────

  private void draw() {
    GraphicsContext gc = canvas.getGraphicsContext2D();
    gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

    double bobY  = Math.sin(tick) * 4;
    double cx    = FIG_CX;
    double baseY = FIG_Y + bobY;

    if (state == State.HAPPY) {
      drawHappy(gc, cx, baseY);
    } else if (state == State.ENCOURAGING) {
      drawEncouraging(gc, cx, baseY);
    } else if (state == State.TALKING) {
      drawTalking(gc, cx, baseY);
    } else {
      drawIdle(gc, cx, baseY);
    }
  }

  // ── IDLE ──────────────────────────────────────────────────────────────────
  private void drawIdle(GraphicsContext gc, double cx, double baseY) {
    drawBody(gc, cx, baseY, COL_BODY);
    drawHead(gc, cx, baseY);
    drawEyes(gc, cx, baseY, false);
    drawMouth(gc, cx, baseY, MouthShape.SMILE);
    drawArms(gc, cx, baseY, 0);
  }

  // ── TALKING ───────────────────────────────────────────────────────────────
  private void drawTalking(GraphicsContext gc, double cx, double baseY) {
    drawBody(gc, cx, baseY, COL_BODY);
    drawHead(gc, cx, baseY);
    drawEyes(gc, cx, baseY, false);
    // animate mouth open/close
    MouthShape mouth = Math.sin(tick * 4) > 0 ? MouthShape.OPEN : MouthShape.SMILE;
    drawMouth(gc, cx, baseY, mouth);
    drawArms(gc, cx, baseY, Math.sin(tick * 2) * 8); // one arm waves
  }

  // ── HAPPY ─────────────────────────────────────────────────────────────────
  private void drawHappy(GraphicsContext gc, double cx, double baseY) {
    double jumpY = -Math.abs(Math.sin(tick * 3)) * 14; // jump
    double by = baseY + jumpY;
    drawBody(gc, cx, by, Color.web(UIConstants.GREEN));
    drawHead(gc, cx, by);
    drawEyes(gc, cx, by, true);  // happy squint
    drawMouth(gc, cx, by, MouthShape.BIG_SMILE);
    drawArms(gc, cx, by, -30);   // arms raised
    drawStars(gc, cx, by);
  }

  // ── ENCOURAGING ───────────────────────────────────────────────────────────
  private void drawEncouraging(GraphicsContext gc, double cx, double baseY) {
    drawBody(gc, cx, baseY, Color.web(UIConstants.ORANGE));
    drawHead(gc, cx, baseY);
    drawEyes(gc, cx, baseY, false);
    drawMouth(gc, cx, baseY, MouthShape.SMILE);
    drawArms(gc, cx, baseY, Math.sin(tick * 3) * 15); // encouraging wave
  }

  // ── Body parts ────────────────────────────────────────────────────────────

  private void drawBody(GraphicsContext gc, double cx, double baseY, Color color) {
    // body (rounded rectangle)
    gc.setFill(color);
    gc.fillRoundRect(cx - 28, baseY - 10, 56, 70, 18, 18);
    // legs
    gc.setFill(color.darker());
    gc.fillRoundRect(cx - 22, baseY + 58, 16, 36, 8, 8);
    gc.fillRoundRect(cx + 6,  baseY + 58, 16, 36, 8, 8);
    // feet
    gc.setFill(Color.web("#333355"));
    gc.fillOval(cx - 26, baseY + 90, 22, 10);
    gc.fillOval(cx + 4,  baseY + 90, 22, 10);
  }

  private void drawHead(GraphicsContext gc, double cx, double baseY) {
    // neck
    gc.setFill(COL_SKIN);
    gc.fillRoundRect(cx - 10, baseY - 22, 20, 16, 6, 6);
    // head
    gc.setFill(COL_SKIN);
    gc.fillOval(cx - 34, baseY - 82, 68, 68);
    // hair
    gc.setFill(Color.web("#3d2b1f"));
    gc.fillOval(cx - 34, baseY - 82, 68, 30);
    gc.fillOval(cx - 36, baseY - 74, 20, 28);
    gc.fillOval(cx + 16, baseY - 74, 20, 28);
  }

  private void drawEyes(GraphicsContext gc, double cx, double baseY, boolean happy) {
    if (happy) {
      // squinting happy eyes (arcs)
      gc.setStroke(COL_EYE);
      gc.setLineWidth(2.5);
      gc.strokeArc(cx - 22, baseY - 56, 14, 10, 0, 180, javafx.scene.shape.ArcType.OPEN);
      gc.strokeArc(cx + 8,  baseY - 56, 14, 10, 0, 180, javafx.scene.shape.ArcType.OPEN);
    } else {
      // normal eyes
      gc.setFill(Color.WHITE);
      gc.fillOval(cx - 22, baseY - 60, 14, 14);
      gc.fillOval(cx + 8,  baseY - 60, 14, 14);
      gc.setFill(COL_EYE);
      gc.fillOval(cx - 18, baseY - 57, 8, 8);
      gc.fillOval(cx + 11, baseY - 57, 8, 8);
      // pupils glint
      gc.setFill(Color.WHITE);
      gc.fillOval(cx - 15, baseY - 55, 3, 3);
      gc.fillOval(cx + 14, baseY - 55, 3, 3);
    }
  }

  private enum MouthShape { SMILE, BIG_SMILE, OPEN }

  private void drawMouth(GraphicsContext gc, double cx, double baseY, MouthShape shape) {
    gc.setStroke(COL_MOUTH);
    gc.setLineWidth(2.5);
    if (shape == MouthShape.SMILE) {
      gc.strokeArc(cx - 12, baseY - 44, 24, 14, 0, -180, javafx.scene.shape.ArcType.OPEN);
    } else if (shape == MouthShape.BIG_SMILE) {
      gc.setLineWidth(3);
      gc.strokeArc(cx - 16, baseY - 46, 32, 18, 0, -180, javafx.scene.shape.ArcType.OPEN);
    } else if (shape == MouthShape.OPEN) {
      gc.setFill(COL_MOUTH);
      gc.fillOval(cx - 8, baseY - 42, 16, 12);
    }
  }

  private void drawArms(GraphicsContext gc, double cx, double baseY, double angleOffset) {
    gc.setStroke(COL_SKIN);
    gc.setLineWidth(9);
    gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);

    // left arm
    double lAngle = Math.toRadians(-140 + angleOffset);
    gc.strokeLine(cx - 26, baseY + 20,
        cx - 26 + Math.cos(lAngle) * 38,
        baseY + 20 + Math.sin(lAngle) * 38);

    // right arm
    double rAngle = Math.toRadians(-40 - angleOffset);
    gc.strokeLine(cx + 26, baseY + 20,
        cx + 26 + Math.cos(rAngle) * 38,
        baseY + 20 + Math.sin(rAngle) * 38);
  }

  private void drawStars(GraphicsContext gc, double cx, double baseY) {
    gc.setFill(Color.web("#f9ca24"));
    double[] sx = {cx - 55, cx + 50, cx - 60, cx + 55, cx};
    double[] sy = {baseY - 90, baseY - 85, baseY - 50, baseY - 45, baseY - 110};
    double flicker = Math.abs(Math.sin(tick * 5));
    for (int i = 0; i < sx.length; i++) {
      double r = 4 + (i % 2) * 3;
      gc.setGlobalAlpha(0.4 + flicker * 0.6);
      gc.fillOval(sx[i] - r, sy[i] - r, r * 2, r * 2);
    }
    gc.setGlobalAlpha(1.0);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /** Returns the speech bubble VBox (second child of container). */
  private VBox bubble() {
    return (VBox) container.getChildren().get(1);
  }
}