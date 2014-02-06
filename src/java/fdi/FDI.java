// APPLICATION
//
// fdi - Find Duplicates Images
//
// FILE
//
// FDI.java
//
// DESCRIPTION
//
// Convenience functions for the Find Duplicate Images application,
// written in Java for speed.
//
// The fingerprint bundle is a hash:
//
// COPYRIGHT
//
// Copyright (C) 2014 Daniel Woods
//
// LICENSE
//
// GNU General Public License, version 3 (http://opensource.org/licenses/GPL-3.0)

package fdi;

import java.io.File;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FDI
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
    System.loadLibrary("fdi" + System.getProperty("sun.arch.data.model"));
    init();
  }
  
  private static final ThreadLocal<MessageDigest> generator = new ThreadLocal<MessageDigest>() {
    @Override
    protected MessageDigest initialValue() {
      return sha1;
    }
  };

  public static final native byte[] fingerprint(String filename) throws java.io.IOException;
  
  public static final String idString(String filename) throws IOException
  {
    /* This key just has to be *unique enough*, given that it's' not driving a nuclear power
       plant.  The best way would probably be to generate a digest of the file, but I don't
       want to have to read every bloody byte of every file off the disk to do this in order
       to determine whether or not it needs to be loaded.
       For the purposes of this system, 'unique enough' is a hyphen-separated string containing
       the SHA1 of the filename, as well as the last modification time and length of the file.
       It's certainly possible to get a false hit with this (copying over an existing file with
       another that happens to have the same length and a doctored modification time), but it's
       a remote enough possibility for me not to care.
       One possibility that I've ruled out (after having done it) is going native to get more
       detailed information from the POSIX stat syscall, but the inode number is synthetic
       when dealing with files on FAT filesystems, and inconsistent across remounts */
    
    File file = new File(filename);
    MessageDigest sha = generator.get();
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    DataOutputStream dataStream = new DataOutputStream(byteStream);
    dataStream.writeBytes(filename);
    dataStream.writeLong(file.lastModified());
    dataStream.writeLong(file.length());
    dataStream.flush();
    byte [] hash = sha.digest(byteStream.toByteArray());
    sha.reset();
    StringBuilder builder = new StringBuilder(hash.length * 2);
    for ( int i = 0 ; i < hash.length ; i++ ) builder.append(Integer.toHexString(hash[i] & 0xff));
    return builder.toString();
  }

  public static final boolean isSimilar(byte [] first, byte [] second, int tolerance)
  {
    if ( first.length != second.length )
      throw new IllegalArgumentException(
	String.format("Both fingerprint arrays must be the same size.  Got %d vs. %d",
		      first.length, second.length)
	);

    if ( tolerance == 0 )
    {
      return Arrays.equals(first, second);
    }
    else
    {
      int error = 0;
      int cutoff = first.length * tolerance;
      for ( int i = 0 ; error < cutoff && i < first.length ; i++ )
      {
	error += Math.abs((first[i] & 0xff) - (second[i] & 0xff));
      }
      return error < cutoff;
    }
  }
}
