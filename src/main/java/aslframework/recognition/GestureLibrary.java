package aslframework.recognition;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads and provides access to ASL reference gesture definitions from JSON files.
 * Each JSON file in the reference data directory represents one ASL letter (A-Z)
 * and contains 21 hand landmark coordinates captured via MediaPipe.
 *
 * <p>Reference data files are expected at: {@code scripts/reference_data/<LETTER>.json}</p>
 */
public class GestureLibrary {

  private static final String REFERENCE_DATA_DIR = "scripts/reference_data";
  private static final Pattern LANDMARK_PATTERN =
      Pattern.compile("\"x\":\\s*([^,\\n]+),\\s*\"y\":\\s*([^,\\n]+),\\s*\"z\":\\s*([^,\\n}]+)");

  private final Map<String, GestureDefinition> gestures;

  /**
   * Constructs a GestureLibrary by loading all available reference JSON files.
   * Letters with missing files are silently skipped.
   *
   * @throws IOException if the reference data directory cannot be read
   */
  public GestureLibrary() throws IOException {
    this.gestures = new HashMap<>();
    loadAllGestures();
  }

  /**
   * Returns the GestureDefinition for the given letter.
   *
   * @param letter the ASL letter (e.g. "A", "B")
   * @return the corresponding GestureDefinition, or null if not loaded
   */
  public GestureDefinition getGesture(String letter) {
    return gestures.get(letter.toUpperCase());
  }

  /**
   * Returns an unmodifiable map of all loaded gesture definitions keyed by letter.
   *
   * @return map of letter to GestureDefinition
   */
  public Map<String, GestureDefinition> getAllGestures() {
    return Collections.unmodifiableMap(gestures);
  }

  /**
   * Returns the number of gesture definitions currently loaded.
   *
   * @return count of loaded gestures
   */
  public int size() {
    return gestures.size();
  }

  /**
   * Loads all gesture JSON files from the reference data directory.
   *
   * @throws IOException if the directory cannot be accessed
   */
  private void loadAllGestures() throws IOException {
    Path dir = Paths.get(REFERENCE_DATA_DIR);

    if (!Files.exists(dir)) {
      throw new IOException("Reference data directory not found: " + REFERENCE_DATA_DIR);
    }

    for (char c = 'A'; c <= 'Z'; c++) {
      String letter = String.valueOf(c);
      Path filePath = dir.resolve(letter + ".json");

      if (Files.exists(filePath)) {
        try {
          String json = new String(Files.readAllBytes(filePath));
          List<HandLandmark> landmarks = parseLandmarks(json);
          gestures.put(letter, new GestureDefinition(letter, landmarks));
          System.out.println("Loaded gesture: " + letter);
        } catch (Exception e) {
          System.err.println("Failed to load gesture " + letter + ": " + e.getMessage());
        }
      }
    }
  }

  /**
   * Parses the landmarks array from a gesture JSON file.
   *
   * @param json the full JSON string of a gesture file
   * @return list of parsed HandLandmark objects
   * @throws IllegalArgumentException if no landmarks can be parsed
   */
  private List<HandLandmark> parseLandmarks(String json) {
    List<HandLandmark> landmarks = new ArrayList<>();
    Matcher matcher = LANDMARK_PATTERN.matcher(json);

    while (matcher.find()) {
      double x = Double.parseDouble(matcher.group(1).trim());
      double y = Double.parseDouble(matcher.group(2).trim());
      double z = Double.parseDouble(matcher.group(3).trim());
      landmarks.add(new HandLandmark(x, y, z));
    }

    if (landmarks.isEmpty()) {
      throw new IllegalArgumentException("No landmarks found in JSON");
    }

    return landmarks;
  }
}