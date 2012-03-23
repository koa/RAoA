package ch.bergturbenthal.image.server;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Base32;

public class Util {
  public static String sha1(final String text) {
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-1");
      md.update(text.getBytes("utf-8"), 0, text.length());
      final Base32 base32 = new Base32();
      return base32.encodeToString(md.digest()).toLowerCase();
    } catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException("cannot make sha1 of " + text, e);
    } catch (final UnsupportedEncodingException e) {
      throw new RuntimeException("cannot make sha1 of " + text, e);
    }
  }
}
