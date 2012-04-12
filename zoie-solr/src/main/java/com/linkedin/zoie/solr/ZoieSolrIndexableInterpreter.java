package com.linkedin.zoie.solr;

import java.io.Serializable;

import com.linkedin.zoie.api.indexing.AbstractZoieIndexableInterpreter;

/**
 * Does not support store data.
 */
public class ZoieSolrIndexableInterpreter extends AbstractZoieIndexableInterpreter<DocumentWithID>
{

	@Override
	public DocumentWithID convertAndInterpret(DocumentWithID src)
	{
		return src;
	}

}
