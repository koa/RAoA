package ch.bergturbenthal.raoa.json;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumMetadata {
	private String id;
	private String name;
	private Date timestamp;
}
