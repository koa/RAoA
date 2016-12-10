package ch.bergturbenthal.raoa.server.spring.interfaces.view;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.data.Item;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Table;

import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess.AlbumDataHandler;

@SpringView(name = AlbumOverviewView.VIEW_NAME)
public class AlbumOverviewView extends CustomComponent implements View {
	private static final String LABEL_PROPERTY = "label";
	private static final String TIME_PROPERTY = "time";
	public static final String VIEW_NAME = "";

	@Autowired
	private AlbumAccess albumAccess;
	private final Consumer<ViewChangeEvent> enterConsumer;

	public AlbumOverviewView() {

		final IndexedContainer container = new IndexedContainer();
		container.addContainerProperty(LABEL_PROPERTY, String.class, null);
		container.addContainerProperty(TIME_PROPERTY, Instant.class, null);

		final Table albumSelect = new Table("Select Album", container);
		albumSelect.setSelectable(true);
		// albumSelect.setColumnHeaderMode(ColumnHeaderMode.HIDDEN);
		albumSelect.setVisibleColumns(LABEL_PROPERTY, TIME_PROPERTY);
		albumSelect.setNullSelectionAllowed(false);
		albumSelect.setSortContainerPropertyId(TIME_PROPERTY);
		albumSelect.setSortAscending(false);
		albumSelect.addItemClickListener(event -> getUI().getNavigator().navigateTo(AlbumListView.VIEW_NAME + "/" + event.getItemId()));
		setCompositionRoot(albumSelect);
		enterConsumer = (event) -> {
			final List<String> albums = albumAccess.listAlbums();
			container.removeAllItems();
			for (final String albumId : albums) {
				final Optional<AlbumDataHandler> albumDataOptional = albumAccess.getAlbumData(albumId);
				if (!albumDataOptional.isPresent()) {
					continue;
				}
				final AlbumDataHandler albumdata = albumDataOptional.get();
				final Optional<String> albumTitle = albumdata.getAlbumTitle();
				if (!albumTitle.isPresent()) {
					continue;
				}
				final Item albumItem = container.addItem(albumId);
				albumItem.getItemProperty(LABEL_PROPERTY).setValue(albumTitle.get());
				albumItem.getItemProperty(TIME_PROPERTY).setValue(albumdata.getCaptureTime().orElse(null));
			}

		};
	}

	@Override
	public void enter(final ViewChangeEvent event) {
		enterConsumer.accept(event);
	}

}
