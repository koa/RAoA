package ch.bergturbenthal.image.data.model.state;

import java.util.ArrayList;
import java.util.Collection;

import lombok.Data;

@Data
public class ServerState {
  private Collection<Progress> progress = new ArrayList<Progress>();
  private Collection<Issue> issues = new ArrayList<Issue>();
}
