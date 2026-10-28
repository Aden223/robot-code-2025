package first.robot.subsystems.objectdetection;

import java.util.ArrayList;
import java.util.List;
import org.photonvision.PhotonCamera;

/** Reads object detection results from a PhotonVision camera. */
public class ObjectDetectionIOPhotonVision implements ObjectDetectionIO {
  protected final PhotonCamera camera;

  public ObjectDetectionIOPhotonVision(String cameraName) {
    camera = new PhotonCamera(cameraName);
  }

  @Override
  public void updateInputs(ObjectDetectionIOInputs inputs) {
    inputs.connected = camera.isConnected();

    List<Detection> detections = new ArrayList<>();
    for (var result : camera.getAllUnreadResults()) {
      double timestamp = result.getTimestampSeconds();
      for (var target : result.targets) {
        detections.add(
            new Detection(
                timestamp,
                target.yaw,
                target.pitch,
                target.area,
                target.objDetectId,
                target.objDetectConf));
      }
    }
    inputs.detections = detections.toArray(new Detection[0]);
  }
}
