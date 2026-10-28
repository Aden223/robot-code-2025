package first.robot.subsystems.questnav;

import org.littletonrobotics.junction.AutoLog;
import org.wpilib.math.geometry.Pose3d;

/** Hardware abstraction for the Quest headset, so QuestNav data is logged and replayable. */
public interface QuestNavIO {
  @AutoLog
  public static class QuestNavIOInputs {
    public boolean connected = false;
    public boolean tracking = false;
    public int batteryPercent = -1;
    public int trackingLostCounter = 0;
    public double latencyMs = 0.0;
    public String questNavVersion = "";

    /** Every pose frame received since the last loop, oldest first. */
    public double[] frameTimestamps = new double[] {};

    public Pose3d[] questPoses = new Pose3d[] {};
    public boolean[] frameTracking = new boolean[] {};
  }

  /** Updates the set of loggable inputs. */
  public default void updateInputs(QuestNavIOInputs inputs) {}

  /** Tells the headset that its current pose is {@code questPose} (in field coordinates). */
  public default void setQuestPose(Pose3d questPose) {}
}
