package ch.bergturbenthal.raoa.server.collage;

import java.awt.Graphics2D;
import java.io.IOException;
import java.util.List;

public interface Rectangle {
	double getScaledWidth();

	double getScaledHeight();

	void scale(final double factor);

	void align();

	void drawInto(final Graphics2D graphics2d, final int x, final int y) throws IOException;

	void exchange(final Rectangle r1, final Rectangle r2);

	Rectangle getReplacedCopy(final Rectangle r1, final Rectangle r2);

	Rectangle findBestFitness(final Fitness fitness, final Rectangle ignoreCandidate, final int curentLevel, final int fromLevel, final int toLevel);

	boolean contains(final Rectangle rect);

	int deepestLevel(final int startLevel);

	int levelOf(final Rectangle rectangle, final int startLevel);

	void collectAllRectangles(final List<Rectangle> list, final int generationSkip);

}