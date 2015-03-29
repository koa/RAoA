/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

import java.io.Serializable;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Builder;
import lombok.experimental.Wither;

/**
 * TODO: add type comment.
 *
 */
@Value
@AllArgsConstructor(suppressConstructorProperties = true, access = AccessLevel.PRIVATE)
@Builder
public class AlbumState implements Serializable {
	/**
	 *
	 */
	private static final long	serialVersionUID	= -7332581137330437461L;
	@Wither
	private boolean	          isSynced;
	@Wither
	private boolean	          shouldSync;
}
