package aslframework.recognition;

import aslframework.model.GestureDefinition;
import aslframework.model.StaticGestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;

import java.util.List;

/**
 * A {@link GestureRecognizer} implementation that compares hand landmark positions
 * using cosine similarity to compute a confidence score.
 *
 * <p>User landmarks are normalized relative to the wrist before comparison.
 * Reference landmarks are pre-normalized at load time by {@link GestureLibrary}.</p>
 *
 * <p>For each recognition attempt, the 21 MediaPipe hand landmarks are treated as
 * a 63-dimensional vector (x, y, z per landmark). Cosine similarity between the
 * user vector and each reference variant vector is computed. The highest similarity
 * across all variants is returned as the confidence score in [0.0, 1.0].</p>
 *
 */
public class MediaPipeRecognizer implements GestureRecognizer {

  private static final int LANDMARK_COUNT = 21;

  /**
   * Compares the user's hand landmarks against all target gesture variants
   * and returns the highest confidence score found.
   *
   * @param userLandmarks the list of 21 hand landmarks detected from the user's hand
   * @param targetGesture the reference gesture variants to compare against
   * @return a RecognitionResult containing the confidence score and closest match
   * @throws IllegalArgumentException if the landmark list does not contain exactly 21 landmarks
   */
  @Override
  public RecognitionResult recognize(List<HandLandmark> userLandmarks,
      List<GestureDefinition> targetGesture) {
    double            bestScore  = 0.0;
    GestureDefinition bestTarget = targetGesture.get(0);

    List<HandLandmark> normalizedUser = LandmarkUtils.normalize(userLandmarks);

    for (GestureDefinition target : targetGesture) {
      if (target instanceof StaticGestureDefinition) {
        StaticGestureDefinition staticTarget = (StaticGestureDefinition) target;
        List<HandLandmark> referenceLandmarks = staticTarget.getReferenceLandmarks();

        if (normalizedUser.size() != LANDMARK_COUNT
            || referenceLandmarks.size() != LANDMARK_COUNT) {
          throw new IllegalArgumentException(
              "Both landmark lists must contain exactly " + LANDMARK_COUNT + " points. "
                  + "Got user=" + normalizedUser.size()
                  + ", reference=" + referenceLandmarks.size());
        }

        double score = cosineSimilarity(normalizedUser, referenceLandmarks);
        if (score > bestScore) {
          bestScore  = score;
          bestTarget = target;
        }
      }
      // future: else if (target instanceof DynamicGestureDefinition) { ... }
    }

    return new RecognitionResult(bestScore, bestTarget);
  }

  /**
   * Computes the cosine similarity between two sets of 21 hand landmarks,
   * treating them as a single 63-dimensional vector (x, y, z per landmark).
   *
   * <p>Cosine similarity = dot(A, B) / (|A| * |B|)
   * Result is in [-1.0, 1.0] but practically [0.0, 1.0] for normalized poses.
   * A value of 1.0 means identical hand shape; 0.0 means orthogonal.</p>
   *
   * @param a the first landmark list (user)
   * @param b the second landmark list (reference)
   * @return cosine similarity in [0.0, 1.0]
   */
  private double cosineSimilarity(List<HandLandmark> a, List<HandLandmark> b) {
    double dotProduct = 0.0;
    double normA      = 0.0;
    double normB      = 0.0;

    for (int i = 0; i < LANDMARK_COUNT; i++) {
      double ax = a.get(i).getX(), ay = a.get(i).getY(), az = a.get(i).getZ();
      double bx = b.get(i).getX(), by = b.get(i).getY(), bz = b.get(i).getZ();

      dotProduct += ax * bx + ay * by + az * bz;
      normA      += ax * ax + ay * ay + az * az;
      normB      += bx * bx + by * by + bz * bz;
    }

    // Guard against zero vectors
    if (normA < 1e-9 || normB < 1e-9) return 0.0;

    double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));

    // Clamp to [0, 1] - negative similarity means completely wrong gesture
    return Math.max(0.0, similarity);
  }
}