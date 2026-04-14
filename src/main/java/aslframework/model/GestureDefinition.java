package aslframework.model;

/**
 * Abstract base class representing an ASL gesture definition.
 * Subclasses define either a static hand pose or a dynamic motion sequence.
 */
public abstract class GestureDefinition {

  /**
   * Returns the name of the ASL gesture (e.g. "A", "J").
   *
   * @return the gesture name
   */
  public abstract String getGestureName();

  /**
   * Returns the type of this gesture - static or dynamic.
   *
   * @return the GestureType
   */
  public abstract GestureType getGestureType();
}