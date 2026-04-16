package aslframework.ui;

import aslframework.game.GameMode;
import aslframework.recognition.GestureLibrary;

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
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

/**
 * Home screen of the ASL Learning Platform.
 *
 * <p>Displays two mode-selection cards — Practice and Battle — and routes
 * the user to the appropriate game screen on click. This is always the first
 * screen the user sees; it is also returned to after a session ends.
 *
 * <p>Person C owns this class. It has no dependency on Person A or Person B's
 * code — it only reads {@link GestureLibrary#size()} to show a "gestures loaded"
 * pill, and delegates navigation to {@link GameUI}.
 */
public class HomeScreen {

  // ── Palette (shared with other screens via UIConstants) ──────────────────
  private static final String BG_DARK  = UIConstants.BG_DARK;
  private static final String BG_CARD  = UIConstants.BG_CARD;
  private static final String ACCENT   = UIConstants.ACCENT;
  private static final String GREEN    = UIConstants.GREEN;
  private static final String ORANGE   = UIConstants.ORANGE;
  private static final String TXT_WHITE= UIConstants.TEXT_WHITE;
  private static final String TXT_GREY = UIConstants.TEXT_GREY;

  private final Stage        stage;
  private final GameUI       router;
  private final GestureLibrary library;

  /**
   * Constructs the home screen.
   *
   * @param stage   the primary stage to render into
   * @param router  the {@link GameUI} navigator used to switch screens
   * @param library the loaded gesture library (used only for the count pill)
   */
  public HomeScreen(Stage stage, GameUI router, GestureLibrary library) {
    this.stage   = stage;
    this.router  = router;
    this.library = library;
  }

  /**
   * Builds and sets the home scene on the primary stage.
   */
  public void show() {
    VBox root = UIConstants.styledRoot();

    // ── Title ────────────────────────────────────────────────────────────────
    Label title = new Label("ASL Learning Platform");
    title.setFont(Font.font("Arial", FontWeight.BOLD, 36));
    title.setTextFill(Color.web(TXT_WHITE));

    Label subtitle = new Label("Choose a game mode to begin");
    subtitle.setFont(Font.font("Arial", 16));
    subtitle.setTextFill(Color.web(TXT_GREY));

    // ── Gesture count pill ───────────────────────────────────────────────────
    Label loadedPill = new Label("✓  " + library.size() + " gestures loaded");
    loadedPill.setFont(Font.font("Arial", 13));
    loadedPill.setTextFill(Color.web(GREEN));
    loadedPill.setPadding(new Insets(6, 16, 6, 16));
    loadedPill.setStyle(
        "-fx-background-color:" + ACCENT + ";"
        + "-fx-background-radius:20;");
    VBox.setMargin(loadedPill, new Insets(0, 0, 28, 0));

    // ── Mode cards ───────────────────────────────────────────────────────────
    HBox cards = new HBox(30,
        buildModeCard(
            "🎓", "Practice Mode",
            "Work through A→Z at your own pace.\n"
            + "Clear each letter 3 times in a row to advance.",
            GREEN, GameMode.PRACTICE),
        buildModeCard(
            "⚔️", "Battle Mode",
            "One mistake and you're out!\n"
            + "Letters get harder each round.\nLast one standing wins.",
            ORANGE, GameMode.BATTLE)
    );
    cards.setAlignment(Pos.CENTER);

    root.getChildren().addAll(title, subtitle, loadedPill, cards);
    stage.setScene(new Scene(root, UIConstants.W, UIConstants.H));
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private VBox buildModeCard(String emoji, String title, String description,
                              String accentColor, GameMode mode) {
    VBox card = new VBox(14);
    card.setAlignment(Pos.CENTER);
    card.setPadding(new Insets(36, 30, 36, 30));
    card.setPrefWidth(320);
    applyCardStyle(card, accentColor, BG_CARD);

    card.setOnMouseEntered(e -> applyCardStyle(card, accentColor, "#1e2d50"));
    card.setOnMouseExited(e  -> applyCardStyle(card, accentColor, BG_CARD));

    Label emojiLbl = new Label(emoji);
    emojiLbl.setFont(Font.font(48));

    Label titleLbl = new Label(title);
    titleLbl.setFont(Font.font("Arial", FontWeight.BOLD, 22));
    titleLbl.setTextFill(Color.web(accentColor));

    Label descLbl = new Label(description);
    descLbl.setFont(Font.font("Arial", 13));
    descLbl.setTextFill(Color.web(TXT_GREY));
    descLbl.setWrapText(true);
    descLbl.setTextAlignment(TextAlignment.CENTER);
    descLbl.setMaxWidth(260);

    Button startBtn = UIConstants.styledButton("Start", accentColor);
    startBtn.setOnAction(e -> {
      if (mode == GameMode.PRACTICE) router.showPractice();
      else                           router.showBattle();
    });

    card.getChildren().addAll(emojiLbl, titleLbl, descLbl, startBtn);
    return card;
  }

  private static void applyCardStyle(VBox card, String border, String bg) {
    card.setStyle(
        "-fx-background-color:" + bg + ";"
        + "-fx-background-radius:16;"
        + "-fx-border-color:" + border + ";"
        + "-fx-border-width:2;"
        + "-fx-border-radius:16;"
        + "-fx-cursor:hand;");
  }
}
