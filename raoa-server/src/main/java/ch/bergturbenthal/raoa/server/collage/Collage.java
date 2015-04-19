package ch.bergturbenthal.raoa.server.collage;

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

import org.junit.Ignore;
import org.junit.Test;

public class Collage {
	private static final Random random = new Random();

	public static Rectangle createMosaic(final List<Image> images, final int targetWidth, final int targetHeight) {
		final double targetRatio = targetWidth * 1.0 / targetHeight;
		final Rectangle treeRoot = targetRatio > 0 ? makeRect(images, Orientation.VERTICAL, Orientation.HORIZONTAL, 1.5) : makeRect(images,
																																																																Orientation.HORIZONTAL,
																																																																Orientation.VERTICAL,
																																																																1.5);
		treeRoot.align();
		treeRoot.scale(targetWidth / treeRoot.getScaledWidth());

		for (int i = 1; i < 40; i++) {
			// System.out.println("----------");
			final int depth = treeRoot.deepestLevel(0);
			final double difference = (targetHeight - treeRoot.getScaledHeight());
			if (Math.abs(difference) < 3) {
				// System.out.println("Found in round " + i);
				break;
			}
			final List<Rectangle> candidateRectangles = new ArrayList<Rectangle>();
			treeRoot.collectAllRectangles(candidateRectangles, depth * 2 / 3);
			final Rectangle candidate1 = candidateRectangles.get(random.nextInt(candidateRectangles.size())); // treeRoot.findBestFitness(smallFitness,
																																																				// null, 0, depth / 2, depth);
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
			// System.out.println("total before: " + describeRectangle(treeRoot));
			// System.out.println("candidate 1: " + describeRectangle(candidate1));
			// System.out.println("candidate 2: " + describeRectangle(candidate2));

			treeRoot.exchange(candidate1, candidate2);
			treeRoot.align();

			// System.out.println("total after: " + describeRectangle(treeRoot));

			treeRoot.scale(targetWidth / treeRoot.getScaledWidth());
			// System.out.println("total after scaled: " + describeRectangle(treeRoot));
			// System.out.println(NumberFormat.getInstance().format(treeRoot.getScaledWidth() / treeRoot.getScaledHeight()));

			// writeImage(treeRoot, new File("target/out-" + series + "-mutation" + i + ".jpg"));

		}
		treeRoot.align();
		treeRoot.scale(targetWidth / treeRoot.getScaledWidth());
		return treeRoot;
	}

	private static Orientation inverse(final Orientation orientation) {
		Orientation nextOrientation;
		if (orientation == Orientation.HORIZONTAL) {
			nextOrientation = Orientation.VERTICAL;
		} else {
			nextOrientation = Orientation.HORIZONTAL;
		}
		return nextOrientation;
	}

	private static Rectangle makeRect(final List<Image> images, final Orientation orientation, final Orientation strechOrientation, final double strechFactor) {
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

	public static List<Image> readImagesFromDirectory(final File dir) throws IOException {
		final List<Image> images = new ArrayList<>();

		final File[] files = dir.listFiles(new FileFilter() {

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

	public static void writeImage(final Rectangle treeRoot, final File output) throws IOException {
		final BufferedImage targetImage = new BufferedImage((int) treeRoot.getScaledWidth(), (int) treeRoot.getScaledHeight(), BufferedImage.TYPE_INT_RGB);
		treeRoot.drawInto(targetImage.createGraphics(), 0, 0);
		ImageIO.write(targetImage, "JPG", output);
	}

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
		final int targetWidth = 1600;
		final int targetHeight = 1200;
		final Rectangle treeRoot = createMosaic(images, targetWidth, targetHeight);
		writeImage(treeRoot, new File("/tmp/ponytreff/out/mosaic-" + series + "-final.jpg"));
		System.out.println("---------------------------------------------");
	}

	private List<Image> readImages() throws IOException {
		final File dir = new File("/tmp/ponytreff");
		return readImagesFromDirectory(dir);
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
}
