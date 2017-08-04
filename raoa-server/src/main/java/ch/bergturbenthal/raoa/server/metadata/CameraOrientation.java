package ch.bergturbenthal.raoa.server.metadata;

import lombok.Getter;

@Getter
public enum CameraOrientation {
	BOTTOM_LEFT(4, false),
	BOTTOM_RIGHT(3, false),
	LEFT_BOTTOM(8, true),
	LEFT_TOP(5, true),
	RIGHT_BOTTOM(7, true),
	RIGHT_TOP(6, true),
	TOP_LEFT(1, false),
	TOP_RIGHT(2, false);
	public static CameraOrientation findById(final int id) {
		for (final CameraOrientation o : CameraOrientation.values()) {
			if (o.getIndex() == id) {
				return o;
			}
		}
		throw new IndexOutOfBoundsException("Unsupported index " + id);
	}

	private final int index;
	private boolean swapDimensions;

	private CameraOrientation(final int index, final boolean swapDimensions) {
		this.index = index;
		this.swapDimensions = swapDimensions;
	}
}
