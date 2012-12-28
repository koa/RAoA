package ch.bergturbenthal.image.data.model.state;

import lombok.Data;

@Data
public class Progress {
  private String progressId;
  private int stepCount;
  private int currentStepNr;
  private String progressDescription;
  private String currentStepDescription;
  private ProgressType type;
}
