// Copyright (c) 2021-2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package first.robot.subsystems.vision;

import static first.robot.subsystems.vision.VisionConstants.*;

import first.robot.util.FieldConstants;
import java.util.ArrayList;
import java.util.List;
import org.littletonrobotics.junction.Logger;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.linalg.VecBuilder;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
import org.wpilib.util.Alert;

/**
 * PhotonVision AprilTag localization. Filters each camera's observations and sends the good ones to
 * the pose estimator, scaled by how trustworthy they are (distance and number of tags).
 */
public class Vision extends SubsystemBase {
  private static final Pose3d[] EMPTY_POSES = new Pose3d[0];

  private final VisionConsumer consumer;
  private final TagObservationListener tagListener;
  private final VisionIO[] io;
  private final VisionIOInputsAutoLogged[] inputs;
  private final Alert[] disconnectedAlerts;
  private final String[] logKeys;

  /**
   * @param consumer Receives accepted observations (the drive pose estimator)
   * @param tagListener Also receives accepted observations (QuestNav seeding and drift checks)
   * @param io One IO per camera, in the same order as {@link VisionConstants#CAMERAS}
   */
  public Vision(VisionConsumer consumer, TagObservationListener tagListener, VisionIO... io) {
    this.consumer = consumer;
    this.tagListener = tagListener;
    this.io = io;

    this.inputs = new VisionIOInputsAutoLogged[io.length];
    this.disconnectedAlerts = new Alert[io.length];
    this.logKeys = new String[io.length];
    for (int i = 0; i < io.length; i++) {
      inputs[i] = new VisionIOInputsAutoLogged();
      String name = i < CAMERAS.length ? CAMERAS[i].name() : "Camera" + i;
      logKeys[i] = "Vision/" + name;
      disconnectedAlerts[i] =
          new Alert(
              "Vision", name + "Disconnected", "Vision camera " + name + " is disconnected.",
              Alert.Level.MEDIUM);
    }
  }

  @Override
  public void periodic() {
    for (int i = 0; i < io.length; i++) {
      io[i].updateInputs(inputs[i]);
      Logger.processInputs(logKeys[i], inputs[i]);
    }

    // Initialize logging values
    List<Pose3d> allTagPoses = new ArrayList<>();
    List<Pose3d> allRobotPosesAccepted = new ArrayList<>();
    List<Pose3d> allRobotPosesRejected = new ArrayList<>();

    // Loop over cameras
    for (int cameraIndex = 0; cameraIndex < io.length; cameraIndex++) {
      disconnectedAlerts[cameraIndex].set(!inputs[cameraIndex].connected);

      // Add tag poses
      for (int tagId : inputs[cameraIndex].tagIds) {
        FieldConstants.FIELD.getTagPose(tagId).ifPresent(allTagPoses::add);
      }

      double cameraFactor = cameraIndex < CAMERAS.length ? CAMERAS[cameraIndex].stdDevFactor() : 1.0;

      // Loop over pose observations
      for (var observation : inputs[cameraIndex].poseObservations) {
        boolean singleTag = observation.tagCount() == 1;
        boolean rejectPose =
            observation.tagCount() == 0 // Must have at least one tag
                || (singleTag && observation.ambiguity() > MAX_AMBIGUITY) // Low ambiguity
                || (singleTag && observation.averageTagDistance() > MAX_SINGLE_TAG_DISTANCE)
                || observation.averageTagDistance() > MAX_TAG_DISTANCE
                || Math.abs(observation.pose().getZ()) > MAX_Z_ERROR // Realistic height
                || !FieldConstants.isOnField(observation.pose().toPose2d(), 0.0); // On field

        if (rejectPose) {
          allRobotPosesRejected.add(observation.pose());
          continue;
        }
        allRobotPosesAccepted.add(observation.pose());

        // Calculate standard deviations
        double stdDevFactor =
            observation.averageTagDistance() * observation.averageTagDistance()
                / observation.tagCount()
                * cameraFactor;
        if (singleTag) {
          stdDevFactor *= SINGLE_TAG_STD_DEV_FACTOR;
        }
        double linearStdDev = LINEAR_STD_DEV_BASELINE * stdDevFactor;
        double angularStdDev = ANGULAR_STD_DEV_BASELINE * stdDevFactor;

        // Send vision observation
        Pose2d robotPose = observation.pose().toPose2d();
        consumer.accept(
            robotPose,
            observation.timestamp(),
            VecBuilder.fill(linearStdDev, linearStdDev, angularStdDev));
        tagListener.onAprilTagObservation(robotPose, observation.timestamp(), linearStdDev);
      }
    }

    // Log summary data
    Logger.recordOutput("Vision/Summary/TagPoses", allTagPoses.toArray(EMPTY_POSES));
    Logger.recordOutput(
        "Vision/Summary/RobotPosesAccepted", allRobotPosesAccepted.toArray(EMPTY_POSES));
    Logger.recordOutput(
        "Vision/Summary/RobotPosesRejected", allRobotPosesRejected.toArray(EMPTY_POSES));
  }

  @FunctionalInterface
  public static interface VisionConsumer {
    public void accept(
        Pose2d visionRobotPoseMeters,
        double timestampSeconds,
        Matrix<N3, N1> visionMeasurementStdDevs);
  }

  @FunctionalInterface
  public static interface TagObservationListener {
    public void onAprilTagObservation(Pose2d robotPose, double timestamp, double linearStdDev);
  }
}
