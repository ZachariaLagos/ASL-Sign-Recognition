package aslframework.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class UIConstants {

  private UIConstants() {}

  // Window
  public static final double W       = 1200;
  public static final double H       = 660;
  public static final double CAM_W   = 380;
  public static final double CAM_H   = 460;
  public static final double INSTR_W = 300;

  // Palette
  public static final String BG_DARK    = "#1a1a2e";
  public static final String BG_CARD    = "#16213e";
  public static final String ACCENT     = "#0f3460";
  public static final String GREEN      = "#4ecca3";
  public static final String ORANGE     = "#e94560";
  public static final String TEXT_WHITE = "#eaeaea";
  public static final String TEXT_GREY  = "#888888";

  // Thresholds
  public static final double PASS_THRESHOLD = 0.85;
  public static final int    REQUIRED_HITS  = 1;

  public static VBox styledRoot() {
    VBox box = new VBox(20);
    box.setAlignment(Pos.CENTER);
    box.setPadding(new Insets(40));
    box.setStyle("-fx-background-color:" + BG_DARK + ";");
    return box;
  }

  public static VBox styledCard() {
    VBox card = new VBox(12);
    card.setAlignment(Pos.CENTER);
    card.setPadding(new Insets(20, 18, 20, 18));
    card.setPrefWidth(210);
    card.setMinHeight(260);
    card.setStyle("-fx-background-color:" + BG_CARD + "; -fx-background-radius:16;");
    return card;
  }

  public static StackPane instructionPanel(String accentColor) {
    Label lbl = new Label("▶  Instructions");
    lbl.setFont(Font.font("Arial", FontWeight.BOLD, 15));
    lbl.setTextFill(Color.web(TEXT_GREY));
    StackPane pane = new StackPane(lbl);
    pane.setPrefSize(CAM_W, CAM_H);
    pane.setMinSize(CAM_W, CAM_H);
    pane.setMaxSize(CAM_W, CAM_H);
    pane.setStyle(
        "-fx-background-color:#0d0d1a;"
            + "-fx-background-radius:16;"
            + "-fx-border-color:" + accentColor + ";"
            + "-fx-border-width:2;"
            + "-fx-border-radius:16;");
    return pane;
  }

  public static Button styledButton(String text, String accentColor) {
    Button btn = new Button(text);
    btn.setFont(Font.font("Arial", FontWeight.BOLD, 14));
    btn.setTextFill(Color.web(BG_DARK));
    btn.setStyle(
        "-fx-background-color:" + accentColor + ";"
            + "-fx-background-radius:10;"
            + "-fx-padding:10 30;"
            + "-fx-cursor:hand;");
    btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
    btn.setOnMouseExited(e  -> btn.setOpacity(1.0));
    return btn;
  }

  public static Label sectionHeader(String text) {
    Label lbl = new Label(text);
    lbl.setFont(Font.font("Arial", 11));
    lbl.setTextFill(Color.web(TEXT_GREY));
    return lbl;
  }
}