package ch.bergturbenthal.image.data.model.state;

import lombok.Data;

@Data
public class ImportError extends Issue {
  private String importPath;

  @Override
  public boolean canAcknownledge() {
    return true;
  }
}
