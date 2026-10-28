package first.robot.subsystems.questnav;

import java.util.Random;
import java.util.function.Supplier;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.geometry.Transform3d;
import org.wpilib.system.Timer;

/**
 * Simulated Quest 3S. Reports the simulated robot pose through the mount transform, in its own
 * arbitrary VIO frame (like a real headset) until it is seeded, with small noise and slow drift so
 * the drift-correction logic gets exercised in simulation.
 */
public class QuestNavIOSim implements QuestNavIO {
  private static final double NOISE_STD_DEV_METERS = 0.002;
  private static final double DRIFT_METERS_PER_SEC = 0.002;

  private final Supplier<Pose2d> truthPoseSupplier;
  private final Random random = new Random(10978);

  /** Transform from the true field frame into the headset's VIO frame. */
  private Transform3d fieldToVio =
      new Transform3d(1.0, -2.0, 0.0, new Rotation3d(0.0, 0.0, 0.7)); // Unseeded: arbitrary

  private double driftX = 0.0;
  private double lastTimestamp = Timer.getTimestamp();

  public QuestNavIOSim(Supplier<Pose2d> truthPoseSupplier) {
    this.truthPoseSupplier = truthPoseSupplier;
  }

  @Override
  public void updateInputs(QuestNavIOInputs inputs) {
    double now = Timer.getTimestamp();
    driftX += DRIFT_METERS_PER_SEC * (now - lastTimestamp);
    lastTimestamp = now;

    Pose3d truthQuestPose =
        new Pose3d(truthPoseSupplier.get()).transformBy(QuestNavConstants.ROBOT_TO_QUEST);
    Pose3d reported =
        truthQuestPose
            .relativeTo(new Pose3d().transformBy(fieldToVio))
            .transformBy(
                new Transform3d(
                    driftX + random.nextGaussian() * NOISE_STD_DEV_METERS,
                    random.nextGaussian() * NOISE_STD_DEV_METERS,
                    0.0,
                    Rotation3d.ZERO));

    inputs.connected = true;
    inputs.tracking = true;
    inputs.batteryPercent = 100;
    inputs.trackingLostCounter = 0;
    inputs.latencyMs = 10.0;
    inputs.questNavVersion = "sim";
    inputs.frameTimestamps = new double[] {now - 0.010};
    inputs.questPoses = new Pose3d[] {reported};
    inputs.frameTracking = new boolean[] {true};
  }

  @Override
  public void setQuestPose(Pose3d questPose) {
    // Choose the VIO frame so the headset now reports exactly questPose
    Pose3d truthQuestPose =
        new Pose3d(truthPoseSupplier.get()).transformBy(QuestNavConstants.ROBOT_TO_QUEST);
    Pose3d vioOrigin = truthQuestPose.transformBy(new Transform3d(questPose, Pose3d.ZERO));
    fieldToVio = new Transform3d(Pose3d.ZERO, vioOrigin);
    driftX = 0.0;
  }
}
