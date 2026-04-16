package aslframework.ui;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.LandmarkBridge;
import aslframework.recognition.MediaPipeRecognizer;

import javafx.application.Platform;

import java.util.Map;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs gesture recognition on a background daemon thread and publishes
 * results back to the JavaFX Application Thread via a callback.
 *
 * <p>Person C owns this class. It is the glue between Person A's
 * {@link MediaPipeRecognizer} / {@link LandmarkBridge} and the UI screens.
 * Neither {@link PracticeScreen} nor {@link BattleScreen} interact with
 * the recognizer or bridge directly — they only call {@link #start} and
 * {@link #stop}.
 *
 * <p>Usage:
 * <pre>{@code
 * RecognitionService service = new RecognitionService(library,
 *     (letter, confidence) -> {
 *         // called on FX thread every ~frame
 *     });
 * service.start();
 * // later…
 * service.stop();
 * }</pre>
 */
public class RecognitionService {

  private final GestureLibrary              library;
  private final BiConsumer<String, Double>  onResult;   // (bestLetter, confidence)
  private final AtomicBoolean               running = new AtomicBoolean(false);
  private Thread                            thread;

  /**
   * Constructs a {@code RecognitionService}.
   *
   * @param library  the loaded gesture library used for matching
   * @param onResult callback invoked on the FX thread with (letter, confidence);
   *                 letter is {@code "–"} when no hand is detected
   */
  public RecognitionService(GestureLibrary library,
      BiConsumer<String, Double> onResult) {
    this.library  = library;
    this.onResult = onResult;
  }

  /**
   * Starts the background recognition thread.
   * Safe to call multiple times — stops any existing thread first.
   */
  public void start() {
    stop();
    running.set(true);

    thread = new Thread(() -> {
      MediaPipeRecognizer recognizer = new MediaPipeRecognizer();
      LandmarkBridge bridge = null;

      try {
        bridge = new LandmarkBridge();

        while (running.get()) {
          List<HandLandmark> landmarks = bridge.nextLandmarks();

          String bestLetter = "–";
          double bestScore  = 0.0;

          for (Map.Entry<String, List<GestureDefinition>> entry
              : library.getAllGestures().entrySet()) {
            RecognitionResult result = recognizer.recognize(landmarks, entry.getValue());
            if (result.getConfidenceScore() > bestScore) {
              bestScore  = result.getConfidenceScore();
              bestLetter = entry.getKey();
            }
          }

          final String letter = bestLetter;
          final double score  = bestScore;
          Platform.runLater(() -> onResult.accept(letter, score));
        }

      } catch (LandmarkBridge.LandmarkBridgeException ex) {
        final String msg = ex.getMessage();
        Platform.runLater(() -> onResult.accept("ERROR:" + msg, 0.0));
      } finally {
        if (bridge != null) bridge.close();
      }
    });

    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Signals the background thread to stop and interrupts it.
   * Safe to call when already stopped.
   */
  public void stop() {
    running.set(false);
    if (thread != null) {
      thread.interrupt();
      thread = null;
    }
  }

  /** Returns {@code true} if the service is currently running. */
  public boolean isRunning() {
    return running.get();
  }
}