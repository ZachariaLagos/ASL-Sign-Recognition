package aslframework.model;

/**
 * Represents the result of a single gesture recognition attempt
 * Contains a confidence score indicating how closely the user's hand pose matched
 * the target gesture, along with the closest matching gesture definition found
 * This class is immutable; all fields are set at construction and cannot be modified
 */
public class RecognitionResult {

  private static final double MATCH_THRESHOLD = 0.8;
  private final double confidenceScore;
  private final GestureDefinition closestMatch;
  private final boolean isMatch;

  /**
   * Constructs a RecognitionResult with the given confidence score and closest match
   *
   * @param confidenceScore the confidence of the match, between 0.0 and 1.0
   * @param closestMatch    the GestureDefinition that most closely matched the user's hand pose
   */
  public RecognitionResult(double confidenceScore, GestureDefinition closestMatch) {
    this.confidenceScore = confidenceScore;
    this.closestMatch = closestMatch;
    this.isMatch = confidenceScore >= MATCH_THRESHOLD;
  }

  /**
   * Returns the confidence score for this recognition result
   *
   * @return confidence score between 0.0 (no match) and 1.0 (perfect match)
   */
  public double getConfidenceScore() {
    return confidenceScore;
  }

  /**
   * Returns the gesture definition that most closely matched the user's hand pose
   *
   * @return the closest matching GestureDefinition
   */
  public GestureDefinition getClosestMatch() {
    return closestMatch;
  }

  /**
   * Returns whether this result represents a successful gesture match
   * A match is determined by whether the confidence score meets the match threshold
   *
   * @return true if the confidence score is at or above the match threshold
   */
  public boolean isMatch() {
    return isMatch;
  }

  /**
   * Returns a string representation of this recognition result
   *
   * @return formatted string with confidence score, match status, and gesture name
   */
  @Override
  public String toString() {
    return String.format("RecognitionResult(confidence=%.2f, match=%b, gesture=%s)",
        confidenceScore, isMatch, closestMatch.getGestureName());
  }
}