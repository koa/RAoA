package ch.bergturbenthal.raoa.data;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.bergturbenthal.raoa.data.model.UpdateMetadataRequest;
import ch.bergturbenthal.raoa.data.model.mutation.Mutation;
import ch.bergturbenthal.raoa.data.model.mutation.StorageMutation;
import ch.bergturbenthal.raoa.data.model.mutation.TitleMutation;

public class JacksonSerialisationTest {
	@Test
	public void testSerializeAlbumMutation() throws IOException {
		final UpdateMetadataRequest request = new UpdateMetadataRequest();
		request.setMutationEntries(Arrays.<Mutation> asList(new StorageMutation(), new TitleMutation()));
		final ObjectMapper mapper = new ObjectMapper();
		final String serialString = mapper.writeValueAsString(request);
		System.out.println(serialString);
		final UpdateMetadataRequest parsedRequest = mapper.readValue(serialString, UpdateMetadataRequest.class);
		// System.out.println(request);
		// System.out.println(parsedRequest);
		Assert.assertEquals(request.toString(), parsedRequest.toString());
	}
}
