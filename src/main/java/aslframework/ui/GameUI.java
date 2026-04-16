package aslframework.ui;

import aslframework.recognition.GestureLibrary;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * JavaFX {@link Application} entry point and screen router for the ASL
 * Learning Platform.
 *
 * <p>Person C owns this class. Its sole responsibilities are:
 * <ol>
 *   <li>Initialising the {@link GestureLibrary} at startup.</li>
 *   <li>Creating each screen object once and reusing them.</li>
 *   <li>Exposing {@code showXxx()} navigation methods so screens can request
 *       transitions without holding direct references to each other.</li>
 * </ol>
 *
 * <p>{@link aslframework.UIMain} launches this class via
 * {@code Application.launch(GameUI.class, args)}.
 */
public class GameUI extends Application {

  private HomeScreen     homeScreen;
  private PracticeScreen practiceScreen;
  private BattleScreen   battleScreen;

  private Stage primaryStage;

  // ── JavaFX lifecycle ──────────────────────────────────────────────────────

  @Override
  public void start(Stage stage) {
    this.primaryStage = stage;
    stage.setTitle("ASL Learning Platform");
    stage.setResizable(false);
    stage.setOnCloseRequest(e -> stopAllScreens());

    GestureLibrary library;
    try {
      library = new GestureLibrary();
    } catch (Exception ex) {
      showFatalError(stage, ex.getMessage());
      return;
    }

    homeScreen     = new HomeScreen(stage, this, library);
    practiceScreen = new PracticeScreen(stage, this, library);
    battleScreen   = new BattleScreen(stage, this, library);

    showHome();
    stage.show();
  }

  @Override
  public void stop() {
    stopAllScreens();
  }

  // ── Navigation API ────────────────────────────────────────────────────────

  public void showHome()     { homeScreen.show(); }
  public void showPractice() { practiceScreen.show(); }
  public void showBattle()   { battleScreen.show(); }

  /**
   * Shows the practice result screen.
   *
   * @param score           final score (base + heart bonus already summed)
   * @param lettersCleared  letters successfully cleared
   * @param heartsRemaining hearts remaining at end of session
   */
  public void showPracticeComplete(int score, int lettersCleared, int heartsRemaining) {
    VBox root = UIConstants.styledRoot();

    boolean fullClear = heartsRemaining > 0;

    Label icon = new Label(fullClear ? "🏆" : "💔");
    icon.setFont(Font.font(72));

    Label title = new Label(fullClear ? "Practice Complete!" : "Out of Hearts!");
    title.setFont(Font.font("Arial", FontWeight.BOLD, 34));
    title.setTextFill(Color.web(fullClear ? UIConstants.GREEN : UIConstants.ORANGE));

    Label sub = new Label("Letters cleared: " + lettersCleared);
    sub.setFont(Font.font("Arial", 16));
    sub.setTextFill(Color.web(UIConstants.TEXT_GREY));

    // hearts row
    HBox heartsRow = new HBox(8);
    heartsRow.setAlignment(Pos.CENTER);
    for (int i = 0; i < 3; i++) {
      Label h = new Label(i < heartsRemaining ? "❤️" : "🖤");
      h.setFont(Font.font(28));
      heartsRow.getChildren().add(h);
    }

    int bonus = heartsRemaining * 50;
    Label bonusLbl = new Label("Heart bonus: +" + bonus + " pts");
    bonusLbl.setFont(Font.font("Arial", 14));
    bonusLbl.setTextFill(Color.web(UIConstants.GREEN));

    Label scoreLbl = new Label("Final Score: " + score);
    scoreLbl.setFont(Font.font("Arial", FontWeight.BOLD, 28));
    scoreLbl.setTextFill(Color.web(UIConstants.TEXT_WHITE));
    VBox.setMargin(scoreLbl, new Insets(12, 0, 20, 0));

    Button retryBtn = UIConstants.styledButton("Try Again", UIConstants.GREEN);
    retryBtn.setOnAction(e -> showPractice());

    Button homeBtn = UIConstants.styledButton("Back to Home", UIConstants.TEXT_GREY);
    homeBtn.setOnAction(e -> showHome());

    HBox btns = new HBox(16, retryBtn, homeBtn);
    btns.setAlignment(Pos.CENTER);

    root.getChildren().addAll(icon, title, sub, heartsRow, bonusLbl, scoreLbl, btns);
    primaryStage.setScene(new Scene(root, UIConstants.W, UIConstants.H));
  }

  /**
   * Shows the battle result screen.
   *
   * @param p1Score Player 1 final score
   * @param p2Score Player 2 final score
   */
  public void showBattleResult(int p1Score, int p2Score) {
    VBox root = UIConstants.styledRoot();

    boolean tie    = p1Score == p2Score;
    int     winner = p1Score >= p2Score ? 1 : 2;

    Label icon = new Label(tie ? "🤝" : "🏆");
    icon.setFont(Font.font(72));

    Label title = new Label(tie ? "It's a Tie!" : "Player " + winner + " Wins!");
    title.setFont(Font.font("Arial", FontWeight.BOLD, 34));
    title.setTextFill(Color.web(tie ? UIConstants.TEXT_GREY : UIConstants.GREEN));

    Label sub = new Label("After " + BattleScreen.TOTAL_ROUNDS + " rounds");
    sub.setFont(Font.font("Arial", 16));
    sub.setTextFill(Color.web(UIConstants.TEXT_GREY));
    VBox.setMargin(sub, new Insets(0, 0, 20, 0));

    Label p1Lbl = new Label("Player 1:  " + p1Score + " pts"
        + (winner == 1 && !tie ? "  👑" : ""));
    p1Lbl.setFont(Font.font("Arial", FontWeight.BOLD, 22));
    p1Lbl.setTextFill(Color.web(winner == 1 || tie
        ? UIConstants.GREEN : UIConstants.TEXT_GREY));

    Label p2Lbl = new Label("Player 2:  " + p2Score + " pts"
        + (winner == 2 && !tie ? "  👑" : ""));
    p2Lbl.setFont(Font.font("Arial", FontWeight.BOLD, 22));
    p2Lbl.setTextFill(Color.web(winner == 2 || tie
        ? UIConstants.ORANGE : UIConstants.TEXT_GREY));
    VBox.setMargin(p2Lbl, new Insets(0, 0, 24, 0));

    Button retryBtn = UIConstants.styledButton("Play Again", UIConstants.ORANGE);
    retryBtn.setOnAction(e -> showBattle());

    Button homeBtn = UIConstants.styledButton("Back to Home", UIConstants.GREEN);
    homeBtn.setOnAction(e -> showHome());

    HBox btns = new HBox(16, retryBtn, homeBtn);
    btns.setAlignment(Pos.CENTER);

    root.getChildren().addAll(icon, title, sub, p1Lbl, p2Lbl, btns);
    primaryStage.setScene(new Scene(root, UIConstants.W, UIConstants.H));
  }

  /**
   * Shows an error screen.
   *
   * @param message the error message to display
   */
  public void showError(String message) {
    VBox root = UIConstants.styledRoot();

    Label icon = new Label("\u26a0\ufe0f");
    icon.setFont(Font.font(48));

    Label lbl = new Label(message);
    lbl.setFont(Font.font("Arial", 16));
    lbl.setTextFill(Color.web(UIConstants.ORANGE));
    lbl.setWrapText(true);
    lbl.setMaxWidth(600);

    Button homeBtn = UIConstants.styledButton("Back to Home", UIConstants.GREEN);
    homeBtn.setOnAction(e -> showHome());

    root.getChildren().addAll(icon, lbl, homeBtn);
    primaryStage.setScene(new Scene(root, UIConstants.W, UIConstants.H));
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private void stopAllScreens() {
    if (practiceScreen != null) practiceScreen.stop();
    if (battleScreen   != null) battleScreen.stop();
  }

  private void showFatalError(Stage stage, String message) {
    VBox root = UIConstants.styledRoot();
    Label lbl = new Label("Fatal error — could not load gesture library:\n" + message);
    lbl.setTextFill(Color.web(UIConstants.ORANGE));
    lbl.setWrapText(true);
    root.getChildren().add(lbl);
    stage.setScene(new Scene(root, UIConstants.W, UIConstants.H));
    stage.show();
  }
}