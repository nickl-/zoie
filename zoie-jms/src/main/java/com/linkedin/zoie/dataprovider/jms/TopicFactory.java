package com.linkedin.zoie.dataprovider.jms;

import javax.jms.Topic;

public interface TopicFactory {

	public Topic createTopic(String name);
}
