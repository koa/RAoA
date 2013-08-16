/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.data.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TODO: add type comment.
 * 
 */
@Data
@AllArgsConstructor(suppressConstructorProperties = true)
@NoArgsConstructor
public class ImportFileRequest {
	private String contentType;
	private byte[] data;
	private String filename;
}
