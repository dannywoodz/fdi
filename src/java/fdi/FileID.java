package fdi;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileID
{
  private static final native void init();
	private static final MessageDigest sha1;

  static {
	  try
		{
  	  sha1 = MessageDigest.getInstance("SHA-1");
	  }
		catch(NoSuchAlgorithmException nsae)
		{
		  throw new RuntimeException(nsae);
		}
    System.loadLibrary("fileid");
    init();
  }
  
	private static final ThreadLocal<MessageDigest> generator = new ThreadLocal<MessageDigest>() {
	  @Override
		protected MessageDigest initialValue() {
		  return sha1;
		}
	};

  public static final native byte[] fingerprint(String filename) throws java.io.IOException;
  
  public static final String idString(String filename) 
  {
	  MessageDigest sha = generator.get();
		byte [] hash = sha.digest(filename.getBytes());
		sha.reset();
		StringBuilder builder = new StringBuilder(20);
		for ( int i = 0 ; i < hash.length ; i++ ) builder.append(Integer.toHexString(hash[i] & 0xff));
		return builder.toString();
  }

  public static final boolean isSimilar(byte [] first, byte [] second)
  {
    int error = 0;
    int cutoff = first.length * 3;
    for ( int i = 0 ; error < cutoff && i < first.length ; i++ )
    {
      error += Math.abs(first[i] - second[i]);
    }
    return error < cutoff;
  }
}
