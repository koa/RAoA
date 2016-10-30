package ch.bergturbenthal.raoa.server.spring.interfaces.ui;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;

import com.vaadin.annotations.Push;
import com.vaadin.navigator.Navigator;
import com.vaadin.server.VaadinRequest;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.ui.Label;
import com.vaadin.ui.UI;

@SpringUI(path = "/ui")
@Push()
public class MainUI extends UI {
	protected static final String MAINVIEW = "main";
	private Navigator navigator;

	@Override
	protected void init(final VaadinRequest vaadinRequest) {
		final OAuth2Authentication authentication = (OAuth2Authentication) SecurityContextHolder.getContext().getAuthentication();
		final Authentication userAuthentication = authentication.getUserAuthentication();
		final Map<String, String> details = (Map<String, String>) userAuthentication.getDetails();
		setContent(new Label("Hello! I'm the root UI! " + authentication.getAuthorities()));
	}
}
