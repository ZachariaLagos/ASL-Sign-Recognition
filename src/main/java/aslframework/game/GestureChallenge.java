package aslframework.game;

import aslframework.model.GestureDefinition;
import java.util.List;

public class GestureChallenge {
  private List<GestureDefinition> target;
  private int difficultyLevel;

  public GestureChallenge(List<GestureDefinition> target, int difficultyLevel) {
    this.target = target;
    this.difficultyLevel = difficultyLevel;
  }

  public List<GestureDefinition> getTarget() {
    return target;
  }

  public int getDifficultyLevel() {
    return difficultyLevel;
  }
}