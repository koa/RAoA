package ch.bergturbenthal.raoa.data.model;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(suppressConstructorProperties = true)
@NoArgsConstructor
public class CreateAlbumRequest {
  private String[] pathComps;
  private Date autoAddDate;
}
