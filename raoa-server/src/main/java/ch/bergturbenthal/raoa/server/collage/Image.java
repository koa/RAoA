package ch.bergturbenthal.raoa.server.collage;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import lombok.Data;

@Data
public class Image implements Rectangle {
	private final File file;
	private final int height;
	private double scale = 1;
	private final int width;

	@Override
	public void align() {
	}

	@Override
	public void collectAllRectangles(final List<Rectangle> list, final int generationSkip) {
		list.add(this);
	}

	@Override
	public boolean contains(final Rectangle rect) {
		return rect == this;
	}

	@Override
	public int deepestLevel(final int startLevel) {
		return startLevel;
	}

	@Override
	public void drawInto(final Graphics2D targetImage, final int x, final int y) throws IOException {
		final BufferedImage inputImage = ImageIO.read(file);
		final AffineTransform transform = AffineTransform.getTranslateInstance(x, y);
		transform.concatenate(AffineTransform.getScaleInstance(scale, scale));
		targetImage.drawImage(inputImage, transform, null);
	}

	@Override
	public void exchange(final Rectangle r1, final Rectangle r2) {
		// do nothing
	}

	@Override
	public Rectangle findBestFitness(final Fitness fitness, final Rectangle ignoreCandidate, final int curentLevel, final int fromLevel, final int toLevel) {
		if (fromLevel >= curentLevel && toLevel < curentLevel) {
			return this;
		}
		return null;
	}

	@Override
	public Rectangle getReplacedCopy(final Rectangle r1, final Rectangle r2) {
		return new Image(file, height, width);
	}

	@Override
	public double getScaledHeight() {
		return height * scale;
	}

	@Override
	public double getScaledWidth() {
		return width * scale;
	}

	@Override
	public int levelOf(final Rectangle rectangle, final int startLevel) {
		return rectangle == this ? startLevel : -1;
	}

	@Override
	public void scale(final double factor) {
		scale *= factor;
	}

}