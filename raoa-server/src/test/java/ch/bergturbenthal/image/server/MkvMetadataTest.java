package ch.bergturbenthal.image.server;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import org.jcodec.containers.mkv.SimpleEBMLParser;
import org.jcodec.containers.mkv.Type;
import org.jcodec.containers.mkv.ebml.Element;
import org.jcodec.containers.mkv.ebml.MasterElement;
import org.jcodec.containers.mkv.ebml.StringElement;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import ch.bergturbenthal.raoa.util.Pair;

public class MkvMetadataTest {
	@Test
	public void testReadMkvMetadata() throws IOException {
		final Resource resource = new ClassPathResource("photos/testalbum/testvideo.mkv");
		final FileChannel iFS = FileChannel.open(resource.getFile().toPath(), StandardOpenOption.READ);
		final SimpleEBMLParser ebmlParser = new SimpleEBMLParser(iFS);
		ebmlParser.parse();
		iFS.close();
		ebmlParser.printParsedTree();
		final MasterElement[] findAll = Type.findAll(ebmlParser.getTree(), MasterElement.class, Type.Segment, Type.Tags, Type.Tag, Type.SimpleTag);
		for (final MasterElement element : findAll) {
			if (element.type == Type.SimpleTag) {
				final Pair<String, String> stringPair = findNamStringPair(element.children);
				System.out.println(stringPair.first + ":" + stringPair.second);
			}
			// System.out.println(element);
		}
	}

	private Pair<String, String> findNamStringPair(final Iterable<Element> elements) {
		String name = null;
		String string = null;
		for (final Element element : elements) {
			if (element.type == Type.TagName) {
				name = ((StringElement) element).get();
			}
			if (element.type == Type.TagString) {
				string = ((StringElement) element).get();
			}
		}
		return new Pair<String, String>(name, string);
	}
}
