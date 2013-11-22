package ch.bergturbenthal.image.server.testalgorithm;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

import org.junit.Test;

public class TestFillImages {
	private static interface Fitness {
		double calcFitness(final Rectangle image);
	}

	@Data
	private static class Image implements Rectangle {
		private File file;
		private final int height;
		private double scale = 1;
		private final int width;

		@Override
		public void align() {
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

	@Data
	@AllArgsConstructor
	private static class Node implements Rectangle {
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

	private enum Orientation {
		HORIZONTAL, VERTICAL
	}

	private static interface Rectangle {
		double getScaledWidth();

		double getScaledHeight();

		void scale(final double factor);

		void align();

		void drawInto(final Graphics2D graphics2d, final int x, final int y) throws IOException;

		void exchange(final Rectangle r1, final Rectangle r2);

		Rectangle findBestFitness(final Fitness fitness, final Rectangle ignoreCandidate, final int curentLevel, final int fromLevel, final int toLevel);

		boolean contains(final Rectangle rect);

		int deepestLevel(final int startLevel);

		int levelOf(final Rectangle rectangle, final int startLevel);

	}

	Random random = new Random();

	private Orientation inverse(final Orientation orientation) {
		Orientation nextOrientation;
		if (orientation == Orientation.HORIZONTAL) {
			nextOrientation = Orientation.VERTICAL;
		} else {
			nextOrientation = Orientation.HORIZONTAL;
		}
		return nextOrientation;
	}

	private Rectangle makeRect(final List<Image> images, final Orientation orientation, final Orientation strechOrientation, final double strechFactor) {
		if (images.size() == 1) {
			System.out.println("one found");
			return images.get(0);
		}
		final int splitCount;
		if (orientation == strechOrientation) {
			splitCount = (int) Math.round((2 + random.nextDouble() * strechFactor));
		} else {
			splitCount = 2;
		}
		final List<Rectangle> subNodes = new ArrayList<>();
		int currentPos = 0;
		final Orientation nextOrientation = inverse(orientation);
		for (int i = 0; i < splitCount && currentPos < images.size(); i++) {
			final int nextSplit = (int) (currentPos + Math.round(1.0 * (images.size() - currentPos) / (splitCount - i)));
			System.out.println("currentPos: " + currentPos + " splitCount: " + splitCount + " total: " + images.size() + " i: " + i + " nextSplit: " + nextSplit);
			subNodes.add(makeRect(images.subList(currentPos, nextSplit), nextOrientation, strechOrientation, strechFactor));
			currentPos = nextSplit;
		}
		return new Node(orientation, subNodes);
	}

	private List<Image> readImages() throws IOException {
		final List<Image> images = new ArrayList<>();

		final File[] files = new File("/data/heap/data/photos/old/Landschaft/Vorführung Seilbahn 2012-02-18/.servercache").listFiles(new FileFilter() {

			@Override
			public boolean accept(final File pathname) {
				if (!pathname.isFile()) {
					return false;
				}
				return pathname.getName().endsWith(".JPG");
			}
		});
		for (final File file : files) {
			final BufferedImage bufferedImage = ImageIO.read(file);
			final Image image = new Image(bufferedImage.getHeight(), bufferedImage.getWidth());
			image.setFile(file);
			images.add(image);
		}
		return images;
	}

	@Test
	public void test() throws IOException {

		final List<Image> images = readImages();
		Collections.shuffle(images);
		final Rectangle treeRoot = makeRect(images, Orientation.VERTICAL, Orientation.HORIZONTAL, 1.5);
		treeRoot.align();
		treeRoot.scale(1600 / treeRoot.getScaledWidth());

		System.out.println(NumberFormat.getInstance().format(treeRoot.getScaledWidth() / treeRoot.getScaledHeight()));

		writeImage(treeRoot, new File("target/out-orig.jpg"));

		final Fitness biggestVerticalFitness = new Fitness() {

			@Override
			public double calcFitness(final Rectangle image) {
				return image.getScaledHeight() - image.getScaledWidth();
			}
		};
		final Fitness biggestHorizontalFitness = new Fitness() {

			@Override
			public double calcFitness(final Rectangle image) {
				return image.getScaledWidth() - image.getScaledHeight();
			}
		};

		final Fitness smallestHorizontalFitness = new Fitness() {

			@Override
			public double calcFitness(final Rectangle image) {
				final double area = image.getScaledWidth() * image.getScaledHeight();
				return (image.getScaledWidth() - image.getScaledHeight()) / area / area;
			}
		};
		final Fitness smallestVerticalFitness = new Fitness() {

			@Override
			public double calcFitness(final Rectangle image) {
				final double area = image.getScaledWidth() * image.getScaledHeight();
				return (image.getScaledHeight() - image.getScaledWidth()) / area / area;
			}
		};

		// final Fitness candidate1Fitness = biggestHorizontalFitness;
		// final Fitness candidate2Fitness = smallestVerticalFitness;
		//
		for (int i = 1; i < 20; i++) {
			final Rectangle candidate1 = treeRoot.findBestFitness(smallestVerticalFitness, null, 0, 3, 20);
			final int hitLevel = candidate1.levelOf(candidate1, 0);
			final Rectangle candidate2 = treeRoot.findBestFitness(biggestHorizontalFitness, candidate1, 0, hitLevel - 1, 20);
			if (candidate2 == null) {
				// no more variants
				break;
			}
			// for (final Image image : images) {
			// if (candidate1 == null || candidate1Fitness.calcFitness(candidate1) < candidate1Fitness.calcFitness(image)) {
			// candidate1 = image;
			// }
			// if (candidate2 == null || candidate2Fitness.calcFitness(candidate2) < candidate2Fitness.calcFitness(image)) {
			// candidate2 = image;
			// }
			// }
			// if (candidate1 == null || candidate2 == null) {
			// return;
			// }
			System.out.println("total before: width: " + treeRoot.getScaledWidth() + ", height: " + treeRoot.getScaledHeight());
			System.out.println("candidate 1: width: " + candidate1.getScaledWidth() + ", height: " + candidate1.getScaledHeight());
			System.out.println("candidate 2: width: " + candidate2.getScaledWidth() + ", height: " + candidate2.getScaledHeight());

			treeRoot.exchange(candidate1, candidate2);
			treeRoot.align();

			System.out.println("total after: width: " + treeRoot.getScaledWidth() + ", height: " + treeRoot.getScaledHeight());

			treeRoot.scale(1600 / treeRoot.getScaledWidth());
			System.out.println(NumberFormat.getInstance().format(treeRoot.getScaledWidth() / treeRoot.getScaledHeight()));

			writeImage(treeRoot, new File("target/out-mutation" + i + ".jpg"));
		}

	}

	private void writeImage(final Rectangle treeRoot, final File output) throws IOException {
		final BufferedImage targetImage = new BufferedImage((int) treeRoot.getScaledWidth(), (int) treeRoot.getScaledHeight(), BufferedImage.TYPE_INT_RGB);
		treeRoot.drawInto(targetImage.createGraphics(), 0, 0);
		ImageIO.write(targetImage, "JPG", output);
	}
}
