package first.robot.subsystems.questnav;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Rotation2d;

class QuestNavIOSimTest {
  private static final double TOLERANCE = 0.02; // Sim adds a few millimeters of noise

  @BeforeAll
  static void setup() {
    HAL.initialize(); // The simulated Quest reads the robot clock
  }

  @Test
  void seedingMakesTheQuestReportTheSeededPose() {
    Pose2d[] truth = {new Pose2d(2.0, 3.0, Rotation2d.fromDegrees(40.0))};
    QuestNavIOSim io = new QuestNavIOSim(() -> truth[0]);
    QuestNavIOInputsAutoLogged inputs = new QuestNavIOInputsAutoLogged();

    // Seed with the true pose, then move the robot: the Quest must follow in field coordinates
    io.setQuestPose(new Pose3d(truth[0]).transformBy(QuestNavConstants.ROBOT_TO_QUEST));
    truth[0] = new Pose2d(4.5, 1.0, Rotation2d.fromDegrees(-70.0));
    io.updateInputs(inputs);

    Pose2d reportedRobot =
        inputs.questPoses[0].transformBy(QuestNavConstants.ROBOT_TO_QUEST.inverse()).toPose2d();
    assertEquals(truth[0].getX(), reportedRobot.getX(), TOLERANCE);
    assertEquals(truth[0].getY(), reportedRobot.getY(), TOLERANCE);
    assertEquals(
        truth[0].getRotation().getDegrees(), reportedRobot.getRotation().getDegrees(), 0.5);
  }
}
