package aslframework.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the HandLandmark class
 * Verifies correct construction, getters, edge cases and string formatting
 */

public class HandLandMarkTest {

  // Testing Constructors and Getters

  @Test
  void testConstructorSetsCoordinatesCorrectly(){
    HandLandmark landmark = new HandLandmark(0.25, 0.5, 0.75);

    assertEquals(0.25, landmark.getX());
    assertEquals(0.50, landmark.getY());
    assertEquals(0.75, landmark.getZ());

  }

  // Edge cases
  @Test
  void testAllZeroCoordinates() {
    HandLandmark landmark = new HandLandmark(0.0, 0.0, 0.0);

    assertEquals(0.0, landmark.getX());
    assertEquals(0.0, landmark.getY());
    assertEquals(0.0, landmark.getZ());
  }


  @Test
  void testBoundaryValues() {
    HandLandmark landmark = new HandLandmark(1.0, 1.0, 1.0);

    assertEquals(1.0, landmark.getX());
    assertEquals(1.0, landmark.getY());
    assertEquals(1.0, landmark.getZ());
  }

  @Test
  void testNegativeValuesStoredAsIs() {HandLandmark landmark = new HandLandmark(-0.5, -0.3, -0.1);

    assertEquals(-0.5, landmark.getX());
    assertEquals(-0.3, landmark.getY());
    assertEquals(-0.1, landmark.getZ());
  }

  // toString test
  @Test
  void testToStringFormat(){
    HandLandmark landmark = new HandLandmark(0.1234, 0.5678, 0.9999);
    String result = landmark.toString();

    assertEquals("HandLandmark(x=0.1234, y=0.5678, z=0.9999", result);
  }

  @Test
  void testToStringWithZeros(){
    HandLandmark landmark = new HandLandmark(0, 0, 0);
    String result = landmark.toString();

    assertEquals("HandLandmark(x=0, y=0, z=0", result);
  }

  }
