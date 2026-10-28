// Copyright (c) 2021-2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package first.robot.subsystems.vision;

import first.robot.util.FieldConstants;
import java.util.function.Supplier;
import org.photonvision.simulation.PhotonCameraSim;
import org.photonvision.simulation.SimCameraProperties;
import org.photonvision.simulation.VisionSystemSim;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Transform3d;
import org.wpilib.system.Timer;

/** IO implementation for physics sim using the PhotonVision simulator. */
public class VisionIOPhotonVisionSim extends VisionIOPhotonVision {
  private static VisionSystemSim visionSim;
  private static double lastUpdateTimestamp = Double.NaN;

  private final Supplier<Pose2d> poseSupplier;
  private final PhotonCameraSim cameraSim;

  /**
   * Creates a new VisionIOPhotonVisionSim.
   *
   * @param name The name of the camera.
   * @param robotToCamera The 3D position of the camera relative to the robot.
   * @param poseSupplier Supplier for the true robot pose to use in simulation.
   */
  public VisionIOPhotonVisionSim(
      String name, Transform3d robotToCamera, Supplier<Pose2d> poseSupplier) {
    super(name, robotToCamera);
    this.poseSupplier = poseSupplier;

    // Initialize vision sim
    if (visionSim == null) {
      visionSim = new VisionSystemSim("apriltags");
      visionSim.addAprilTags(FieldConstants.FIELD);
    }

    // Add sim camera, modeled on an Arducam OV9281 global shutter camera on an Orange Pi 5
    var cameraProperties =
        new SimCameraProperties()
            .setCalibration(1280, 800, Rotation2d.fromDegrees(90.0))
            .setCalibError(0.35, 0.10)
            .setFPS(40.0)
            .setAvgLatencyMs(25.0)
            .setLatencyStdDevMs(5.0);
    cameraSim = new PhotonCameraSim(camera, cameraProperties, FieldConstants.FIELD);
    visionSim.addCamera(cameraSim, robotToCamera);
  }

  @Override
  public void updateInputs(VisionIOInputs inputs) {
    // The shared sim renders every camera at once, so only update it once per robot loop
    double now = Timer.getTimestamp();
    if (now != lastUpdateTimestamp) {
      visionSim.update(poseSupplier.get());
      lastUpdateTimestamp = now;
    }
    super.updateInputs(inputs);
  }
}
