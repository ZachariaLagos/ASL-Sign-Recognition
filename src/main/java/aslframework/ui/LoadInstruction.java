package aslframework.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.io.File;

/**
 * Encapsulates the instruction video player for the ASL learning platform.
 *
 * <p>Manages loading, playing, and replaying per-letter MP4 instruction videos
 * stored at {@code assets/guidance/<letter>.mp4}. Exposes a {@link StackPane}
 * that can be embedded directly into the instruction panel of {@link GameUI}.
 *
 * <p>Usage:
 * <pre>{@code
 * LoadInstruction loader = new LoadInstruction(VIDEO_DIR);
 * instructionPane.getChildren().add(0, loader.getView());
 * loader.load("A");   // call whenever the target letter changes
 * loader.dispose();   // call on window close
 * }</pre>
 */
public class LoadInstruction {

  private final String     videoDir;
  private MediaPlayer      mediaPlayer;
  private final MediaView  mediaView;
  private final Button     replayButton;
  private final StackPane  view;

  /**
   * Constructs a {@code LoadInstruction} for the given video directory.
   *
   * @param videoDir absolute path to the folder containing a.mp4 … z.mp4
   */
  public LoadInstruction(String videoDir) {
    this.videoDir = videoDir;

    // ── MediaView ──────────────────────────────────────────────────────────────
    mediaView = new MediaView();
    mediaView.setPreserveRatio(false);
    mediaView.setVisible(true);

    // ── Replay button ──────────────────────────────────────────────────────────
    replayButton = new Button("\u25b6  Replay");
    replayButton.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));
    replayButton.setPrefWidth(120);
    replayButton.setStyle(styleReplay(false));
    replayButton.setOnMouseEntered(e -> replayButton.setStyle(styleReplay(true)));
    replayButton.setOnMouseExited(e  -> replayButton.setStyle(styleReplay(false)));
    replayButton.setOnAction(e -> replay());
    replayButton.setVisible(false);

    // ── Container ──────────────────────────────────────────────────────────────
    view = new StackPane(mediaView, replayButton);
    view.setAlignment(Pos.CENTER);
    view.setStyle(
        "-fx-background-color: #111111;" +
            "-fx-border-color: #484848;" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;"
    );

    // Size is set externally via setFixedSize() after the scene is shown
  }

  // ── Public API ────────────────────────────────────────────────────────────────

  /**
   * Returns the {@link StackPane} containing the video and replay button.
   * Embed this node into the instruction panel layout.
   *
   * @return the video container node
   */
  public StackPane getView() {
    return view;
  }

  /**
   * Sets fixed pixel dimensions for the video container and MediaView.
   * Call once after the scene is shown to lock in the size with no animation.
   *
   * @param width  fixed width in pixels
   * @param height fixed height in pixels
   */
  public void setFixedSize(double width, double height) {
    view.setPrefWidth(width);
    view.setPrefHeight(height);
    view.setMinWidth(width);
    view.setMinHeight(height);
    view.setMaxWidth(width);
    view.setMaxHeight(height);
    mediaView.setFitWidth(width);
    mediaView.setFitHeight(height);
  }

  /**
   * Loads and immediately plays the instruction video for the given letter.
   * Disposes the previous {@link MediaPlayer} before loading the new one.
   *
   * @param letter the ASL letter to show (e.g. {@code "A"})
   */
  public void load(String letter) {
    disposeCurrent();

    File videoFile = new File(videoDir + letter.toLowerCase() + ".mp4");
    if (!videoFile.exists()) {
      System.err.println("[LoadInstruction] Video not found: " + videoFile.getAbsolutePath());
      mediaView.setVisible(false);
      replayButton.setVisible(false);
      return;
    }

    Media media = new Media(videoFile.toURI().toString());
    mediaPlayer = new MediaPlayer(media);
    mediaPlayer.setCycleCount(1);        // play exactly once
    mediaPlayer.setStopTime(media.getDuration().isUnknown()
        ? javafx.util.Duration.INDEFINITE
        : media.getDuration());          // hard stop at end

    mediaView.setMediaPlayer(mediaPlayer);
    mediaView.setVisible(true);
    replayButton.setVisible(false);

    // When duration is known, set hard stop time
    mediaPlayer.setOnReady(() ->
        mediaPlayer.setStopTime(mediaPlayer.getMedia().getDuration()));

    // Stop completely at end — do NOT seek or loop, just show replay button
    mediaPlayer.setOnEndOfMedia(() -> {
      mediaPlayer.stop();               // explicit stop prevents any re-trigger
      replayButton.setVisible(true);
    });

    mediaPlayer.setOnError(() ->
        System.err.println("[LoadInstruction] Playback error for " + letter
            + ": " + mediaPlayer.getError().getMessage()));

    mediaPlayer.play();
  }

  /**
   * Seeks to the beginning and replays the current video.
   * Called by the replay button and can also be called programmatically.
   */
  public void replay() {
    if (mediaPlayer != null) {
      replayButton.setVisible(false);
      mediaPlayer.seek(Duration.ZERO);
      // Re-register end handler since stop() clears internal state on some JFX versions
      mediaPlayer.setOnEndOfMedia(() -> {
        mediaPlayer.stop();
        replayButton.setVisible(true);
      });
      mediaPlayer.play();
    }
  }

  /**
   * Stops playback and releases all media resources.
   * Must be called when the owning window is closed.
   */
  public void dispose() {
    disposeCurrent();
  }

  // ── Private helpers ───────────────────────────────────────────────────────────

  private void disposeCurrent() {
    if (mediaPlayer != null) {
      mediaPlayer.stop();
      mediaPlayer.dispose();
      mediaPlayer = null;
    }
  }

  private String styleReplay(boolean hover) {
    return "-fx-background-color: " + (hover ? "#505050" : "#3a3a3a") + ";" +
        "-fx-text-fill: "        + (hover ? "#ffffff"  : "#dddddd") + ";" +
        "-fx-background-radius: 6;" +
        "-fx-cursor: hand;";
  }
}