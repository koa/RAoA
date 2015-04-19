package ch.bergturbenthal.raoa.server.collage;

import java.awt.Graphics2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class Node implements Rectangle {
	@NonNull
	private Orientation orientation;
	@NonNull
	private List<Rectangle> subNodes;

	@Override
	public void align() {
		for (final Rectangle subNode : subNodes) {
			subNode.align();
		}
		if (orientation == Orientation.HORIZONTAL) {
			final double firstHeight = subNodes.get(0).getScaledHeight();
			for (final Rectangle node : subNodes.subList(1, subNodes.size())) {
				node.scale(firstHeight / node.getScaledHeight());
			}
		} else {
			final double firstWidth = subNodes.get(0).getScaledWidth();
			for (final Rectangle node : subNodes.subList(1, subNodes.size())) {
				node.scale(firstWidth / node.getScaledWidth());
			}
		}

	}

	@Override
	public void collectAllRectangles(final List<Rectangle> list, final int generationSkip) {
		if (generationSkip <= 0) {
			list.add(this);
		}
		for (final Rectangle rectangle : subNodes) {
			rectangle.collectAllRectangles(list, generationSkip - 1);
		}
	}

	@Override
	public boolean contains(final Rectangle rect) {
		if (this == rect) {
			return true;
		}
		for (final Rectangle subNode : subNodes) {
			if (subNode.contains(rect)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int deepestLevel(final int startLevel) {
		int deepestLevel = startLevel;
		for (final Rectangle rect : subNodes) {
			final int level = rect.deepestLevel(startLevel + 1);
			if (level > deepestLevel) {
				deepestLevel = level;
			}
		}
		return deepestLevel;
	}

	@Override
	public void drawInto(final Graphics2D targetImage, final int x, final int y) throws IOException {
		if (orientation == Orientation.HORIZONTAL) {
			int currentX = x;
			for (final Rectangle rect : subNodes) {
				rect.drawInto(targetImage, currentX, y);
				currentX += rect.getScaledWidth();
			}
		} else {
			int currentY = y;
			for (final Rectangle rect : subNodes) {
				rect.drawInto(targetImage, x, currentY);
				currentY += rect.getScaledHeight();
			}
		}

	}

	@Override
	public void exchange(final Rectangle r1, final Rectangle r2) {
		for (int i = 0; i < subNodes.size(); i++) {
			final Rectangle rect = subNodes.get(i);
			rect.exchange(r1, r2);
			if (rect == r1) {
				subNodes.set(i, r2);
			} else if (rect == r2) {
				subNodes.set(i, r1);
			}
		}
	}

	@Override
	public Rectangle findBestFitness(final Fitness fitness, final Rectangle ignoreCandidate, final int currentLevel, final int fromLevel, final int toLevel) {
		if (this == ignoreCandidate) {
			return null;
		}
		Rectangle bestCandidate = (!contains(ignoreCandidate) && currentLevel >= fromLevel && currentLevel < toLevel) ? this : null;
		double bestFitness = fitness.calcFitness(this);
		for (final Rectangle rect : subNodes) {
			final Rectangle deepFitnessRectangle = rect.findBestFitness(fitness, ignoreCandidate, currentLevel + 1, fromLevel, toLevel);
			if (deepFitnessRectangle != null && ignoreCandidate != deepFitnessRectangle) {
				final double deepFitness = fitness.calcFitness(deepFitnessRectangle);
				if (bestCandidate == null || deepFitness > bestFitness) {
					bestCandidate = deepFitnessRectangle;
					bestFitness = deepFitness;
				}
			}
		}
		return bestCandidate;
	}

	@Override
	public Rectangle getReplacedCopy(final Rectangle r1, final Rectangle r2) {
		final List<Rectangle> x = new ArrayList<Rectangle>(subNodes.size());
		for (int i = 0; i < subNodes.size(); i++) {
			final Rectangle rect = subNodes.get(i);
			if (rect == r1) {
				x.add(r2);
			} else if (rect == r2) {
				x.add(r1);
			} else {
				x.add(rect.getReplacedCopy(r1, r2));
			}
		}
		return new Node(orientation, x);
	}

	@Override
	public double getScaledHeight() {
		if (orientation == Orientation.HORIZONTAL) {
			return subNodes.get(0).getScaledHeight();
		} else {
			double height = 0;
			for (final Rectangle rect : subNodes) {
				height += rect.getScaledHeight();
			}
			return height;
		}
	}

	@Override
	public double getScaledWidth() {
		if (orientation == Orientation.HORIZONTAL) {
			double width = 0;
			for (final Rectangle rect : subNodes) {
				width += rect.getScaledWidth();
			}
			return width;
		} else {
			return subNodes.get(0).getScaledWidth();
		}
	}

	@Override
	public int levelOf(final Rectangle rectangle, final int startLevel) {
		if (rectangle == this) {
			return startLevel;
		}
		for (final Rectangle rect : subNodes) {
			final int level = rect.levelOf(rectangle, startLevel + 1);
			if (level >= 0) {
				return level;
			}
		}
		return -1;
	}

	@Override
	public void scale(final double factor) {
		for (final Rectangle rect : subNodes) {
			rect.scale(factor);
		}
	}

}