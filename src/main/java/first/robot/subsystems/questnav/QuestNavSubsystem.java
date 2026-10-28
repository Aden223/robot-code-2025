package first.robot.subsystems.questnav;

import static first.robot.subsystems.questnav.QuestNavConstants.*;

import first.robot.subsystems.drive.Drive;
import first.robot.util.FieldConstants;
import first.robot.util.LoggedTunableNumber;
import java.util.Optional;
import org.littletonrobotics.junction.AutoLogOutput;
import org.littletonrobotics.junction.Logger;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.driverstation.RobotState;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Transform2d;
import org.wpilib.math.geometry.Transform3d;
import org.wpilib.math.interpolation.TimeInterpolatableBuffer;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.linalg.VecBuilder;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
import org.wpilib.system.Timer;
import org.wpilib.util.Alert;

/**
 * Fuses the Meta Quest 3S (QuestNav) into the drive pose estimator.
 *
 * <p>How it works:
 *
 * <ol>
 *   <li>The Quest tracks its own motion (visual-inertial odometry) at up to 120 Hz, but in its own
 *       coordinate frame. It must be "seeded" with a field pose before its data is used.
 *   <li>It is seeded whenever the robot pose is hard-reset (auto start, driver reset), or
 *       automatically from a good PhotonVision AprilTag fix when it has never been seeded.
 *   <li>Every Quest frame is converted to a robot pose and fed into the estimator with small
 *       standard deviations, after sanity checks (tracking, timestamp, field bounds, height, jumps).
 *   <li>Good AprilTag fixes are continuously compared with the Quest. If the Quest drifts more than
 *       {@link QuestNavConstants#DRIFT_RESEED_THRESHOLD_METERS}, the pose is re-seeded from tags.
 * </ol>
 */
public class QuestNavSubsystem extends SubsystemBase {
  private static final Transform3d QUEST_TO_ROBOT = ROBOT_TO_QUEST.inverse();
  private static final double MAX_DISAGREEMENT_METERS = 1.0;
  private static final double UNSEED_AFTER_DISAGREEING_SECONDS = 1.0;

  private static final LoggedTunableNumber linearStdDev =
      new LoggedTunableNumber("QuestNav/LinearStdDev", LINEAR_STD_DEV);
  private static final LoggedTunableNumber angularStdDev =
      new LoggedTunableNumber("QuestNav/AngularStdDev", ANGULAR_STD_DEV);

  private final QuestNavIO io;
  private final QuestNavIOInputsAutoLogged inputs = new QuestNavIOInputsAutoLogged();
  private final Drive drive;

  private final Alert disconnectedAlert =
      new Alert("QuestNav", "Disconnected", "QuestNav disconnected.", Alert.Level.HIGH);
  private final Alert notTrackingAlert =
      new Alert(
          "QuestNav", "NotTracking", "QuestNav connected but not tracking.", Alert.Level.HIGH);
  private final Alert lowBatteryAlert =
      new Alert("QuestNav", "LowBattery", "QuestNav battery low.", Alert.Level.MEDIUM);
  private final Alert unseededAlert =
      new Alert(
          "QuestNav",
          "Unseeded",
          "QuestNav not seeded yet: show the robot an AprilTag or reset the pose.",
          Alert.Level.MEDIUM);

  private boolean seeded = false;
  private double lastSeedTimestamp = Double.NEGATIVE_INFINITY;
  private double disagreeingSinceTimestamp = Double.NaN;
  private int reseedCount = 0;

  // Drift tracking against AprilTags
  private final TimeInterpolatableBuffer<Pose2d> questRobotPoseHistory =
      TimeInterpolatableBuffer.createBuffer(1.5);
  private double driftEmaMeters = 0.0;
  private int driftObservations = 0;

  private Pose2d latestQuestRobotPose = Pose2d.ZERO;

  public QuestNavSubsystem(QuestNavIO io, Drive drive) {
    this.io = io;
    this.drive = drive;
    drive.addPoseResetListener(this::seed);
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("QuestNav", inputs);

    double now = Timer.getTimestamp();
    Matrix<N3, N1> stdDevs =
        VecBuilder.fill(linearStdDev.get(), linearStdDev.get(), angularStdDev.get());
    int accepted = 0;
    int rejected = 0;

    for (int i = 0; i < inputs.frameTimestamps.length; i++) {
      double timestamp = inputs.frameTimestamps[i];
      Pose3d robotPose3d = inputs.questPoses[i].transformBy(QUEST_TO_ROBOT);
      Pose2d robotPose = robotPose3d.toPose2d();
      latestQuestRobotPose = robotPose;

      if (!seeded || !inputs.frameTracking[i] || now - lastSeedTimestamp < RESET_SETTLE_SECONDS) {
        continue;
      }
      questRobotPoseHistory.addSample(timestamp, robotPose);

      boolean reject =
          Math.abs(timestamp - now) > MAX_TIMESTAMP_ERROR_SECONDS
              || Math.abs(robotPose3d.getZ()) > MAX_Z_ERROR_METERS
              || !FieldConstants.isOnField(robotPose, FIELD_MARGIN_METERS);

      // A large disagreement with the estimator means the Quest relocalized badly or the frame
      // is wrong. If it persists, un-seed so the next good AprilTag fix re-seeds the headset.
      if (!reject) {
        double disagreement =
            robotPose.getTranslation().getDistance(drive.getPose().getTranslation());
        if (disagreement > MAX_DISAGREEMENT_METERS) {
          reject = true;
          if (Double.isNaN(disagreeingSinceTimestamp)) {
            disagreeingSinceTimestamp = now;
          } else if (now - disagreeingSinceTimestamp > UNSEED_AFTER_DISAGREEING_SECONDS) {
            seeded = false;
            disagreeingSinceTimestamp = Double.NaN;
          }
        } else {
          disagreeingSinceTimestamp = Double.NaN;
        }
      }

      if (reject) {
        rejected++;
        continue;
      }
      drive.addVisionMeasurement(robotPose, timestamp, stdDevs);
      accepted++;
    }

    // Alerts
    disconnectedAlert.set(!inputs.connected);
    notTrackingAlert.set(inputs.connected && !inputs.tracking);
    lowBatteryAlert.set(
        inputs.connected
            && inputs.batteryPercent >= 0
            && inputs.batteryPercent <= LOW_BATTERY_PERCENT);
    unseededAlert.set(inputs.connected && !seeded);

    Logger.recordOutput("QuestNav/RobotPose", latestQuestRobotPose);
    Logger.recordOutput("QuestNav/AcceptedFrames", accepted);
    Logger.recordOutput("QuestNav/RejectedFrames", rejected);
    Logger.recordOutput("QuestNav/DriftEstimateMeters", driftEmaMeters);
    Logger.recordOutput("QuestNav/ReseedCount", reseedCount);
  }

  /**
   * Tells the Quest that the robot is at {@code robotPose} right now. Called automatically when the
   * drive pose is hard-reset.
   */
  public void seed(Pose2d robotPose) {
    io.setQuestPose(new Pose3d(robotPose).transformBy(ROBOT_TO_QUEST));
    seeded = true;
    lastSeedTimestamp = Timer.getTimestamp();
    disagreeingSinceTimestamp = Double.NaN;
    questRobotPoseHistory.clear();
    driftEmaMeters = 0.0;
    driftObservations = 0;
    Logger.recordOutput("QuestNav/LastSeedPose", robotPose);
  }

  /**
   * Called by the AprilTag vision subsystem with every accepted observation. Used to seed the Quest
   * the first time, and to detect and correct Quest drift.
   *
   * @param tagRobotPose Robot pose measured from AprilTags
   * @param timestamp Measurement timestamp in seconds
   * @param tagLinearStdDev The standard deviation the vision subsystem assigned (meters)
   */
  public void onAprilTagObservation(Pose2d tagRobotPose, double timestamp, double tagLinearStdDev) {
    if (tagLinearStdDev > DRIFT_MAX_TAG_STD_DEV || !isSlowEnough()) {
      return;
    }

    if (!seeded) {
      // First good fix: snap the whole estimator (and through the reset listener, the Quest)
      if (inputs.connected && inputs.tracking) {
        drive.setPose(projectToNow(tagRobotPose, timestamp));
        reseedCount++;
      }
      return;
    }

    Optional<Pose2d> questAtTimestamp = questRobotPoseHistory.getSample(timestamp);
    if (questAtTimestamp.isEmpty()) {
      return;
    }
    double error =
        questAtTimestamp.get().getTranslation().getDistance(tagRobotPose.getTranslation());
    driftEmaMeters = driftObservations == 0 ? error : driftEmaMeters + DRIFT_EMA_ALPHA * (error - driftEmaMeters);
    driftObservations++;

    if (driftObservations >= DRIFT_MIN_OBSERVATIONS
        && driftEmaMeters > DRIFT_RESEED_THRESHOLD_METERS
        && !RobotState.isAutonomous()) {
      drive.setPose(projectToNow(tagRobotPose, timestamp));
      reseedCount++;
    }
  }

  /** Moves a past pose measurement forward to now using the estimator's motion since then. */
  private Pose2d projectToNow(Pose2d pastPose, double timestamp) {
    Optional<Pose2d> estimatorThen = drive.getPoseAt(timestamp);
    if (estimatorThen.isEmpty()) {
      return pastPose;
    }
    return pastPose.transformBy(new Transform2d(estimatorThen.get(), drive.getPose()));
  }

  private boolean isSlowEnough() {
    ChassisVelocities velocities = drive.getChassisVelocities();
    return Math.hypot(velocities.vx, velocities.vy) < DRIFT_MAX_ROBOT_SPEED
        && Math.abs(velocities.omega) < Math.PI / 2.0;
  }

  /** Returns true when the Quest is connected, tracking, and seeded (its data is being used). */
  @AutoLogOutput(key = "QuestNav/Healthy")
  public boolean isHealthy() {
    return inputs.connected && inputs.tracking && seeded;
  }

  @AutoLogOutput(key = "QuestNav/Seeded")
  public boolean isSeeded() {
    return seeded;
  }
}
