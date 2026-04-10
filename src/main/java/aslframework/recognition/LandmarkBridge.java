package aslframework.recognition;

import aslframework.model.HandLandmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridges the Python MediaPipe landmark extractor to the Java recognition layer.
 * Launches the Python script as a subprocess and reads detected hand landmarks
 * from its stdout as JSON, converting them into a list of {@link HandLandmark} objects.
 *
 * <p>The bridge runs continuously until a hand is detected, then returns the
 * landmarks for that frame. If a camera error occurs, an exception is thrown.</p>
 */
public class LandmarkBridge {

  private static final String SCRIPT_PATH = "scripts/landmark_extractor.py";
  private static final Pattern LANDMARK_PATTERN =
      Pattern.compile("\\{\"x\":\\s*([^,]+),\\s*\"y\":\\s*([^,]+),\\s*\"z\":\\s*([^}]+)\\}");

  private final Process process;
  private final BufferedReader stdout;
  private final BufferedReader stderr;

  /**
   * Constructs a LandmarkBridge and starts the Python landmark extractor subprocess.
   *
   * @throws LandmarkBridgeException if the subprocess fails to start or a camera error occurs
   */
  public LandmarkBridge() throws LandmarkBridgeException {
    try {
      ProcessBuilder pb = new ProcessBuilder("python3", SCRIPT_PATH, "--headless");
      pb.redirectErrorStream(false);
      this.process = pb.start();
      this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
      this.stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
    } catch (IOException e) {
      throw new LandmarkBridgeException("Failed to start landmark extractor: " + e.getMessage(), e);
    }
  }

  /**
   * Reads the next detected hand landmarks from the Python subprocess.
   * Blocks until a hand is detected. Skips stderr lines (MediaPipe info/warnings).
   *
   * @return a list of 21 {@link HandLandmark} objects for the detected hand
   * @throws LandmarkBridgeException if a camera error occurs or the subprocess exits unexpectedly
   */
  public List<HandLandmark> nextLandmarks() throws LandmarkBridgeException {
    try {
      String line;
      while ((line = stdout.readLine()) != null) {
        line = line.trim();
        if (line.startsWith("[")) {
          return parseLandmarks(line);
        }
      }

      // stdout closed - check exit code
      int exitCode = process.waitFor();
      if (exitCode == 2) {
        throw new LandmarkBridgeException("Camera error - could not open camera (exit code 2)");
      }
      throw new LandmarkBridgeException("Landmark extractor exited unexpectedly (exit code " + exitCode + ")");

    } catch (IOException | InterruptedException e) {
      throw new LandmarkBridgeException("Error reading from landmark extractor: " + e.getMessage(), e);
    }
  }

  /**
   * Closes the Python subprocess and releases associated resources.
   */
  public void close() {
    process.destroy();
    try {
      stdout.close();
      stderr.close();
    } catch (IOException e) {
      // best effort cleanup
    }
  }

  /**
   * Parses a JSON array string of landmark objects into a list of HandLandmark instances.
   *
   * @param json the raw JSON string from the Python script
   * @return list of parsed HandLandmark objects
   * @throws LandmarkBridgeException if the JSON cannot be parsed
   */
  private List<HandLandmark> parseLandmarks(String json) throws LandmarkBridgeException {
    List<HandLandmark> landmarks = new ArrayList<>();
    Matcher matcher = LANDMARK_PATTERN.matcher(json);

    while (matcher.find()) {
      try {
        double x = Double.parseDouble(matcher.group(1).trim());
        double y = Double.parseDouble(matcher.group(2).trim());
        double z = Double.parseDouble(matcher.group(3).trim());
        landmarks.add(new HandLandmark(x, y, z));
      } catch (NumberFormatException e) {
        throw new LandmarkBridgeException("Failed to parse landmark coordinates: " + e.getMessage(), e);
      }
    }

    if (landmarks.isEmpty()) {
      throw new LandmarkBridgeException("No landmarks found in JSON output: " + json);
    }

    return landmarks;
  }

  /**
   * Exception thrown when the landmark bridge encounters an unrecoverable error.
   */
  public static class LandmarkBridgeException extends Exception {

    /**
     * Constructs a LandmarkBridgeException with the given message.
     *
     * @param message description of the error
     */
    public LandmarkBridgeException(String message) {
      super(message);
    }

    /**
     * Constructs a LandmarkBridgeException with the given message and cause.
     *
     * @param message description of the error
     * @param cause   the underlying exception
     */
    public LandmarkBridgeException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}