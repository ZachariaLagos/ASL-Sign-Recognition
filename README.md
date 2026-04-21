# ASL Recognition Framework

An interactive learning framework for American Sign Language (ASL) hand gesture recognition. Built with Java, Google MediaPipe, and SQLite.

## Contributors

Tavish Hookoom (https://github.com/tavishh) Zihao Li (this account) Chengchi Jiang (https://github.com/Chengchi2002)

## Project Overview

This framework enables real-time ASL letter recognition through an interactive learning application. Users see a target ASL gesture, perform it in front of their webcam, and receive instant accuracy feedback. Designed as an educational tool to help learners master the ASL alphabet through gamified practice sessions.

## Tech Stack

- **Language**: Java 11+
- **Build**: Maven
- **Computer Vision**: Google MediaPipe (hand pose detection)
- **Database**: SQLite 3
- **UI**: Swing / JavaFX
- **Testing**: JUnit 5

## Project Structure
src/main/java/aslframework/
├── core/              (Data models: GestureDefinition, UserProgress, AttemptRecord)
├── game/              (Game logic: LearningSession, ScoringEngine, GestureChallenge)
├── recognition/       (Gesture recognition: GestureRecognizer interface, MediaPipeRecognizer)
├── persistence/       (DAOs: UserProgressDAO, GestureLibraryDAO)
├── ui/                (UI components: GameUI, FeedbackPanel, ProgressDisplay)
└── Main.java          (Application entry point)

## Building & Running

### Prerequisites
- Java 11 or later
- Maven 3.6+
- Webcam (USB camera)

### Build
```bash
mvn clean compile
```

### Run
```bash
mvn exec:java -Dexec.mainClass="aslframework.Main"
```

### Run Tests
```bash
mvn test
```

### Generate Javadoc
```bash
mvn javadoc:javadoc
```

## Team Responsibilities

**Person A - Gesture Recognition**
- Owns: `recognition/GestureRecognizer.java`, `recognition/MediaPipeRecognizer.java`
- Integrates MediaPipe for hand detection and landmark extraction
- Implements gesture matching and accuracy scoring logic
- Unit tests for recognition algorithms with mock hand data

**Person B - Game Progression & Scoring**
- Owns: `game/LearningSession.java`, `game/ScoringEngine.java`, `game/GestureChallenge.java`
- Implements learning progression (gesture sequences, difficulty levels)
- Develops scoring rules and advancement criteria
- Unit tests for game state transitions and scoring calculations

**Person C - Persistence & UI Coordination**
- Owns: `persistence/UserProgressDAO.java`, `ui/GameUI.java`, main game loop
- Handles SQLite database operations using DAO pattern
- Builds UI components for game display and feedback
- Tests for database consistency and UI state management

**All Team Members**
- Develop core data models: `core/GestureDefinition.java`, `core/UserProgress.java`, `core/AttemptRecord.java`
- Write comprehensive Javadoc for all public classes and methods
- Create integration tests that verify end-to-end workflows
- Participate in code reviews and testing strategy

## OOD Principles Demonstrated

- **Polymorphism**: `GestureRecognizer` interface allows multiple recognition implementations
- **Composition**: Game logic composes gesture recognizer and scoring engine
- **Encapsulation**: Data models are immutable; DAOs hide database implementation details
- **Separation of Concerns**: UI, recognition, persistence, and game logic are decoupled
- **Testability**: Core logic is pure Java with no side effects; easy to unit test

## Deliverables

- Working, buildable, and runnable code
- Comprehensive Javadoc on all public classes and methods
- Unit and integration tests with good code coverage
- Clean Git history with meaningful commits
- Final presentation with slides, demo, and code walkthrough
