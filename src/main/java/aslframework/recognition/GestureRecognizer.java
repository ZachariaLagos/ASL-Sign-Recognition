package java.aslframework.recognition;

import java.aslframework.core.GestureDefinition;

/**
 * Contract for all gesture-recognition backends.
 *
 * <p>Two implementations are currently provided:
 * <ul>
 *   <li>{@link TemplateMatchRecognizer} – lightweight template-matching approach</li>
 *   <li>{@link MediaPipeRecognizer} – high-accuracy MediaPipe-based approach</li>
 * </ul>
 *
 * <p>Implementations are injected into {@link java.aslframework.game.LearningSession}
 * via constructor, making it straightforward to swap recognition backends at runtime.
 */
public interface GestureRecognizer {

  /**
   * Identifies the most likely gesture present in the given camera frame.
   *
   * @param frame raw image bytes captured from the camera
   * @return the gesture ID of the best match (e.g. {@code "thumbs_up"})
   */
  String recognize(byte[] frame);

  /**
   * Computes how closely the pose in {@code frame} matches the
   * {@code target} gesture's reference pose.
   *
   * @param frame  raw image bytes captured from the camera
   * @param target the {@link GestureDefinition} to compare against
   * @return similarity score in {@code [0.0, 1.0]}, where {@code 1.0} is a
   *         perfect match
   */
  double getAccuracy(byte[] frame, GestureDefinition target);
}
