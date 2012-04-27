package com.linkedin.zoie.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Date;

import org.apache.log4j.Logger;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.SimpleFSDirectory;

import com.linkedin.zoie.api.impl.util.ChannelUtil;
import com.linkedin.zoie.api.impl.util.FileUtil;
import com.linkedin.zoie.impl.indexing.internal.IndexSignature;

public class DefaultDirectoryManager implements DirectoryManager
{
  public static final Logger log = Logger.getLogger(DefaultDirectoryManager.class);

  private File _location;
  private final DIRECTORY_MODE _mode;
  public DefaultDirectoryManager(File location)
  {
    if (location==null) throw new IllegalArgumentException("null index directory.");
    _location = location;
    _mode = DIRECTORY_MODE.SIMPLE;
  }
  public DefaultDirectoryManager(File location, DIRECTORY_MODE mode)
  {
    if (location==null) throw new IllegalArgumentException("null index directory.");
    _location = location;
    _mode = mode;
  }
  
  public File getLocation()
  {
    return _location;
  }
  
  public Directory getDirectory() throws IOException
  {
    return getDirectory(false);
  }
  
  public Directory getDirectory(boolean create) throws IOException
  {
    if(!_location.exists() && create)
    {
      // create the parent directory
      _location.mkdirs();
    }
    
    if(create)
    {
      IndexSignature sig = null;
      if (_location.exists())
      {
        sig = getCurrentIndexSignature();
      }
      
      if (sig == null)
      {
        File directoryFile = new File(_location, INDEX_DIRECTORY);
        sig = new IndexSignature(null);
        try
        {
          saveSignature(sig, directoryFile);
        }
        catch (IOException e)
        {
          throw e;
        }
      }
    }
    
    FSDirectory dir = null;
    switch(_mode)
    {
    case SIMPLE:
      dir = new SimpleFSDirectory(_location);
      break;
    case NIO:
      dir = new NIOFSDirectory(_location);
      break;
    case MMAP:
      dir = new MMapDirectory(_location);
      break;
    }
    log.info("created Directory: " + dir);
    return dir;
  }
  
  public static IndexSignature readSignature(File file)
  {
    if (file.exists())
    {
      FileInputStream fin = null;
      try
      {
        fin = new FileInputStream(file);
        return IndexSignature.read(fin);
      }
      catch (IOException ioe)
      {
        log.error("Problem reading index directory file.", ioe);
        return null;
      }
      finally
      {
        if (fin != null)
        {
          try
          {
            fin.close();
          }
          catch (IOException e)
          {
            log.warn("Problem closing index directory file: " + e.getMessage());
          }
        }
      }
    }
    else
    {
      log.info("Starting with empty search index: version information not found at " + file.getAbsolutePath());
      return null;
    }
  }
  
  public static void saveSignature(IndexSignature sig, File file) throws IOException
  {
    if (!file.exists())
    {
      //System.out.println("DefaultDirectoryManager:saveSignature:createNewFile");
      file.createNewFile();
    }
    FileOutputStream fout = null;
    try
    {
      fout = new FileOutputStream(file);
      sig.save(fout);
    }
    finally
    {
      if (fout != null)
      {
        try
        {
          fout.close();
        }
        catch (IOException e)
        {
          log.warn("Problem closing index directory file: " + e.getMessage());
        }
      }
    }
  }
   
  /**
   * Gets the current signature
   * @param indexHome
   * @return
   */
  public IndexSignature getCurrentIndexSignature()
  {
    return getCurrentIndexSignature(_location);
  }
  
  public static IndexSignature getCurrentIndexSignature(File idxDir){
	File directoryFile = new File(idxDir, INDEX_DIRECTORY);
	IndexSignature sig = readSignature(directoryFile);
	return sig;
  }
  

  
  public String getVersion() throws IOException
  {
    IndexSignature sig = getCurrentIndexSignature();
    return sig == null ? null : sig.getVersion();
  }
  
  public void setVersion(String version) throws IOException
  {
    // update new index file
    File directoryFile = new File(_location, INDEX_DIRECTORY);
    IndexSignature sig = readSignature(directoryFile);
    sig.updateVersion(version);
    try
    {
      // make sure atomicity of the index publication
      File tmpFile = new File(_location, INDEX_DIRECTORY + ".new");
      saveSignature(sig, tmpFile);
      File tmpFile2 = new File(_location, INDEX_DIRECTORY + ".tmp");
      directoryFile.renameTo(tmpFile2);
      tmpFile.renameTo(directoryFile);
      tmpFile2.delete();
    }
    catch (IOException e)
    {
      throw e;
    }
    
    
  }
  
  public Date getLastIndexModifiedTime()
  {
    File directoryFile = new File(_location, INDEX_DIRECTORY);
    return new Date(directoryFile.lastModified());      
  }
  
  public String getPath()
  {
    return _location.getAbsolutePath();
  }
  
  public void purge()
  { 
    FileUtil.rmDir(_location);
  }
  
  public boolean exists()
  {
    return _location.exists();
  }
  
  public boolean transferFromChannelToFile(ReadableByteChannel channel, String fileName) throws IOException
  {
    if(!_location.exists())
    {
      // create the parent directory
      _location.mkdirs();
    }
 
    long dataLen = ChannelUtil.readLong(channel);
    if(dataLen < 0) return false;
    
    File file = new File(_location, fileName);
    RandomAccessFile raf = null;
    FileChannel fc = null;
    try
    {
      log.info("transferFromChannelToFile for " + fileName +  " with " +  dataLen + " bytes");
      raf = new RandomAccessFile(file, "rw");
      fc = raf.getChannel();

      long position = 0;
      do
      {
        position += fc.transferFrom(channel, position, dataLen - position);
      } while (position < dataLen);
      return true;
    }
    finally
    {
      try
      {
      if (fc != null) fc.close();
      } finally
      {
        if (raf != null) raf.close();
      }
    }
  }
  
  public long transferFromFileToChannel(String fileName, WritableByteChannel channel) throws IOException
  {
    long amount = 0;
    File file = new File(_location, fileName);
    RandomAccessFile raf = null;
    FileChannel fc = null;
    try
    {
      raf = new RandomAccessFile(file, "r");
      fc = raf.getChannel();
      long dataLen = fc.size();
      log.info("transferFromFileToChannel for " + fileName +  " of " +  dataLen + " bytes");
      amount += ChannelUtil.writeLong(channel, dataLen);

      long position = 0;
      do
      {
        position += fc.transferTo(position, dataLen - position, channel);
      } while (position < dataLen);
      
      amount += position;
    }
    finally
    {
      try
      {
      if (fc != null) fc.close();
      } finally
      {
        if (raf != null) raf.close();
      }
    }
    return amount;
  }
}
