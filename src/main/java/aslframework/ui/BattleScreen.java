package aslframework.ui;

import aslframework.game.LetterDifficulty;
import aslframework.recognition.GestureLibrary;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Battle mode game screen — 2 players, 5 rounds, 5 seconds per turn.
 *
 * <p>Each round presents the same letter to both players in turn.
 * A player scores points by holding the correct letter for 1 second
 * within their 5-second window. After 5 rounds the player with the
 * higher total score wins.
 *
 * <p>Round structure:
 * <ol>
 *   <li>Player 1 attempts the letter (5s timer)</li>
 *   <li>Player 2 attempts the same letter (5s timer)</li>
 *   <li>Advance to next letter, repeat for 5 rounds total</li>
 * </ol>
 */
public class BattleScreen {

  private final Stage              stage;
  private final GameUI             router;
  private final GestureLibrary     library;
  private final RecognitionService recognitionService;
  private final CameraPanel        cameraPanel;
  private final InstructionPanel   instructionPanel;

  // ── Battle config ─────────────────────────────────────────────────────────
  public  static final int TOTAL_ROUNDS   = 5;
  private static final int PLAYERS        = 2;
  private static final int TURN_TICKS     = 50;  // 5s per turn (50 × 100ms)
  private static final int SUCCESS_HOLD   = 20;  // 2s celebration between turns
  private static final int HOLD_NEEDED    = 10;  // 1s hold to confirm

  // ── Session state ─────────────────────────────────────────────────────────
  private List<String> letters;
  private int          round           = 0;   // 0-based, max TOTAL_ROUNDS
  private int          currentPlayer   = 0;   // 0 = P1, 1 = P2
  private int[]        scores          = new int[PLAYERS];
  private boolean[]    scoredThisRound = new boolean[PLAYERS];

  // ── Timing ────────────────────────────────────────────────────────────────
  private int          turnTicks       = 0;   // ticks elapsed in current turn
  private int          holdTicks       = 0;
  private int          successTicks    = -1;

  // ── Live recognition data ─────────────────────────────────────────────────
  private String  liveLetter     = "–";
  private double  liveConfidence = 0.0;

  // ── UI nodes ──────────────────────────────────────────────────────────────
  private Label       targetLetterLabel;
  private Label       tierLabel;
  private Label       currentPlayerLabel;
  private Label       roundLabel;
  private Label       timerLabel;
  private Label       detectedLabel;
  private ProgressBar confBar;
  private ProgressBar timerBar;
  private Label       confLabel;
  private Label       feedbackLabel;
  private Label       p1ScoreLabel;
  private Label       p2ScoreLabel;

  private Timeline    gameLoop;

  public BattleScreen(Stage stage, GameUI router, GestureLibrary library) {
    this.stage              = stage;
    this.router             = router;
    this.library            = library;
    this.recognitionService = new RecognitionService(library, this::onRecognition);
    this.cameraPanel        = new CameraPanel();
    this.instructionPanel   = new InstructionPanel();
  }

  public void show() {
    letters          = LetterDifficulty.orderedLetters().stream()
        .filter(l -> library.getGestureVariants(l) != null)
        .collect(Collectors.toList());
    round            = 0;
    currentPlayer    = 0;
    scores           = new int[PLAYERS];
    scoredThisRound  = new boolean[PLAYERS];
    turnTicks        = 0;
    holdTicks        = 0;
    successTicks     = -1;
    liveLetter       = "–";
    liveConfidence   = 0.0;

    buildScene();
    recognitionService.start();
    cameraPanel.start();
    instructionPanel.resume();
    instructionPanel.setState(InstructionPanel.State.TALKING,
        "Player 1, show me\nthe letter " + currentLetter() + "!");
    startGameLoop();
  }

  public void stop() {
    if (gameLoop != null) gameLoop.stop();
    recognitionService.stop();
    cameraPanel.stop();
    instructionPanel.stop();
  }

  // ── Scene ─────────────────────────────────────────────────────────────────

  private void buildScene() {
    VBox root = new VBox(0);
    root.setStyle("-fx-background-color:" + UIConstants.BG_DARK + ";");

    HBox topBar = buildTopBar();
    topBar.setPadding(new Insets(16, 24, 10, 24));

    Label camHeader = UIConstants.sectionHeader("LIVE CAMERA");
    VBox camCol = new VBox(8, camHeader, cameraPanel.getPane(UIConstants.ORANGE));
    camCol.setAlignment(Pos.TOP_CENTER);
    camCol.setPadding(new Insets(10, 10, 10, 24));
    HBox.setHgrow(camCol, Priority.ALWAYS);

    Label instrHeader = UIConstants.sectionHeader("GUIDE");
    VBox instrCol = new VBox(8, instrHeader, instructionPanel.getPane(UIConstants.ORANGE));
    instrCol.setAlignment(Pos.TOP_CENTER);
    instrCol.setPadding(new Insets(10, 24, 10, 10));
    HBox.setHgrow(instrCol, Priority.ALWAYS);

    HBox topRow = new HBox(0, camCol, instrCol);
    topRow.setAlignment(Pos.TOP_LEFT);
    VBox.setVgrow(topRow, Priority.ALWAYS);

    // ── Dashboard ─────────────────────────────────────────────────────────────

    // current letter
    Label targetHeader = UIConstants.sectionHeader("CURRENT LETTER");
    targetLetterLabel = new Label(currentLetter());
    targetLetterLabel.setFont(Font.font("Arial", FontWeight.BOLD, 56));
    targetLetterLabel.setTextFill(Color.web(UIConstants.TEXT_WHITE));

    tierLabel = new Label(formatTier(currentLetter()));
    tierLabel.setFont(Font.font("Arial", 11));
    tierLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));

    roundLabel = new Label("Round 1 / " + TOTAL_ROUNDS);
    roundLabel.setFont(Font.font("Arial", 11));
    roundLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));

    VBox targetSection = dashSection(
        targetHeader, targetLetterLabel, tierLabel, roundLabel);

    // current player + timer
    Label turnHeader = UIConstants.sectionHeader("NOW PLAYING");
    currentPlayerLabel = new Label("Player 1");
    currentPlayerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 26));
    currentPlayerLabel.setTextFill(Color.web(UIConstants.ORANGE));

    timerLabel = new Label("5s");
    timerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
    timerLabel.setTextFill(Color.web(UIConstants.TEXT_WHITE));

    timerBar = new ProgressBar(1.0);
    timerBar.setPrefWidth(180);
    timerBar.setPrefHeight(10);
    timerBar.setStyle("-fx-accent:" + UIConstants.ORANGE + ";");

    VBox turnSection = dashSection(
        turnHeader, currentPlayerLabel, timerBar, timerLabel);

    // detected
    Label detectedHeader = UIConstants.sectionHeader("DETECTED");
    detectedLabel = new Label("–");
    detectedLabel.setFont(Font.font("Arial", FontWeight.BOLD, 56));
    detectedLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));

    confBar = new ProgressBar(0);
    confBar.setPrefWidth(180);
    confBar.setPrefHeight(10);
    confBar.setStyle("-fx-accent:" + UIConstants.ORANGE + ";");

    confLabel = new Label("0%");
    confLabel.setFont(Font.font("Arial", 11));
    confLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));

    feedbackLabel = new Label("");
    feedbackLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
    feedbackLabel.setMinHeight(18);

    VBox detectedSection = dashSection(
        detectedHeader, detectedLabel, confBar, confLabel, feedbackLabel);

    // scores
    Label p1Header = UIConstants.sectionHeader("PLAYER 1");
    p1ScoreLabel = new Label("0");
    p1ScoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
    p1ScoreLabel.setTextFill(Color.web(UIConstants.GREEN));

    Label p2Header = UIConstants.sectionHeader("PLAYER 2");
    p2ScoreLabel = new Label("0");
    p2ScoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
    p2ScoreLabel.setTextFill(Color.web(UIConstants.ORANGE));

    VBox scoresSection = dashSection(
        p1Header, p1ScoreLabel, p2Header, p2ScoreLabel);

    HBox dashBar = new HBox(12,
        targetSection, turnSection, detectedSection, scoresSection);
    dashBar.setAlignment(Pos.CENTER_LEFT);
    dashBar.setPadding(new Insets(10, 24, 16, 24));

    root.getChildren().addAll(topBar, topRow, dashBar);
    stage.setScene(new Scene(root, UIConstants.W, UIConstants.H));
  }

  // ── Game loop ─────────────────────────────────────────────────────────────

  private void startGameLoop() {
    gameLoop = new Timeline(new KeyFrame(Duration.millis(100), e -> tick()));
    gameLoop.setCycleCount(Timeline.INDEFINITE);
    gameLoop.play();
  }

  private void tick() {
    // ── Between-turn celebration pause ────────────────────────────────────────
    if (successTicks >= 0) {
      successTicks++;
      if (successTicks >= SUCCESS_HOLD) {
        successTicks = -1;
        nextTurn();
      }
      return;
    }

    // ── 5-second turn timer ───────────────────────────────────────────────────
    turnTicks++;
    double timeLeft = Math.max(0, (TURN_TICKS - turnTicks) * 0.1);
    timerLabel.setText(String.format("%.1fs", timeLeft));
    timerBar.setProgress((double)(TURN_TICKS - turnTicks) / TURN_TICKS);

    if (turnTicks >= TURN_TICKS) {
      // time's up for this player — no score this turn
      holdTicks      = 0;
      liveLetter     = "–";
      liveConfidence = 0.0;
      feedbackLabel.setText("⏰  Time's up!");
      feedbackLabel.setTextFill(Color.web(UIConstants.ORANGE));
      instructionPanel.setState(InstructionPanel.State.ENCOURAGING,
          "Time's up,\nPlayer " + (currentPlayer + 1) + "!");
      recognitionService.stop();
      successTicks = 0;
      return;
    }

    String letter = liveLetter;
    double conf   = liveConfidence;
    String target = currentLetter();

    detectedLabel.setText(letter);
    confBar.setProgress(conf);
    confLabel.setText(String.format("%.0f%%", conf * 100));

    boolean correctMatch = letter.equals(target) && conf >= UIConstants.PASS_THRESHOLD;

    if (correctMatch) {
      holdTicks++;
      confBar.setProgress((double) holdTicks / HOLD_NEEDED);
      confBar.setStyle("-fx-accent:" + UIConstants.GREEN + ";");
      detectedLabel.setTextFill(Color.web(UIConstants.GREEN));
      int rem = (int) Math.ceil((HOLD_NEEDED - holdTicks) * 0.1);
      feedbackLabel.setText("Hold… " + (rem > 0 ? rem + "s" : "✓"));
      feedbackLabel.setTextFill(Color.web(UIConstants.GREEN));

      if (holdTicks >= HOLD_NEEDED) {
        holdTicks = 0;
        // score = confidence × 100 × tier × time-left bonus
        int tier   = LetterDifficulty.tierOf(target);
        int earned = (int)(conf * 100) * tier;
        scores[currentPlayer] += earned;
        scoredThisRound[currentPlayer] = true;
        updateScoreLabels();

        liveLetter     = "–";
        liveConfidence = 0.0;
        recognitionService.stop();
        feedbackLabel.setText("✓  +" + earned + " pts!");
        instructionPanel.setState(InstructionPanel.State.HAPPY,
            "Player " + (currentPlayer + 1) + " scores!\n+" + earned + " points!");
        successTicks = 0;
      }

    } else {
      holdTicks = 0;
      detectedLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));
      confBar.setStyle("-fx-accent:" + UIConstants.ORANGE + ";");
      if (!letter.equals("–")) {
        feedbackLabel.setText("Keep trying…");
        feedbackLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));
      }
    }
  }

  /** Called after each turn (either scored or timed out). */
  private void nextTurn() {
    holdTicks      = 0;
    successTicks   = -1;
    liveLetter     = "–";
    liveConfidence = 0.0;
    turnTicks      = 0;

    currentPlayer++;

    if (currentPlayer < PLAYERS) {
      // next player's turn, same letter
      updateTurnUI();
      recognitionService.start();
      instructionPanel.setState(InstructionPanel.State.TALKING,
          "Player " + (currentPlayer + 1) + ", show me\nthe letter " + currentLetter() + "!");
    } else {
      // all players done — advance round
      currentPlayer   = 0;
      scoredThisRound = new boolean[PLAYERS];
      round++;

      if (round >= TOTAL_ROUNDS) {
        stop();
        router.showBattleResult(scores[0], scores[1]);
        return;
      }

      updateTurnUI();
      recognitionService.start();
      instructionPanel.setState(InstructionPanel.State.TALKING,
          "Round " + (round + 1) + "!\nPlayer 1, show me\n" + currentLetter() + "!");
    }
  }

  private void updateTurnUI() {
    targetLetterLabel.setText(currentLetter());
    tierLabel.setText(formatTier(currentLetter()));
    roundLabel.setText("Round " + (round + 1) + " / " + TOTAL_ROUNDS);
    currentPlayerLabel.setText("Player " + (currentPlayer + 1));
    currentPlayerLabel.setTextFill(
        currentPlayer == 0 ? Color.web(UIConstants.GREEN) : Color.web(UIConstants.ORANGE));
    timerBar.setStyle("-fx-accent:"
        + (currentPlayer == 0 ? UIConstants.GREEN : UIConstants.ORANGE) + ";");
    timerBar.setProgress(1.0);
    timerLabel.setText("5.0s");
    feedbackLabel.setText("");
    detectedLabel.setText("–");
    detectedLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));
    confBar.setProgress(0);
    confLabel.setText("0%");
  }

  private void updateScoreLabels() {
    p1ScoreLabel.setText(String.valueOf(scores[0]));
    p2ScoreLabel.setText(String.valueOf(scores[1]));
  }

  // ── Recognition callback ──────────────────────────────────────────────────

  private void onRecognition(String letter, double confidence) {
    if (letter.startsWith("ERROR:")) { stop(); router.showError(letter.substring(6)); return; }
    if (successTicks >= 0) return;
    liveLetter     = letter;
    liveConfidence = confidence;
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  private HBox buildTopBar() {
    Button backBtn = new Button("← Home");
    backBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:"
        + UIConstants.ORANGE + "; -fx-font-size:14; -fx-cursor:hand;");
    backBtn.setOnAction(e -> { stop(); router.showHome(); });

    Label title = new Label("⚔️  Battle Mode  —  " + TOTAL_ROUNDS + " Rounds");
    title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
    title.setTextFill(Color.web(UIConstants.ORANGE));

    HBox bar = new HBox(12, backBtn, title);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setMaxWidth(UIConstants.W - 80);
    return bar;
  }

  private VBox dashSection(javafx.scene.Node... nodes) {
    VBox box = new VBox(4);
    box.setAlignment(Pos.CENTER_LEFT);
    box.setPadding(new Insets(12, 24, 12, 24));
    box.setStyle("-fx-background-color:" + UIConstants.BG_CARD
        + "; -fx-background-radius:12;");
    box.getChildren().addAll(nodes);
    return box;
  }

  private String currentLetter() {
    if (letters == null || round >= letters.size()) return "?";
    return letters.get(round);
  }

  private String formatTier(String letter) {
    int tier = LetterDifficulty.tierOf(letter);
    String[] stars = { "", "★☆☆☆☆", "★★☆☆☆", "★★★☆☆", "★★★★☆", "★★★★★" };
    return "Difficulty: " + (tier >= 1 && tier <= 5 ? stars[tier] : "");
  }
}