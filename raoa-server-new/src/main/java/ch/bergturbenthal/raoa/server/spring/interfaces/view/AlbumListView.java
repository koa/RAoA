package ch.bergturbenthal.raoa.server.spring.interfaces.view;

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

import javax.servlet.ServletContext;

import org.apache.catalina.util.URLEncoder;
import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.data.Item;
import com.vaadin.data.Property.ReadOnlyException;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.server.ExternalResource;
import com.vaadin.server.Resource;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Grid;
import com.vaadin.ui.renderers.ImageRenderer;

import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess.AlbumDataHandler;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess.ImageDataHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringView(name = AlbumListView.VIEW_NAME)
public class AlbumListView extends CustomComponent implements View {
	public static final String VIEW_NAME = "album";

	private final Consumer<ViewChangeEvent> viewChangeConsumer;

	@Autowired
	public AlbumListView(final AlbumAccess albumAccess, final ServletContext servletContext) {
		final IndexedContainer container = new IndexedContainer();
		container.addContainerProperty("image", Resource.class, null);
		final Grid imageListGrid = new Grid(container);
		imageListGrid.getColumn("image").setRenderer(new ImageRenderer());
		imageListGrid.setStyleName("gridwiththumbnails");
		imageListGrid.setCellStyleGenerator(cell -> "image".equals(cell.getPropertyId()) ? "imagecol" : null);
		final CssLayout photoOverviewlayout = new CssLayout();
		setCompositionRoot(imageListGrid);
		final String contextPath = servletContext.getContextPath();
		final String rootPath;
		if (contextPath.isEmpty()) {
			rootPath = "/";
		} else {
			rootPath = contextPath;
		}
		viewChangeConsumer = (event) -> {
			final String albumId = event.getParameters();
			photoOverviewlayout.removeAllComponents();
			final Optional<AlbumDataHandler> albumDataOptional = albumAccess.getAlbumData(albumId);
			if (!albumDataOptional.isPresent()) {
				return;
			}
			final AlbumDataHandler albumData = albumDataOptional.get();
			for (final ImageDataHandler image : albumData.listImages()) {
				final Item item = container.addItem(image.getId());
				try {
					final URI uri = URI	.create(rootPath)
															.resolve("albums/" + URLEncoder.DEFAULT.encode(albumId) + "/image/" + URLEncoder.DEFAULT.encode(image.getId()) + "/thumbnail.jpg");
					item.getItemProperty("image").setValue(new ExternalResource(uri.toString()));
				} catch (final ReadOnlyException e) {
					log.warn("Cannlot load image", e);
				}
			}
		};
	}

	@Override
	public void enter(final ViewChangeEvent event) {
		viewChangeConsumer.accept(event);
	}

}
