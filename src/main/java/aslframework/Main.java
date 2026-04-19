package aslframework;

import aslframework.ui.Open;

/**
 * Entry point for the ASL Recognition and Learning Platform.
 * Delegates to {@link Open}, which plays the splash screen
 * before handing off to {@link aslframework.ui.GameUI}.
 */
public class Main {
  public static void main(String[] args) {
    Open.main(args);
  }
}