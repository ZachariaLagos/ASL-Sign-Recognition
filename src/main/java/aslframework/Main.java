package aslframework;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.LandmarkBridge;
import aslframework.recognition.MediaPipeRecognizer;
import aslframework.ui.CameraService;
import aslframework.ui.GameUI;
import aslframework.ui.GestureGate;
import aslframework.ConfigLoader;

import javafx.application.Application;

import java.util.List;
import java.util.Map;

/**
 * Main entry point for the ASL Recognition and Learning Platform.
 *
 * <p>Loads the OpenCV native library, launches {@link GameUI}, then starts a
 * background recognition loop. The camera feed is handled independently inside
 * {@link GameUI} via {@link CameraService}.</p>
 */
public class Main {

  public static void main(String[] args) throws Exception {
    // Load paths from config.properties — each developer has their own copy
    ConfigLoader config = new ConfigLoader();
    CameraService.loadNativeLibrary(config.getOpenCvLibPath());

    System.out.println("=== ASL Recognition Platform ===");
    System.out.println("Loading gesture library...");

    GestureLibrary library = new GestureLibrary();
    System.out.println("Loaded " + library.size() + " gestures\n");

    if (library.size() == 0) {
      System.err.println("No reference gestures found. Run collect_reference_data.py first.");
      return;
    }

    // Pass video directory to GameUI before launch
    GameUI.setVideoDir(config.getVideoDir());

    // Launch JavaFX — GameUI.getInstance() becomes non-null once start() returns.
    Thread fxThread = new Thread(() -> Application.launch(GameUI.class, args));
    fxThread.setDaemon(false);
    fxThread.start();

    while (GameUI.getInstance() == null) {
      Thread.sleep(50);
    }
    GameUI ui = GameUI.getInstance();

    MediaPipeRecognizer recognizer = new MediaPipeRecognizer();
    LandmarkBridge bridge = new LandmarkBridge();
    GestureGate gate = new GestureGate();

    System.out.println("Show a letter to the camera and hold for 1 second. Press Ctrl+C to quit.\n");

    // Recognition loop — hold timer and recording handled inside GameUI
    Thread recognitionThread = new Thread(() -> {
      String lastLetter = "";
      try {
        while (!Thread.currentThread().isInterrupted()) {
          List<HandLandmark> landmarks = bridge.nextLandmarks();

          String bestLetter = null;
          double bestScore  = -1;

          for (Map.Entry<String, List<GestureDefinition>> entry : library.getAllGestures().entrySet()) {
            RecognitionResult result = recognizer.recognize(landmarks, entry.getValue());
            if (result.getConfidenceScore() > bestScore) {
              bestScore  = result.getConfidenceScore();
              bestLetter = entry.getKey();
            }
          }

          // Pass landmarks to CameraService so it can draw them on the Mat if enabled
          CameraService cs = ui.getCameraService();
          if (cs != null) cs.setLandmarks(landmarks);

          if (bestLetter != null) {
            if (gate.passes(bestScore)) {
              ui.updateDetection(bestLetter, bestScore);

              if (!bestLetter.equals(lastLetter)) {
                System.out.printf("Letter: %-3s  Confidence: %.1f%%  %s%n",
                    bestLetter, bestScore * 100, bestScore >= 0.8 ? "✓" : "?");
                lastLetter = bestLetter;
              }
            } else {
              ui.showVacant();
            }
          }
        }
      } catch (LandmarkBridge.LandmarkBridgeException e) {
        System.err.println("Camera error: " + e.getMessage());
      } finally {
        bridge.close();
      }
    }, "recognition-loop");

    recognitionThread.setDaemon(true);
    recognitionThread.start();
  }
}