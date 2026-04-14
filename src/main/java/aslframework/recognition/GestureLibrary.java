package aslframework.recognition;

import aslframework.model.GestureDefinition;
import aslframework.model.StaticGestureDefinition;
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
 * Each JSON file represents one ASL letter (A-Z) and contains 21 hand landmark coordinates
 * captured via MediaPipe.
 *
 * <p>For each letter, multiple rotated variants are generated at load time to improve
 * recognition robustness against wrist rotation variation.</p>
 *
 * <p>Reference data files are expected at {@code scripts/reference_data/<LETTER>.json}</p>
 */
public class GestureLibrary {

  private static final String REFERENCE_DATA_DIR = "scripts/reference_data";
  private static final Pattern LANDMARK_PATTERN =
      Pattern.compile("\"x\":\\s*([^,\\n]+),\\s*\"y\":\\s*([^,\\n]+),\\s*\"z\":\\s*([^,\\n}]+)");

  private static final double[] ROTATION_ANGLES_DEG = {-15, -10, -5, 0, 5, 10, 15};

  private final Map<String, List<GestureDefinition>> gestures;

  /**
   * Constructs a GestureLibrary by loading all available reference JSON files.
   * and generating rotated variants for each gesture.
   *
   * @throws IOException if the reference data directory cannot be read
   */
  public GestureLibrary() throws IOException {
    this.gestures = new HashMap<>();
    loadAllGestures();
  }

  /**
   * Returns all the variants (original + rotated) for the given letter.
   *
   * @param letter the ASL letter (e.g. "A", "B")
   * @return unmodifiable list of GestureDefinition variants, or null if not loaded
   */
  public List<GestureDefinition> getGestureVariants(String letter) {
    List<GestureDefinition> variants = gestures.get(letter.toUpperCase());
    return variants != null ? Collections.unmodifiableList(variants) :null;
  }

  /**
   * Returns an unmodifiable map of all loaded gesture definitions keyed by letter.
   *
   * @return map of letter to list of GestureDefinition variants
   */
  public Map<String, List<GestureDefinition>> getAllGestures() {
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
   * Loads all gesture JSON files from the reference data directory
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
          List<HandLandmark> baseLandmarks = parseLandmarks(json);
          List<GestureDefinition> variants = generateVariants(letter, baseLandmarks);
          gestures.put(letter, variants);
          System.out.println("Loaded gesture: " + letter + " (" +variants.size() + " variants)");
        } catch (Exception e) {
          System.err.println("Failed to load gesture " + letter + ": " + e.getMessage());
        }
      }
    }
  }

  /**
   * Generates rotated variants of a gesture's landmarks across predefined angles.
   *
   * @param letter          the ASL letter this gesture represents
   * @param baseLandmarks   the original captured landmarks
   * @return  list of GestureDefinition objects, one per rotation angle
   */
  private List<GestureDefinition> generateVariants(String letter, List<HandLandmark> baseLandmarks){
    List<GestureDefinition> variants = new ArrayList<>();

    for (double angleDeg : ROTATION_ANGLES_DEG){
      double angleRad = Math.toRadians(angleDeg);
      List<HandLandmark> rotated = rotateLandmarks(baseLandmarks, angleRad);
      variants.add(new StaticGestureDefinition(letter, rotated));
    }

    return variants;
  }

  /**
   * Rotates a list of landmarks around the centroid of the hand in the x/y plane.
   *
   * @param landmarks the original landmarks to rotate
   * @param angleRad the rotation angle in radians
   * @return a new list of rotated HandLandmark objects
   */

  private List<HandLandmark> rotateLandmarks(List<HandLandmark> landmarks, double angleRad){
    // Compute Centroid
    double cx = landmarks.stream().mapToDouble(HandLandmark::getX).average().orElse(0.5);
    double cy = landmarks.stream().mapToDouble(HandLandmark::getY).average().orElse(0.5);

    double cos = Math.cos(angleRad);
    double sin = Math.sin(angleRad);

    List<HandLandmark> rotated = new ArrayList<>();
    for (HandLandmark lm:landmarks){
      double dx = lm.getX() - cx;
      double dy = lm.getY() - cy;
      double newX = cx + (dx * cos - dy * sin);
      double newY = cy + (dx * sin + dy * cos);
      rotated.add(new HandLandmark(newX, newY, lm.getZ())); // z  unchanged
    }
    return rotated;
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