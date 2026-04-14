package aslframework.model;

/**
 * Enumerates the possible types of ASL gestures.
 * STATIC gestures are defined by a single hand pose.
 * DYNAMIC gestures are defined by a sequence of hand poses over time.
 */
public enum GestureType {
  STATIC,
  DYNAMIC
}