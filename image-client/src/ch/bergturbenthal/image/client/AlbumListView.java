package ch.bergturbenthal.image.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import ch.bergturbenthal.image.data.AlbumList;

public class AlbumListView extends ListActivity {

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final Intent intent = getIntent();
    final String[] hostnames = intent.getStringArrayExtra("hostnames");
    final int port = intent.getIntExtra("port", -1);
    for (final String hostname : hostnames) {
      try {

        final String url = "http://" + hostname + ":" + port + "/albums";
        Log.d("QUERY", "Try host " + url);
        // Set the Accept header for "application/xml"
        final HttpHeaders requestHeaders = new HttpHeaders();
        final List<MediaType> acceptableMediaTypes = new ArrayList<MediaType>();
        acceptableMediaTypes.add(MediaType.APPLICATION_XML);
        requestHeaders.setAccept(acceptableMediaTypes);
        // Populate the headers in an HttpEntity object to use for the request
        final HttpEntity<?> requestEntity = new HttpEntity<Object>(requestHeaders);

        // Create a new RestTemplate instance
        final RestTemplate restTemplate = new RestTemplate();

        // Perform the HTTP GET request
        final ResponseEntity<AlbumList> responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, AlbumList.class);

        // Return the list of states
        final AlbumList stateList = responseEntity.getBody();
        Log.e("QUERY", "Connected");
        break;
      } catch (final ResourceAccessException ex) {
        Log.d("QUERY", "Cannot access to host " + hostname + " try next", ex);
      }
    }
  }

}
