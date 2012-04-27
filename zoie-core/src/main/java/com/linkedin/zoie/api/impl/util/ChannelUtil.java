package com.linkedin.zoie.api.impl.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class ChannelUtil
{
  public static long writeInt(WritableByteChannel channel, int val) throws IOException
  {
    ByteBuffer buf = ByteBuffer.allocate(4);
    buf.putInt(val);
    buf.rewind();
    return writeByteBuffer(channel, buf, 4);
  }
  
  public static long writeLong(WritableByteChannel channel, long val) throws IOException
  {
    ByteBuffer buf = ByteBuffer.allocate(8);
    buf.putLong(val);
    buf.rewind();
    return writeByteBuffer(channel, buf, 8);
  }

  /**
   * Writes bytes from buf to the channel until at least bytesToWrite number of bytes are written.
   * @param channel the destination channel
   * @param buf the source buffer
   * @param t the number of bytes to write
   * @return the number of bytes written
   * @throws IOException
   */
  private static int writeByteBuffer(WritableByteChannel channel, ByteBuffer buf, int bytesToWrite) throws IOException
  {
    int t = bytesToWrite; // remaining bytes to write
    while(t>0)
    {
      t -= channel.write(buf);
    }
    return bytesToWrite - t;
  }
  
  public static long writeString(WritableByteChannel channel, String val) throws IOException
  {
    int len = val.length();
    int size;
    ByteBuffer buf = ByteBuffer.allocate(size = 4 + 2 * len);
    buf.putInt(len);
    for(int i = 0; i < len; i++)
    {
      buf.putChar(val.charAt(i));
    }
    buf.rewind();
    return writeByteBuffer(channel, buf, size);
  }
  
  public static int readInt(ReadableByteChannel channel) throws IOException
  {
    ByteBuffer buf = ByteBuffer.allocate(4);
    if(fillBuffer(channel, buf, true))
    {
      buf.rewind();
      return buf.getInt();
    }
    return -1;
  }
  
  public static long readLong(ReadableByteChannel channel) throws IOException
  {
    ByteBuffer buf = ByteBuffer.allocate(8);
    if(fillBuffer(channel, buf, true))
    {
      buf.rewind();
      return buf.getLong();
    }
    return -1L;
  }
  
  public static String readString(ReadableByteChannel channel) throws IOException
  {
    int nameLen = readInt(channel); // name length
    if(nameLen < 0) return null;
    
    ByteBuffer buf = ByteBuffer.allocate(nameLen * 2);
    if(fillBuffer(channel, buf, true))
    {
      char[] name = new char[nameLen];
      buf.rewind();
      for(int i = 0; i < nameLen; i++)
      {
        name[i] = buf.getChar();
      }
      return new String(name);
    }
    return null;
  }
  
  public static boolean fillBuffer(ReadableByteChannel channel, ByteBuffer buf, boolean clear) throws IOException
  {
    if(clear) buf.clear();
    
    while(true)
    {
      int cnt = channel.read(buf);
      if(cnt < 0) return false;
      if(buf.position() == buf.capacity()) break;// fill to capacity
    }
    return true;
  }
}
