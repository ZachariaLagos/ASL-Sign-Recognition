package aslframework.game;

import aslframework.model.GestureDefinition;

public class GestureChallenge {
  private GestureDefinition target;
  private int difficultyLevel;

  public GestureChallenge(GestureDefinition target, int difficultyLevel) {
    this.target = target;
    this.difficultyLevel = difficultyLevel;
  }

  public GestureDefinition getTarget() {
    return target;
  }

  public int getDifficultyLevel() {
    return difficultyLevel;
  }
}