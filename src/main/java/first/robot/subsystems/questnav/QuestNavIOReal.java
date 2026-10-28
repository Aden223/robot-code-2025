package first.robot.subsystems.questnav;

import gg.questnav.questnav.PoseFrame;
import gg.questnav.questnav.QuestNav;
import org.wpilib.math.geometry.Pose3d;

/** QuestNavIO for a real Meta Quest 3S running the QuestNav app, connected over Ethernet. */
public class QuestNavIOReal implements QuestNavIO {
  private final QuestNav questNav = new QuestNav();

  public QuestNavIOReal() {
    // This project vendors a 2027 build of the robot library while the headset may run a 2026
    // release; the NT4 protocol is the same, so silence the version mismatch warning.
    questNav.setVersionCheckEnabled(false);
  }

  @Override
  public void updateInputs(QuestNavIOInputs inputs) {
    // Must run every loop: processes command responses (pose resets) from the headset
    questNav.commandPeriodic();

    inputs.connected = questNav.isConnected();
    inputs.tracking = questNav.isTracking();
    inputs.batteryPercent = questNav.getBatteryPercent().orElse(-1);
    inputs.trackingLostCounter = questNav.getTrackingLostCounter().orElse(0);
    inputs.latencyMs = questNav.getLatency();
    inputs.questNavVersion = questNav.getQuestNavVersion();

    PoseFrame[] frames = questNav.getAllUnreadPoseFrames();
    if (inputs.frameTimestamps.length != frames.length) {
      inputs.frameTimestamps = new double[frames.length];
      inputs.questPoses = new Pose3d[frames.length];
      inputs.frameTracking = new boolean[frames.length];
    }
    for (int i = 0; i < frames.length; i++) {
      inputs.frameTimestamps[i] = frames[i].dataTimestamp();
      inputs.questPoses[i] = frames[i].questPose3d();
      inputs.frameTracking[i] = frames[i].isTracking();
    }
  }

  @Override
  public void setQuestPose(Pose3d questPose) {
    questNav.setPose(questPose);
  }
}
