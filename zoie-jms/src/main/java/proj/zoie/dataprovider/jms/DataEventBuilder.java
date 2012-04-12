package com.linkedin.zoie.dataprovider.jms;

import javax.jms.JMSException;
import javax.jms.Message;

import com.linkedin.zoie.api.DataConsumer.DataEvent;

public interface DataEventBuilder<T> {

	public DataEvent<T> buildDataEvent(Message message) throws JMSException;
	
}
