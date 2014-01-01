package fdi;

public class FileID
{
  private static final native void init();

  static {
    System.loadLibrary("fileid");
    init();
  }
  
  public static final native long[] id(String filename) throws java.io.IOException;
  public static final native byte[] fingerprint(String filename) throws java.io.IOException;
  
  public static final String idString(String filename) throws java.io.IOException
  {
    long [] id = id(filename);
    return String.format("%d-%d-%d", id[0], id[1], id[2]);
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
