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
 * Practice mode game screen.
 *
 * <p>The player works through A-Z. Each letter must be held for 1 second
 * to register a correct answer. The player starts with 3 hearts.
 * Holding a wrong letter for 1s costs one heart.
 * When all hearts are lost the session ends early.
 * Remaining hearts each add {@code HEART_BONUS} points to the final score.
 */
public class PracticeScreen {

  private final Stage              stage;
  private final GameUI             router;
  private final GestureLibrary     library;
  private final RecognitionService recognitionService;
  private final CameraPanel        cameraPanel;
  private final InstructionPanel   instructionPanel;

  // ── Session state ─────────────────────────────────────────────────────────
  private List<String> letters;
  private int          letterIndex      = 0;
  private int          score            = 0;
  private int          hearts           = 3;
  private static final int MAX_HEARTS   = 3;
  private static final int HEART_BONUS  = 50; // points per remaining heart

  // ── Hold timing (100ms per tick) ──────────────────────────────────────────
  private int          holdTicks        = 0;
  private int          wrongHoldTicks   = 0;
  private int          successTicks     = -1;
  private int          graceTicks       = -1; // 3s pause after losing a heart
  private static final int HOLD_NEEDED  = 10; // 1s
  private static final int SUCCESS_HOLD = 30; // 3s
  private static final int GRACE_HOLD   = 30; // 3s grace after heart lost

  // ── Live recognition data ─────────────────────────────────────────────────
  private String  liveLetter     = "–";
  private double  liveConfidence = 0.0;

  // ── UI nodes ──────────────────────────────────────────────────────────────
  private Label       targetLetterLabel;
  private Label       difficultyLabel;
  private Label       progressLabel;
  private Label       scoreLabel;
  private Label       detectedLabel;
  private ProgressBar confBar;
  private Label       confLabel;
  private Label       feedbackLabel;
  private HBox        heartsRow;
  private Timeline    gameLoop;

  public PracticeScreen(Stage stage, GameUI router, GestureLibrary library) {
    this.stage              = stage;
    this.router             = router;
    this.library            = library;
    this.recognitionService = new RecognitionService(library, this::onRecognition);
    this.cameraPanel        = new CameraPanel();
    this.instructionPanel   = new InstructionPanel();
  }

  public void show() {
    letters        = library.getAllGestures().keySet().stream()
        .sorted().collect(Collectors.toList());
    letterIndex    = 0;
    score          = 0;
    hearts         = MAX_HEARTS;
    holdTicks      = 0;
    wrongHoldTicks = 0;
    successTicks   = -1;
    graceTicks     = -1;
    liveLetter     = "–";
    liveConfidence = 0.0;

    buildScene();
    recognitionService.start();
    cameraPanel.start();
    instructionPanel.resume();
    instructionPanel.setState(InstructionPanel.State.TALKING,
        "Show me the letter\n" + letters.get(0) + "!");
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
    VBox camCol = new VBox(8, camHeader, cameraPanel.getPane(UIConstants.GREEN));
    camCol.setAlignment(Pos.TOP_CENTER);
    camCol.setPadding(new Insets(10, 10, 10, 24));
    HBox.setHgrow(camCol, Priority.ALWAYS);

    Label instrHeader = UIConstants.sectionHeader("GUIDE");
    VBox instrCol = new VBox(8, instrHeader, instructionPanel.getPane(UIConstants.GREEN));
    instrCol.setAlignment(Pos.TOP_CENTER);
    instrCol.setPadding(new Insets(10, 24, 10, 10));
    HBox.setHgrow(instrCol, Priority.ALWAYS);

    HBox topRow = new HBox(0, camCol, instrCol);
    topRow.setAlignment(Pos.TOP_LEFT);
    VBox.setVgrow(topRow, Priority.ALWAYS);

    // target
    Label targetHeader = UIConstants.sectionHeader("SHOW THIS LETTER");
    targetLetterLabel = new Label(letters.get(0));
    targetLetterLabel.setFont(Font.font("Arial", FontWeight.BOLD, 56));
    targetLetterLabel.setTextFill(Color.web(UIConstants.TEXT_WHITE));

    difficultyLabel = new Label(formatTier(letters.get(0)));
    difficultyLabel.setFont(Font.font("Arial", 11));
    difficultyLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));

    progressLabel = new Label("Letter 1 / " + letters.size());
    progressLabel.setFont(Font.font("Arial", 11));
    progressLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));

    VBox targetSection = dashSection(UIConstants.BG_CARD,
        targetHeader, targetLetterLabel, difficultyLabel, progressLabel);

    // detected
    Label detectedHeader = UIConstants.sectionHeader("DETECTED");
    detectedLabel = new Label("–");
    detectedLabel.setFont(Font.font("Arial", FontWeight.BOLD, 56));
    detectedLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));

    confBar = new ProgressBar(0);
    confBar.setPrefWidth(200);
    confBar.setPrefHeight(10);
    confBar.setStyle("-fx-accent:" + UIConstants.GREEN + ";");

    confLabel = new Label("0%");
    confLabel.setFont(Font.font("Arial", 11));
    confLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));

    feedbackLabel = new Label("");
    feedbackLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
    feedbackLabel.setMinHeight(18);

    VBox detectedSection = dashSection(UIConstants.BG_CARD,
        detectedHeader, detectedLabel, confBar, confLabel, feedbackLabel);

    // hearts
    Label heartsHeader = UIConstants.sectionHeader("HEARTS");
    heartsRow = buildHeartsRow(hearts);
    Label heartHint = new Label("+" + HEART_BONUS + " pts each remaining");
    heartHint.setFont(Font.font("Arial", 10));
    heartHint.setTextFill(Color.web(UIConstants.TEXT_GREY));

    VBox heartsSection = dashSection(UIConstants.BG_CARD,
        heartsHeader, heartsRow, heartHint);

    // score
    Label scoreHeader = UIConstants.sectionHeader("SCORE");
    scoreLabel = new Label("0");
    scoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 40));
    scoreLabel.setTextFill(Color.web(UIConstants.GREEN));

    VBox scoreSection = dashSection(UIConstants.BG_CARD, scoreHeader, scoreLabel);

    HBox dashBar = new HBox(12,
        targetSection, detectedSection, heartsSection, scoreSection);
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
    // ── Success pause (3s before advancing to next letter) ────────────────────
    if (successTicks >= 0) {
      successTicks++;
      if (successTicks >= SUCCESS_HOLD) {
        successTicks = -1;
        advanceLetter();
      }
      return;
    }

    // ── Grace pause (3s after losing a heart, same letter resumes) ────────────
    if (graceTicks >= 0) {
      graceTicks++;
      if (graceTicks >= GRACE_HOLD) {
        graceTicks     = -1;
        liveLetter     = "–";
        liveConfidence = 0.0;
        recognitionService.start();
        feedbackLabel.setText("Try again!");
        feedbackLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));
        instructionPanel.setState(InstructionPanel.State.TALKING,
            "Let's try again!\nShow me " + letters.get(letterIndex) + ".");
      }
      return;
    }

    String letter = liveLetter;
    double conf   = liveConfidence;
    String target = letters.get(letterIndex);

    detectedLabel.setText(letter);
    confBar.setProgress(conf);
    confLabel.setText(String.format("%.0f%%", conf * 100));

    boolean correctMatch = letter.equals(target) && conf >= UIConstants.PASS_THRESHOLD;
    boolean wrongMatch   = !letter.equals("–")
        && !letter.equals(target)
        && conf >= UIConstants.PASS_THRESHOLD;

    if (correctMatch) {
      wrongHoldTicks = 0;
      holdTicks++;
      confBar.setProgress((double) holdTicks / HOLD_NEEDED);
      confBar.setStyle("-fx-accent:" + UIConstants.GREEN + ";");
      detectedLabel.setTextFill(Color.web(UIConstants.GREEN));
      int rem = (int) Math.ceil((HOLD_NEEDED - holdTicks) * 0.1);
      feedbackLabel.setText("Hold… " + (rem > 0 ? rem + "s" : "✓"));
      feedbackLabel.setTextFill(Color.web(UIConstants.GREEN));

      if (holdTicks >= HOLD_NEEDED) {
        holdTicks      = 0;
        int tier       = LetterDifficulty.tierOf(target);
        score         += (int)(conf * 100) * tier;
        scoreLabel.setText(String.valueOf(score));
        feedbackLabel.setText("✓  Correct!");
        successTicks   = 0;
        liveLetter     = "–";
        liveConfidence = 0.0;
        recognitionService.stop();
        instructionPanel.setState(InstructionPanel.State.HAPPY,
            "Amazing! Moving to\nthe next letter…");
      }

    } else if (wrongMatch) {
      holdTicks = 0;
      wrongHoldTicks++;
      confBar.setProgress((double) wrongHoldTicks / HOLD_NEEDED);
      confBar.setStyle("-fx-accent:" + UIConstants.ORANGE + ";");
      detectedLabel.setTextFill(Color.web(UIConstants.ORANGE));
      int rem = (int) Math.ceil((HOLD_NEEDED - wrongHoldTicks) * 0.1);
      feedbackLabel.setText("Wrong! " + (rem > 0 ? rem + "s" : "✗"));
      feedbackLabel.setTextFill(Color.web(UIConstants.ORANGE));

      if (wrongHoldTicks >= HOLD_NEEDED) {
        wrongHoldTicks = 0;
        holdTicks      = 0;
        hearts--;
        liveLetter     = "–";
        liveConfidence = 0.0;
        recognitionService.stop();
        refreshHearts();
        instructionPanel.setState(InstructionPanel.State.ENCOURAGING,
            "Oops! That was " + letter + ".\nTry " + target + " again.\n"
                + hearts + " heart(s) left.");

        if (hearts <= 0) {
          stop();
          router.showPracticeComplete(score, letterIndex, 0);
        } else {
          feedbackLabel.setText("💔  " + hearts + " heart(s) left — resuming in 3s…");
          feedbackLabel.setTextFill(Color.web(UIConstants.ORANGE));
          graceTicks = 0;
        }
      }

    } else {
      holdTicks      = 0;
      wrongHoldTicks = 0;
      detectedLabel.setTextFill(
          conf >= UIConstants.PASS_THRESHOLD - 0.1
              ? Color.web(UIConstants.ORANGE)
              : Color.web(UIConstants.TEXT_GREY));
      confBar.setStyle("-fx-accent:" + UIConstants.ORANGE + ";");
      if (!letter.equals("–")) {
        feedbackLabel.setText("Keep trying…");
        feedbackLabel.setTextFill(Color.web(UIConstants.TEXT_GREY));
        instructionPanel.setState(InstructionPanel.State.ENCOURAGING,
            "Almost there!\nTry " + target + " again.");
      }
    }
  }

  private void advanceLetter() {
    holdTicks      = 0;
    wrongHoldTicks = 0;
    successTicks   = -1;
    graceTicks     = -1;
    liveLetter     = "–";
    liveConfidence = 0.0;
    letterIndex++;

    if (letterIndex >= letters.size()) {
      stop();
      int bonus = hearts * HEART_BONUS;
      router.showPracticeComplete(score + bonus, letters.size(), hearts);
      return;
    }

    String next = letters.get(letterIndex);
    targetLetterLabel.setText(next);
    difficultyLabel.setText(formatTier(next));
    progressLabel.setText("Letter " + (letterIndex + 1) + " / " + letters.size());
    feedbackLabel.setText("⬆  Next letter!");
    instructionPanel.setState(InstructionPanel.State.TALKING,
        "Now show me the\nletter " + next + "!");
    recognitionService.start();
  }

  // ── Callbacks ─────────────────────────────────────────────────────────────

  private void onRecognition(String letter, double confidence) {
    if (letter.startsWith("ERROR:")) { stop(); router.showError(letter.substring(6)); return; }
    if (successTicks >= 0) return;
    if (graceTicks   >= 0) return;
    liveLetter     = letter;
    liveConfidence = confidence;
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  private HBox buildTopBar() {
    Button backBtn = new Button("← Home");
    backBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:"
        + UIConstants.GREEN + "; -fx-font-size:14; -fx-cursor:hand;");
    backBtn.setOnAction(e -> { stop(); router.showHome(); });

    Label title = new Label("🎓  Practice Mode");
    title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
    title.setTextFill(Color.web(UIConstants.GREEN));

    HBox bar = new HBox(12, backBtn, title);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setMaxWidth(UIConstants.W - 80);
    return bar;
  }

  private HBox buildHeartsRow(int count) {
    HBox row = new HBox(8);
    row.setAlignment(Pos.CENTER_LEFT);
    for (int i = 0; i < MAX_HEARTS; i++) {
      Label h = new Label(i < count ? "❤️" : "🖤");
      h.setFont(Font.font(22));
      row.getChildren().add(h);
    }
    return row;
  }

  private void refreshHearts() {
    heartsRow.getChildren().clear();
    for (int i = 0; i < MAX_HEARTS; i++) {
      Label h = new Label(i < hearts ? "❤️" : "🖤");
      h.setFont(Font.font(22));
      heartsRow.getChildren().add(h);
    }
  }

  /** Creates a styled dashboard section VBox. */
  private VBox dashSection(String bg, javafx.scene.Node... nodes) {
    VBox box = new VBox(4);
    box.setAlignment(Pos.CENTER_LEFT);
    box.setPadding(new Insets(12, 24, 12, 24));
    box.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:12;");
    box.getChildren().addAll(nodes);
    return box;
  }

  private String formatTier(String letter) {
    int tier = LetterDifficulty.tierOf(letter);
    String[] stars = { "", "★☆☆☆☆", "★★☆☆☆", "★★★☆☆", "★★★★☆", "★★★★★" };
    return "Difficulty: " + (tier >= 1 && tier <= 5 ? stars[tier] : "");
  }
}