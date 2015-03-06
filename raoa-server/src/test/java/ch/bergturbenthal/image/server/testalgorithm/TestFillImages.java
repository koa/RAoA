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

import org.junit.Ignore;
import org.junit.Test;

public class TestFillImages {
	private static interface Fitness {
		double calcFitness(final Rectangle image);
	}

	@Data
	private static class Image implements Rectangle {
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
			final List<Rectangle> x = new ArrayList<TestFillImages.Rectangle>(subNodes.size());
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

		Rectangle getReplacedCopy(final Rectangle r1, final Rectangle r2);

		Rectangle findBestFitness(final Fitness fitness, final Rectangle ignoreCandidate, final int curentLevel, final int fromLevel, final int toLevel);

		boolean contains(final Rectangle rect);

		int deepestLevel(final int startLevel);

		int levelOf(final Rectangle rectangle, final int startLevel);

		void collectAllRectangles(final List<Rectangle> list, final int generationSkip);

	}

	Random random = new Random();

	private String describeRectangle(final Rectangle candidate1) {
		final NumberFormat format = NumberFormat.getNumberInstance();
		final String areaDescription = "width: " + format.format(candidate1.getScaledWidth())
																		+ ", height: "
																		+ format.format(candidate1.getScaledHeight())
																		+ ", ratio: "
																		+ format.format(candidate1.getScaledWidth() / candidate1.getScaledHeight())
																		+ ", area: "
																		+ format.format(candidate1.getScaledWidth() * candidate1.getScaledHeight());
		return areaDescription;
	}

	private void generateMosaic(final List<Image> images, final String series) throws IOException {
		final double targetRatio = Math.sqrt(2);
		final int targetHeight = (int) Math.round(1600 / targetRatio);
		final Rectangle treeRoot = makeRect(images, Orientation.VERTICAL, Orientation.HORIZONTAL, 1.5);
		treeRoot.align();
		treeRoot.scale(1600 / treeRoot.getScaledWidth());

		// System.out.println(NumberFormat.getInstance().format(treeRoot.getScaledWidth() / treeRoot.getScaledHeight()));

		// writeImage(treeRoot, new File("target/out-" + series + "-orig.jpg"));

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
		for (int i = 1; i < 40; i++) {
			System.out.println("----------");
			final int depth = treeRoot.deepestLevel(0);
			final double difference = (targetHeight - treeRoot.getScaledHeight());
			if (Math.abs(difference) < 3) {
				System.out.println("Found in round " + i);
				break;
			}
			final List<Rectangle> candidateRectangles = new ArrayList<TestFillImages.Rectangle>();
			treeRoot.collectAllRectangles(candidateRectangles, depth * 2 / 3);
			final Rectangle candidate1 = candidateRectangles.get(random.nextInt(candidateRectangles.size())); // treeRoot.findBestFitness(smallFitness,
																																																				// null, 0, depth / 2, depth);
			final int hitLevel = treeRoot.levelOf(candidate1, 0);
			final Rectangle candidate2 = treeRoot.findBestFitness(new Fitness() {

				@Override
				public double calcFitness(final Rectangle image) {
					final Rectangle replacedCopy = treeRoot.getReplacedCopy(candidate1, image);
					replacedCopy.align();
					final double ratio = replacedCopy.getScaledWidth() / replacedCopy.getScaledHeight();
					final double ratioDifference = Math.abs(ratio - targetRatio);
					return 1 / ratioDifference;
				}
			}, candidate1, 0, depth / 2, depth);
			if (candidate2 == null) {
				// no more variants
				continue;
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
			System.out.println("total before: " + describeRectangle(treeRoot));
			System.out.println("candidate 1: " + describeRectangle(candidate1));
			System.out.println("candidate 2: " + describeRectangle(candidate2));

			treeRoot.exchange(candidate1, candidate2);
			treeRoot.align();

			System.out.println("total after: " + describeRectangle(treeRoot));

			treeRoot.scale(1600 / treeRoot.getScaledWidth());
			System.out.println("total after scaled: " + describeRectangle(treeRoot));
			// System.out.println(NumberFormat.getInstance().format(treeRoot.getScaledWidth() / treeRoot.getScaledHeight()));

			// writeImage(treeRoot, new File("target/out-" + series + "-mutation" + i + ".jpg"));

		}
		treeRoot.align();
		treeRoot.scale(5000 / treeRoot.getScaledWidth());
		writeImage(treeRoot, new File("/tmp/ponytreff/out/mosaic-" + series + "-final.jpg"));
		System.out.println("---------------------------------------------");
	}

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

		final File[] files = new File("/tmp/ponytreff").listFiles(new FileFilter() {

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
			final Image image = new Image(file, bufferedImage.getHeight(), bufferedImage.getWidth());
			images.add(image);
		}
		return images;
	}

	@Test
	@Ignore
	public void test() throws IOException {

		final List<Image> images = readImages();
		Collections.shuffle(images);
		final int seriesCount = 35;// images.size() / 60;
		final double seriesSize = images.size() * 1.0 / seriesCount;
		int lastSeriesStart = 0;
		for (int i = 0; i < seriesCount; i++) {
			final int nextSeriesStart = (int) Math.round((i + 1) * seriesSize);
			final List<Image> part = images.subList(lastSeriesStart, nextSeriesStart);
			lastSeriesStart = nextSeriesStart;
			final ArrayList<Image> list = new ArrayList<Image>(part);
			for (int j = 0; j < 3; j++) {
				Collections.shuffle(list);
				generateMosaic(list, i + "-" + j);
			}
		}

	}

	private void writeImage(final Rectangle treeRoot, final File output) throws IOException {
		final BufferedImage targetImage = new BufferedImage((int) treeRoot.getScaledWidth(), (int) treeRoot.getScaledHeight(), BufferedImage.TYPE_INT_RGB);
		treeRoot.drawInto(targetImage.createGraphics(), 0, 0);
		ImageIO.write(targetImage, "JPG", output);
	}
}
