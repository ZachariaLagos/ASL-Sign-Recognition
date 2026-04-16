package aslframework;

import aslframework.ui.GameUI;
import javafx.application.Application;

/**
 * JavaFX UI launcher for the ASL Learning Platform.
 *
 * <p>Person C's entry point. Run this to launch the game UI.
 *
 * <pre>
 *   mvn javafx:run
 * </pre>
 *
 * <p>For console-based recognition testing, see {@code Main}.
 */
public class UIMain {
  public static void main(String[] args) {
    Application.launch(GameUI.class, args);
  }
}
