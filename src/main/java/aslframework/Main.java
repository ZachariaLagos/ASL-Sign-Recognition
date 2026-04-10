package aslframework;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.LandmarkBridge;
import aslframework.recognition.MediaPipeRecognizer;

import java.util.List;
import java.util.Map;

/**
 * Main entry point for the ASL Recognition and Learning Platform.
 * Launches a live recognition loop that detects ASL letters in real time
 * using MediaPipe hand landmarks and displays results to the console.
 */
public class Main {

  public static void main(String[] args) throws Exception {
    System.out.println("=== ASL Recognition Platform ===");
    System.out.println("Loading gesture library...");

    GestureLibrary library = new GestureLibrary();
    System.out.println("Loaded " + library.size() + " gestures\n");

    if (library.size() == 0) {
      System.err.println("No reference gestures found. Run collect_reference_data.py first.");
      return;
    }

    MediaPipeRecognizer recognizer = new MediaPipeRecognizer();
    LandmarkBridge bridge = new LandmarkBridge();

    System.out.println("Show a letter to the camera. Press Ctrl+C to quit.\n");

    String lastLetter = "";

    try {
      while (true) {
        List<HandLandmark> landmarks = bridge.nextLandmarks();

        String bestLetter = null;
        double bestScore = -1;

        for (Map.Entry<String, GestureDefinition> entry : library.getAllGestures().entrySet()) {
          RecognitionResult result = recognizer.recognize(landmarks, entry.getValue());
          if (result.getConfidenceScore() > bestScore) {
            bestScore = result.getConfidenceScore();
            bestLetter = entry.getKey();
          }
        }

        if (bestLetter != null && !bestLetter.equals(lastLetter)) {
          String confidence = String.format("%.1f%%", bestScore * 100);
          String match = bestScore >= 0.8 ? "✓" : "?";
          System.out.printf("Letter: %-3s  Confidence: %-8s %s%n",
              bestLetter, confidence, match);
          lastLetter = bestLetter;
        }
      }
    } catch (LandmarkBridge.LandmarkBridgeException e) {
      System.err.println("Camera error: " + e.getMessage());
    } finally {
      bridge.close();
    }
  }
}