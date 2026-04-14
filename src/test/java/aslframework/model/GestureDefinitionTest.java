package aslframework.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for the GestureDefinition class
 * Verifies correct construction, immutability guarantees, and string formatting
 */

public class GestureDefinitionTest {
  private List<HandLandmark> landmarks;
  private StaticGestureDefinition gestureA;

  @BeforeEach
  void setUp(){
    landmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++){
      landmarks.add(new HandLandmark(i * 0.01, i * 0.02, i * 0.03));
    }
    gestureA = new StaticGestureDefinition("A", landmarks);
  }

  // Testing Constructors and Getters

  @Test
  void testConstructorSetsGestureNameCorrectly(){
    assertEquals("A", gestureA.getGestureName());
  }

  @Test
  void testConstructorSetsLandmarksCorrectly(){
    assertEquals(21, gestureA.getReferenceLandmarks().size());
    assertEquals(landmarks.get(0).getX(), gestureA.getReferenceLandmarks().get(0).getX());
  }

  // Testing Immutability
  @Test
  void testDefensiveCopy_mutatingOriginalListDoesNotAffectInternal(){
    int originalSize = gestureA.getReferenceLandmarks().size();

    landmarks.add(new HandLandmark(0.9,0.9,0.9));   // Mutate original list

    assertEquals(originalSize, gestureA.getReferenceLandmarks().size());
  }

  @Test
  void testGetterReturnsUnmodifiableList(){
    List<HandLandmark> returned = gestureA.getReferenceLandmarks();

    assertThrows(UnsupportedOperationException.class, () -> {
      returned.add(new HandLandmark(0.5, 0.5, 0.5));
    });
  }

  // Testing toString
  @Test
  void testToStringFormat(){
    String result = gestureA.toString();
    assertEquals("GestureDefinition(name=A, landmarks=21)", result);
  }

}
