package ch.bergturbenthal.image.data.model.state;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Progress {
  private String progressId;
  private int stepCount;
  private int currentStepNr;
  private String progressDescription;
  private String currentStepDescription;
  private ProgressType type;
}
