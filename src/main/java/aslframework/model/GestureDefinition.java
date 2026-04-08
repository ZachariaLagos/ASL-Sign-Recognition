package aslframework.model;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a reference ASL gesture definition used for recognition matching
 * Each definition holds a gesture name (e.g. "A", "B") and a list of 21 reference
 * hand landmarks that describe the expected hand pose for that gesture
 * This class is immutable; the gesture name and reference landmarks cannot be modified after creation
 */

public class GestureDefinition {

  private final String gestureName;
  private final List<HandLandmark> referenceLandmarks;

  /**
   * Constructs a GestureDefinition with the given name and reference landmarks
   *
   * @param gestureName         the ASL letter this gesture represents (e.g "A")
   * @param referenceLandmarks  the list of 21 reference hand landmarks for this gesture
   */

  public GestureDefinition(String gestureName, List<HandLandmark> referenceLandmarks) {
    this.gestureName = gestureName;
    this.referenceLandmarks = new ArrayList<>(referenceLandmarks);   // defensive copy
  }


  /**
   * Returns the name of the ASL gesture this definition represents
   *
   * @return the gesture name (e.g "A", "B")
   */

  public String getGestureName() {
    return gestureName;
  }

  /**
   * Returns an unmodifiable view of the reference landmarks for this gesture
   *
   * @return an unmodifiable list of 21 reference HandLandmark objects
   */

  public List<HandLandmark> getReferenceLandmarks() {
    return Collections.unmodifiableList(referenceLandmarks);
  }

  /**
   * Returns a string representation of this gesture definition
   *
   * @return formatted string with gesture name and landmark count
   */
  @Override
  public String toString(){
    return String.format("GestureDefinition(name=%s, landmarks=%d)", gestureName, referenceLandmarks.size());
  }
}
