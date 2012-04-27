package com.linkedin.zoie.hourglass.impl;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

import org.apache.log4j.Logger;

public class HourGlassScheduler
{
  public static final Logger log = Logger.getLogger(HourGlassScheduler.class.getName());
  private String _schedule;
  private final FREQUENCY _freq;
  private int[] _params = new int[3];
  private int _trimThreshold = Integer.MAX_VALUE;
  private static ThreadLocal<SimpleDateFormat> dateFormatter = new ThreadLocal<SimpleDateFormat>()
  {
    @Override
    protected SimpleDateFormat initialValue()
    {
      return new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    }
  };
  public static enum FREQUENCY
  {
    MINUTELY, // good for use in testing
    HOURLY,
    DAILY,
  }
  public HourGlassScheduler(FREQUENCY freq, String schedule)
  {
    // format "ss mm hh"
    _schedule = schedule;
    _freq = freq;
    String [] param = _schedule.split(" ");
    for(int i = 0; i < Math.min(_params.length, param.length); i++)
    {
      _params[i] = parseParam(param[i]);
    }
    log.info("schedule: " + Arrays.toString(_params) + " frequenty: " + _freq);
  }
  public HourGlassScheduler(FREQUENCY freq, String schedule, int trimThreshold)
  {
    this(freq, schedule);
    _trimThreshold = trimThreshold;
    log.info("schedule: " + Arrays.toString(_params) + " frequenty: " + _freq + " trimThreshold: keep last " + _trimThreshold + " rolling periods");
  }
  private int parseParam(String param)
  {
    if (param.indexOf('*')>=0) return 0;
    int ret = 0;
    try
    {
      ret = Integer.parseInt(param.trim());
    } catch(NumberFormatException e)
    {
      throw new IllegalArgumentException("Failed to instantiate HourGlassScheduler", e);
    }
    return ret;
  }
  public FREQUENCY getFreq()
  {
    return _freq;
  }
  public int getTrimThreshold()
  {
    return _trimThreshold;
  }
  protected Calendar getNextRoll()
  {
    long timenow = System.currentTimeMillis();
    Calendar next = Calendar.getInstance();
    next.setTimeInMillis(timenow);
    Calendar now = Calendar.getInstance();
    now.setTimeInMillis(timenow);
    switch(_freq)
    {
    case MINUTELY: // set the second so that every minute we switch over at the same second
      next.set(Calendar.SECOND, _params[0]);
      if (!next.after(now)) next.add(Calendar.MINUTE, 1);
      break;
    case HOURLY:   // set the second and minute so that we switch over at the same time in the hour
      next.set(Calendar.SECOND, _params[0]);
      next.set(Calendar.MINUTE, _params[1]);
      if (!next.after(now)) next.add(Calendar.HOUR_OF_DAY, 1);
      break;
    case DAILY:    // set hour, minute, second so that we switch over at the same time of the day
      next.set(Calendar.SECOND, _params[0]);
      next.set(Calendar.MINUTE, _params[1]);
      next.set(Calendar.HOUR_OF_DAY, _params[2]);
      if (!next.after(now)) next.add(Calendar.DAY_OF_MONTH, 1);
      break;
    }
    return next;
  }
  Calendar getCurrentRoll()
  {
    Calendar current = getNextRoll();
    switch(_freq)
    {
    case MINUTELY:
      current.add(Calendar.MINUTE, -1);
      break;
    case HOURLY:
      current.add(Calendar.HOUR_OF_DAY, -1);
      break;
    case DAILY:
      current.add(Calendar.DAY_OF_MONTH, -1);
      break;
    }
    return current;
  }
  public Calendar getTrimTime(Calendar now)
  {
    Calendar threshold = (Calendar) now.clone();
    int trimUnit = 60*60*24;
    switch(getFreq())
    {
    case MINUTELY:
      trimUnit = 60;
      break;
    case HOURLY:
      trimUnit = 60*60;
      break;
    case DAILY:
      trimUnit = 60*60*24;
      break;
    }
    threshold.add(Calendar.SECOND, - trimUnit * _trimThreshold);
    return threshold;
  }
  /**
   * convert a Calendar time to a folder name using GMT time string
   * @param cal
   * @return a String for folder name
   */
  public String getFolderName(Calendar cal)
  {
    return dateFormatter.get().format(cal.getTime());
  }
  @Override
  public String toString()
  {
    return "HourGlassScheduler:" + _freq + "  " + _schedule;
  }
}
