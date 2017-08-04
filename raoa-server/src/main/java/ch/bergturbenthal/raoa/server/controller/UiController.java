package ch.bergturbenthal.raoa.server.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import ch.bergturbenthal.raoa.server.Album;
import ch.bergturbenthal.raoa.server.AlbumAccess;
import ch.bergturbenthal.raoa.server.AlbumImage;
import ch.bergturbenthal.raoa.server.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.util.AlbumEntryComparator;
import lombok.Value;

@Controller
@RequestMapping("/")
public class UiController {
	@Value
	public static class AlbumData {
		private String id;
		private String name;
	}

	@Value
	public static class AlbumEntry {
		private String filename;
		private String id;
		private double widthRatio;
	}

	@Autowired
	private AlbumAccess albumAccess;

	@GetMapping("album/{albumId}")
	public ModelAndView albumIndex(@PathVariable("albumId") final String albumId) {
		final Album album = albumAccess.getAlbum(albumId);
		if (album == null) {
			return new ModelAndView("album", HttpStatus.NOT_FOUND);
		}
		final List<AlbumEntry> foundImages = album.listImages().entrySet().stream().filter(e -> !e.getValue().isVideo()).sorted(new AlbumEntryComparator()).map(entry -> {
			final AlbumImage albumEntry = entry.getValue();
			final AlbumEntryData albumEntryData = albumEntry.getAlbumEntryData();
			final double ratio = albumEntryData.getOriginalDimension().map(d -> d.getWidth() / d.getHeight()).orElse(3.0 / 4);
			return new AlbumEntry(albumEntry.getName(), entry.getKey(), ratio);
		}).collect(Collectors.toList());
		final Map<String, Object> variables = new HashMap<>();
		variables.put("images", foundImages);
		variables.put("album", new AlbumData(albumId, album.getName()));
		return new ModelAndView("link", variables);
	}

	@GetMapping("/")
	public ModelAndView main() {
		return new ModelAndView("index");
	}
}
