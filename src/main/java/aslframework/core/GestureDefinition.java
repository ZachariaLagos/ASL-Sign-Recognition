package java.aslframework.core;

/**
 * Immutable definition of a single ASL gesture.
 *
 * <p>A {@code GestureDefinition} captures everything the recognition engine
 * needs to evaluate a user's pose:
 * <ul>
 *   <li>A unique identifier used throughout the persistence layer</li>
 *   <li>A human-readable name shown in the UI</li>
 *   <li>A reference pose expressed as a flat array of landmark coordinates</li>
 *   <li>A tolerance threshold that controls how strictly the pose must match</li>
 * </ul>
 *
 * <p>All array fields are defensively copied on construction and on access,
 * so instances are safe to share across threads.
 */
public final class GestureDefinition {

  private final String gestureId;
  private final String name;
  private final double[] referencePose;
  private final double tolerance;

  /**
   * Constructs a new {@code GestureDefinition}.
   *
   * @param gestureId     unique identifier for this gesture (e.g. {@code "asl_a"})
   * @param name          display name shown in the UI (e.g. {@code "Letter A"})
   * @param referencePose flat array of landmark coordinates representing the
   *                      ideal pose; the array is copied defensively
   * @param tolerance     maximum allowed deviation from the reference pose
   */
  public GestureDefinition(String gestureId, String name,
      double[] referencePose, double tolerance) {
    this.gestureId = gestureId;
    this.name = name;
    this.referencePose = referencePose.clone(); // defensive copy
    this.tolerance = tolerance;
  }

  /**
   * Returns the unique identifier of this gesture.
   *
   * @return gesture ID string
   */
  public String getGestureId() { return gestureId; }

  /**
   * Returns the human-readable display name of this gesture.
   *
   * @return gesture name
   */
  public String getName() { return name; }

  /**
   * Returns a defensive copy of the reference pose landmark array.
   *
   * @return copy of the reference pose coordinates
   */
  public double[] getReferencePose() { return referencePose.clone(); }

  /**
   * Returns the matching tolerance for this gesture.
   *
   * @return tolerance value
   */
  public double getTolerance() { return tolerance; }
}
