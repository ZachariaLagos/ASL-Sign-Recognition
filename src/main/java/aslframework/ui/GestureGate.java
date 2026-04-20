package aslframework.ui;

/**
 * Gates incoming gesture detections by a minimum confidence threshold.
 *
 * <p>Only detections whose confidence score meets or exceeds
 * {@link #CONFIDENCE_GATE} are considered valid and passed through.
 * Everything below is treated as noise — random hand positions, partial
 * occlusions, or ambiguous poses that should not affect the hold timer
 * or scoring logic.
 *
 * <p>This gate is deliberately separate from the pass/fail threshold used
 * in scoring ({@code 0.8}). The gate only asks "is this a recognisable
 * gesture at all?" — not "is it the correct gesture?".
 *
 * <p>Usage:
 * <pre>{@code
 * GestureGate gate = new GestureGate();
 * if (gate.passes(confidence)) {
 *     ui.updateDetection(letter, confidence);
 * }
 * }</pre>
 */
public class GestureGate {

  /**
   * Minimum cosine similarity score required for a detection to be
   * considered a valid hand gesture. Detections below this value are
   * discarded as noise.
   */
  public static final double CONFIDENCE_GATE = 0.95;

  /**
   * Returns {@code true} if the given confidence score meets or exceeds
   * the gate threshold, meaning the detection is a valid gesture.
   *
   * @param confidence cosine similarity score in [0.0, 1.0]
   * @return {@code true} if the gesture should be processed, {@code false} if it should be ignored
   */
  public boolean passes(double confidence) {
    return confidence >= CONFIDENCE_GATE;
  }

  /**
   * Returns the gate threshold.
   *
   * @return the minimum confidence score required to pass
   */
  public double getThreshold() {
    return CONFIDENCE_GATE;
  }
}