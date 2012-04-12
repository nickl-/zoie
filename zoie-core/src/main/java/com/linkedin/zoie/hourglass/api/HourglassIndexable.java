/**
 * 
 */
package com.linkedin.zoie.hourglass.api;

import com.linkedin.zoie.api.indexing.AbstractZoieIndexable;

/**
 * @author "Xiaoyang Gu<xgu@linkedin.com>"
 *
 */
public abstract class HourglassIndexable extends AbstractZoieIndexable
{
  @Override
  public final boolean isDeleted()
  {
    return false;
  }
}
