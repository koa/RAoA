package ch.bergturbenthal.raoa.server.spring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AlbumMetadata {
	private String albumId;
	private String albumTitle;
	private String titleEntry;
}
