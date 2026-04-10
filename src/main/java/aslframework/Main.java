package aslframework;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;
import aslframework.recognition.LandmarkBridge;
import aslframework.recognition.MediaPipeRecognizer;

import java.util.List;

public class Main {
  public static void main(String[] args) throws Exception {
    System.out.println("Show your hand to the camera...");

    LandmarkBridge bridge = new LandmarkBridge();
    MediaPipeRecognizer recognizer = new MediaPipeRecognizer();

    // Capture one frame of landmarks
    List<HandLandmark> landmarks = bridge.nextLandmarks();
    System.out.println("Detected " + landmarks.size() + " landmarks!");

    // Use the same landmarks as the reference (should give confidence = 1.0)
    GestureDefinition testGesture = new GestureDefinition("TEST", landmarks);
    RecognitionResult result = recognizer.recognize(landmarks, testGesture);

    System.out.println("Confidence: " + result.getConfidenceScore());
    System.out.println("Match: " + result.isMatch());

    bridge.close();
  }
}