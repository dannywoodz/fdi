package fdi;

public class FileID
{
  static {
    System.loadLibrary("fileid");
  }
  
  public static final native long[] id(String filename) throws java.io.IOException;
  
  public static final String idString(String filename) throws java.io.IOException
  {
    long [] id = id(filename);
    return String.format("%d-%d-%d", id[0], id[1], id[2]);
  }
}
