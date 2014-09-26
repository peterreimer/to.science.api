/*
 * Copyright 2014 hbz NRW (http://www.hbz-nrw.de/)
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
package actions;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import play.mvc.Results.Chunks;
import helper.Globals;
import models.Node;

/**
 * @author Jan Schnasse
 *
 */
public class Index {
    Chunks.Out<String> messageOut;

    /**
     * @param index
     *            the elasticsearch index
     * @param type
     *            the type of the resource
     * @param pid
     *            The namespaced pid to remove from index
     * @return A short message
     */
    public String removeFromIndex(String index, String type, String pid) {

	Globals.search.delete(index, type, pid);
	return pid + " of type " + type + " removed from index " + index + "!";
    }

    /**
     * @param p
     *            The pid with namespace that must be indexed
     * @param index
     *            the name of the index. Convention is to use the namespace of
     *            the pid.
     * @param type
     *            the type of the resource
     * @return a short message.
     */
    public String index(String p, String index, String type) {
	String jsonCompactStr = new Transform().oaiore(p,
		"application/json+compact");
	Globals.search.index(index, type, p, jsonCompactStr);
	return p + " indexed!";
    }

    /**
     * @param p
     *            The pid with namespace that must be indexed
     * @param index
     *            the name of the index. Convention is to use the namespace of
     *            the pid.
     * @param type
     *            the type of the resource
     * @return a short message.
     */
    public String publicIndex(String p, String index, String type) {
	Globals.search.index(index, type, p, new Read().readNode(p).toString());
	return p + " indexed!";
    }

    protected String index(Node n) {
	String namespace = n.getNamespace();
	String pid = n.getPid();
	return index(pid, namespace, n.getContentType());
    }

    /**
     * @param indexName
     */
    public void indexAll(String indexName) {
	Read read = new Read();
	String indexNameWithDatestamp = indexName + "-" + getCurrentDate();
	int until = 0;
	int stepSize = 10;
	int from = 0 - stepSize;
	List<String> nodes = new ArrayList<String>();
	do {
	    until += stepSize;
	    from += stepSize;
	    nodes = read.listRepoNamespace(indexName, from, until);
	    if (nodes.isEmpty())
		break;
	    messageOut.write(Globals.search.indexAll(read.getNodes(nodes),
		    indexNameWithDatestamp).toString());

	} while (nodes.size() == stepSize);
	messageOut.write("\nSuccessfuly Finished\n");
	messageOut.close();

    }

    private String getCurrentDate() {
	DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
	Date date = new Date();
	return dateFormat.format(date);
    }

    /**
     * @param out
     *            messages for chunked responses
     */
    public void setMessageQueue(Chunks.Out<String> out) {
	messageOut = out;
    }

    /**
     * Close messageQueue for chunked responses
     * 
     */
    public void closeMessageQueue() {
	if (messageOut != null)
	    messageOut.close();
    }
}
