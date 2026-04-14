package aslframework.model;

import java.util.List;

/**
 * Represents a dynamic ASL gesture defined by a sequence of hand poses over time.
 * Used for motion-based letters such as J and Z.
 * NOTE!!: This class is a stub - full implementation to be completed in a future iteration.
 */
public class DynamicGestureDefinition extends GestureDefinition {

  private final String gestureName;
  private final List<List<HandLandmark>> frameSequence;

  /**
   * Constructs a DynamicGestureDefinition with the given name and frame sequence.
   *
   * @param gestureName   the ASL letter this gesture represents (e.g "J")
   * @param frameSequence ordered list of landmark sets, one per captured frame
   */
  public DynamicGestureDefinition(String gestureName, List<List<HandLandmark>> frameSequence) {
    this.gestureName = gestureName;
    this.frameSequence = frameSequence;
  }

  /**
   * Returns the name of the ASL gesture this definition represents.
   *
   * @return the gesture name (e.g "J", "Z")
   */
  @Override
  public String getGestureName() {
    return gestureName;
  }

  /**
   * Returns DYNAMIC as the gesture type.
   *
   * @return GestureType.DYNAMIC
   */
  @Override
  public GestureType getGestureType() {
    return GestureType.DYNAMIC;
  }

  /**
   * Returns the sequence of landmark frames for this gesture.
   *
   * @return ordered list of landmark sets
   */
  public List<List<HandLandmark>> getFrameSequence() {
    return frameSequence;
  }

  /**
   * Returns a string representation of this gesture definition.
   *
   * @return formatted string with gesture name and frame count
   */
  @Override
  public String toString() {
    return String.format("DynamicGestureDefinition(name=%s, frames=%d)",
        gestureName, frameSequence.size());
  }
}