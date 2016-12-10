package ch.bergturbenthal.raoa.server.spring.interfaces.ui;

import java.util.Collection;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;

import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.navigator.Navigator;
import com.vaadin.server.VaadinRequest;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.spring.navigator.SpringViewProvider;
import com.vaadin.ui.Panel;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

import ch.bergturbenthal.raoa.server.spring.interfaces.view.AlbumOverviewView;

@Theme("mytheme")
@SpringUI(path = "/")
@Push()
public class MainUI extends UI {
	protected static final String MAINVIEW = AlbumOverviewView.VIEW_NAME;
	// we can use either constructor autowiring or field autowiring
	@Autowired
	private SpringViewProvider viewProvider;

	@Override
	protected void init(final VaadinRequest vaadinRequest) {
		final Authentication authentication2 = SecurityContextHolder.getContext().getAuthentication();
		final Collection<? extends GrantedAuthority> authorities;
		if (authentication2 instanceof OAuth2Authentication) {
			final OAuth2Authentication authentication = (OAuth2Authentication) authentication2;
			final Authentication userAuthentication = authentication.getUserAuthentication();
			final Map<String, String> details = (Map<String, String>) userAuthentication.getDetails();
			authorities = authentication.getAuthorities();
		} else {
			authorities = authentication2.getAuthorities();
		}

		final VerticalLayout root = new VerticalLayout();
		root.setSizeFull();
		root.setMargin(true);
		root.setSpacing(true);
		setContent(root);

		final Panel viewContainer = new Panel();
		viewContainer.setSizeFull();
		root.addComponent(viewContainer);
		root.setExpandRatio(viewContainer, 1.0f);
		final Navigator navigator = new Navigator(this, viewContainer);
		navigator.addProvider(viewProvider);
	}
}
