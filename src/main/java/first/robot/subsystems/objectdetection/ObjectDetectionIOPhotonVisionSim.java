package first.robot.subsystems.objectdetection;

import java.util.function.Supplier;
import org.photonvision.estimation.TargetModel;
import org.photonvision.simulation.PhotonCameraSim;
import org.photonvision.simulation.SimCameraProperties;
import org.photonvision.simulation.VisionSystemSim;
import org.photonvision.simulation.VisionTargetSim;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.geometry.Transform3d;
import org.wpilib.math.util.Units;

/**
 * Simulated object detection camera with a few game pieces on the field, so driver practice in
 * simulation can use the "drive to game piece" assist.
 */
public class ObjectDetectionIOPhotonVisionSim extends ObjectDetectionIOPhotonVision {
  /** Placeholder game piece: a 7 inch ball. Replace with the BIOCORE game piece after kickoff. */
  private static final TargetModel GAME_PIECE_MODEL = new TargetModel(Units.inchesToMeters(7.0));

  private static final double[][] PIECE_LOCATIONS = {
    {2.9, 4.0}, {2.9, 5.5}, {2.9, 2.5}, {8.3, 4.0}, {8.3, 1.5}, {8.3, 6.5}
  };

  private final VisionSystemSim visionSim = new VisionSystemSim("objects");
  private final Supplier<Pose2d> poseSupplier;

  public ObjectDetectionIOPhotonVisionSim(
      String cameraName, Transform3d robotToCamera, Supplier<Pose2d> poseSupplier) {
    super(cameraName);
    this.poseSupplier = poseSupplier;

    for (double[] location : PIECE_LOCATIONS) {
      visionSim.addVisionTargets(
          "gamepiece",
          new VisionTargetSim(
              new Pose3d(
                  location[0],
                  location[1],
                  ObjectDetectionConstants.GAME_PIECE_CENTER_HEIGHT,
                  Rotation3d.ZERO),
              GAME_PIECE_MODEL,
              0,
              -1));
    }

    var properties =
        new SimCameraProperties()
            .setCalibration(640, 480, Rotation2d.fromDegrees(75.0))
            .setFPS(30.0)
            .setAvgLatencyMs(35.0)
            .setLatencyStdDevMs(5.0);
    visionSim.addCamera(new PhotonCameraSim(camera, properties), robotToCamera);
  }

  @Override
  public void updateInputs(ObjectDetectionIOInputs inputs) {
    visionSim.update(poseSupplier.get());
    super.updateInputs(inputs);
  }
}
