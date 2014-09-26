/*
 * Copyright 2012 hbz NRW (http://www.hbz-nrw.de/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package archive.search;

import helper.Globals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import models.Node;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.Play;
import actions.Transform;
import archive.fedora.CopyUtils;

/**
 * @author Jan Schnasse schnasse@hbz-nrw.de
 * 
 */
public class Search {
    final static Logger logger = LoggerFactory.getLogger(Search.class);

    @SuppressWarnings("serial")
    class InvalidRangeException extends RuntimeException {
	// It is just there to be thrown
    }

    @SuppressWarnings("serial")
    class SearchException extends RuntimeException {
	public SearchException(Throwable e) {
	    super(e);
	}
    }

    Client client = null;

    Search(Client client) {
	this.client = client;
    }

    void init(String[] index, String config) {
	try {
	    String indexConfig = CopyUtils.copyToString(Play.application()
		    .resourceAsStream(config), "utf-8");
	    for (String i : index) {
		client.admin().indices().prepareCreate(i)
			.setSource(indexConfig).execute().actionGet();
	    }
	} catch (org.elasticsearch.indices.IndexAlreadyExistsException e) {
	    logger.warn("", e);
	} catch (Exception e) {
	    logger.warn("", e);
	}
    }

    void init(String[] index) {
	try {
	    String indexConfig = CopyUtils.copyToString(Play.application()
		    .resourceAsStream(Globals.elasticsearchSettings), "utf-8");
	    for (String i : index) {
		client.admin().indices().prepareCreate(i)
			.setSource(indexConfig).execute().actionGet();
	    }
	} catch (org.elasticsearch.indices.IndexAlreadyExistsException e) {
	    logger.warn("", e);
	} catch (Exception e) {
	    logger.warn("", e);
	}
    }

    void init(String index) {
	try {
	    String indexConfig = CopyUtils.copyToString(Play.application()
		    .resourceAsStream(Globals.elasticsearchSettings), "utf-8");
	    client.admin().indices().prepareCreate(index)
		    .setSource(indexConfig).execute().actionGet();
	} catch (org.elasticsearch.indices.IndexAlreadyExistsException e) {
	    logger.warn("", e);
	} catch (Exception e) {
	    logger.warn("", e);
	}
    }

    ActionResponse index(String index, String type, String id, String data) {
	return client.prepareIndex(index, type, id).setSource(data).execute()
		.actionGet();
    }

    SearchHits listResources(String index, String type, int from, int until) {
	if (from >= until)
	    throw new InvalidRangeException();
	SearchRequestBuilder builder = null;
	client.admin().indices().refresh(new RefreshRequest()).actionGet();
	if (index == null || index.equals(""))
	    builder = client.prepareSearch();
	else
	    builder = client.prepareSearch(index);
	if (type != null && !type.equals(""))
	    builder.setTypes(type);
	builder.setFrom(from).setSize(until - from);
	SearchResponse response = builder.execute().actionGet();
	return response.getHits();
    }

    List<String> list(String index, String type, int from, int until) {
	SearchHits hits = listResources(index, type, from, until);
	Iterator<SearchHit> it = hits.iterator();
	List<String> list = new Vector<String>();
	while (it.hasNext()) {
	    SearchHit hit = it.next();
	    list.add(hit.getId());
	}
	return list;
    }

    ActionResponse delete(String index, String type, String id) {
	return client.prepareDelete(index, type, id)
		.setOperationThreaded(false).execute().actionGet();
    }

    SearchHits query(String index, String fieldName, String fieldValue) {
	client.admin().indices().refresh(new RefreshRequest()).actionGet();
	QueryBuilder query = QueryBuilders.boolQuery().must(
		QueryBuilders.matchQuery(fieldName, fieldValue));
	SearchResponse response = client.prepareSearch(index).setQuery(query)
		.execute().actionGet();
	return response.getHits();
    }

    Map<String, Object> getSettings(String index, String type) {
	try {
	    client.admin().indices().refresh(new RefreshRequest()).actionGet();
	    ClusterState clusterState = client.admin().cluster().prepareState()
		    .setIndices(index).execute().actionGet().getState();
	    IndexMetaData inMetaData = clusterState.getMetaData().index(index);
	    MappingMetaData metad = inMetaData.mapping(type);
	    return metad.getSourceAsMap();
	} catch (IOException e) {
	    throw new SearchException(e);
	}
    }

    /**
     * @param pid
     *            a pid of a node
     * @return a map that represents the node
     */
    public Map<String, Object> get(String pid) {
	client.admin().indices().refresh(new RefreshRequest()).actionGet();
	GetResponse response = client
		.prepareGet(pid.split(":")[0], "_all", pid).execute()
		.actionGet();
	return response.getSource();

    }

    /**
     * @param list
     *            list of nodes to index
     * @param index
     *            name of a index
     * @return list of messages
     */
    public List<String> indexAll(List<Node> list, String index) {
	init(index);
	List<String> result = new ArrayList<String>();
	Transform t = new Transform();
	BulkRequestBuilder bulkRequest = client.prepareBulk();

	for (Node node : list) {
	    try {
		bulkRequest.add(client.prepareIndex(index,
			node.getContentType(), node.getPid()).setSource(
			t.oaiore(node, "application/json+compact")));
		result.add(node.getPid());
		play.Logger.debug("Add " + node.getPid() + "to bulk action");
	    } catch (Exception e) {
		result.add("A problem occured");
	    }
	}
	play.Logger.debug("Start building Index " + index);
	BulkResponse bulkResponse = bulkRequest.execute().actionGet();
	if (bulkResponse.hasFailures()) {
	    result.add(bulkResponse.buildFailureMessage());
	    play.Logger.debug("FAIL: " + bulkResponse.buildFailureMessage());
	}
	return result;
    }

}
