package aslframework;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Loads local configuration from {@code config.properties} at the project root.
 *
 * <p>{@code config.properties} is listed in {@code .gitignore} so each developer
 * maintains their own copy with paths specific to their machine. A template is
 * provided at {@code config.properties.template}.
 *
 * <p>If a key is missing or the file cannot be read, a clear error is thrown
 * at startup rather than failing silently later.
 */
public class ConfigLoader {

  private static final String CONFIG_FILE = "config.properties";
  private final Properties props = new Properties();

  /**
   * Constructs a {@code ConfigLoader} and reads {@code config.properties}
   * from the current working directory (project root when run via Maven).
   *
   * @throws RuntimeException if the file is missing or cannot be read
   */
  public ConfigLoader() {
    File file = new File(CONFIG_FILE);
    if (!file.exists()) {
      throw new RuntimeException(
          "config.properties not found at: " + file.getAbsolutePath() + "\n" +
              "Copy config.properties.template to config.properties and fill in your paths."
      );
    }
    try (FileInputStream fis = new FileInputStream(file)) {
      props.load(fis);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read config.properties: " + e.getMessage(), e);
    }
  }

  /**
   * Returns the absolute path to the OpenCV native library.
   *
   * @return value of {@code opencv.lib.path}
   */
  public String getOpenCvLibPath() {
    return require("opencv.lib.path");
  }

  /**
   * Returns the absolute path to the instruction video directory.
   *
   * @return value of {@code video.dir}
   */
  public String getVideoDir() {
    return require("video.dir");
  }

  /**
   * Returns the camera URL for IP camera streaming, or null if not set.
   * When null, the default webcam (index 0) will be used.
   *
   * @return value of {@code camera.url}, or null if not configured
   */
  public String getCameraUrl() {
    String value = props.getProperty("camera.url");
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  // ── Private ─────────────────────────────────────────────────────────────────────────────

  private String require(String key) {
    String value = props.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new RuntimeException(
          "Missing required property '" + key + "' in config.properties"
      );
    }
    return value.trim();
  }
}