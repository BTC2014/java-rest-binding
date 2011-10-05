/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.rest.graphdb;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.index.impl.lucene.LuceneIndexImplementation;
import org.neo4j.rest.graphdb.batch.BatchCallback;
import org.neo4j.rest.graphdb.batch.BatchRestAPI;
import org.neo4j.rest.graphdb.batch.RestOperations;
import org.neo4j.rest.graphdb.batch.RestOperations.RestOperation;
import org.neo4j.rest.graphdb.converter.RelationshipIterableConverter;
import org.neo4j.rest.graphdb.converter.RestEntityExtractor;
import org.neo4j.rest.graphdb.converter.RestIndexHitsConverter;
import org.neo4j.rest.graphdb.entity.RestEntity;
import org.neo4j.rest.graphdb.entity.RestNode;
import org.neo4j.rest.graphdb.entity.RestRelationship;
import org.neo4j.rest.graphdb.index.IndexInfo;
import org.neo4j.rest.graphdb.index.RestIndex;
import org.neo4j.rest.graphdb.index.RestIndexManager;
import org.neo4j.rest.graphdb.index.RetrievedIndexInfo;
import org.neo4j.rest.graphdb.index.SimpleIndexHits;
import org.neo4j.rest.graphdb.traversal.RestTraversal;
import org.neo4j.rest.graphdb.util.JsonHelper;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.*;


public class RestAPI {

    protected RestRequest restRequest;
    private long propertyRefetchTimeInMillis = 1000;


    public RestAPI(RestRequest restRequest) {
        this.restRequest = restRequest;
    }

    public RestAPI(String uri) {
        this.restRequest = createRestRequest(uri, null, null);
    }

    public RestAPI(String uri, String user, String password) {
        this.restRequest = createRestRequest(uri, user, password);
    }

    protected RestRequest createRestRequest(String uri, String user, String password) {
        return new ExecutingRestRequest(uri, user, password);
    }

    public RestIndexManager index() {
        return new RestIndexManager(restRequest, this);
    }

    public Node getNodeById(long id) {
        RequestResult response = restRequest.get("node/" + id);
        if (response.statusIs(Status.NOT_FOUND)) {
            throw new NotFoundException("" + id);
        }
        return new RestNode(response.toMap(), this);
    }

    public Relationship getRelationshipById(long id) {
        RequestResult requestResult = restRequest.get("relationship/" + id);
        if (requestResult.statusIs(Status.NOT_FOUND)) {
            throw new NotFoundException("" + id);
        }
        return new RestRelationship(requestResult.toMap(), this);
    }


    public Node createNode(Map<String, Object> props) {
        RequestResult requestResult = restRequest.post("node", props);
        return createRestNode(requestResult);
    }

    public Node createRestNode(RequestResult requestResult) {
        if (requestResult.statusOtherThan(Status.CREATED)) {
            final int status = requestResult.getStatus();
            throw new RuntimeException("" + status);
        }
        final String location = requestResult.getLocation();
        return new RestNode(location, this);
    }

    public RestRelationship createRelationship(Node startNode, Node endNode, RelationshipType type, Map<String, Object> props) {
        final RestRequest restRequest = ((RestNode) startNode).getRestRequest();
        Map<String, Object> data = MapUtil.map("to", ((RestNode) endNode).getUri(), "type", type.name());
        if (props != null && props.size() > 0) {
            data.put("data", props);
        }
        RequestResult requestResult = restRequest.post("relationships", data);
        return createRestRelationship(requestResult, startNode);
    }

    public RestRelationship createRestRelationship(RequestResult requestResult, Node startNode) {

        if (requestResult.statusOtherThan(javax.ws.rs.core.Response.Status.CREATED)) {
            final int status = requestResult.getStatus();
            throw new RuntimeException("" + status);
        }
        final String location = requestResult.getLocation();
        return new RestRelationship(location, ((RestNode) startNode).getRestApi());
    }

    public <T extends PropertyContainer> Index<T> getIndex(String indexName) {
        final RestIndexManager index = this.index();
        if (index.existsForNodes(indexName)) return (Index<T>) index.forNodes(indexName);
        if (index.existsForRelationships(indexName)) return (Index<T>) index.forRelationships(indexName);
        throw new IllegalArgumentException("Index " + indexName + " does not yet exist");
    }

    public <T extends PropertyContainer> Index<T> createIndex(Class<T> type, String indexName, boolean fullText) {
        Map<String, String> config = fullText ? LuceneIndexImplementation.FULLTEXT_CONFIG : LuceneIndexImplementation.EXACT_CONFIG;
        if (Node.class.isAssignableFrom(type)) {
            return (Index<T>) this.index().forNodes(indexName, config);
        }
        if (Relationship.class.isAssignableFrom(type)) {
            return (Index<T>) this.index().forRelationships(indexName, config);
        }
        throw new IllegalArgumentException("Required Node or Relationship types to create index, got " + type);
    }

    public RestRequest getRestRequest() {
        return restRequest;
    }


    public TraversalDescription createTraversalDescription() {
        return new RestTraversal();
    }

    public Node getReferenceNode() {
        Map<?, ?> map = restRequest.get("").toMap();
        return new RestNode((String) map.get("reference_node"), this);
    }

    public long getPropertyRefetchTimeInMillis() {
        return propertyRefetchTimeInMillis;
    }

    public String getStoreDir() {
        return restRequest.getUri().toString();
    }


    public void setPropertyRefetchTimeInMillis(long propertyRefetchTimeInMillis) {
        this.propertyRefetchTimeInMillis = propertyRefetchTimeInMillis;
    }


    public <T> T executeBatch(BatchCallback<T> batchCallback) {
        BatchRestAPI batchRestApi = new BatchRestAPI(this.restRequest.getUri(), (ExecutingRestRequest) this.restRequest);
        T batchResult = batchCallback.recordBatch(batchRestApi);
        batchRestApi.stop();
        RestOperations operations = batchRestApi.getRecordedOperations();      
        RequestResult response = this.restRequest.post("batch", createBatchRequestData(operations));      
        Map<Long, Object> mappedObjects = convertRequestResultToEntities(operations, response);
        updateRestOperations(operations, mappedObjects);
        return batchResult;
    }

    private Collection<Map<String, Object>> createBatchRequestData(RestOperations operations) {
        Collection<Map<String, Object>> batch = new ArrayList<Map<String, Object>>();
        final String baseUri = restRequest.getUri();
        for (RestOperation operation : operations.getRecordedRequests().values()) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("method", operation.getMethod());
            if (operation.isSameUri(baseUri)) {
                params.put("to", operation.getUri());
            } else {
                params.put("to",createOperationUri(operation)); 
            }
            if (operation.getData() != null) {
                params.put("body", operation.getData());
            }
            params.put("id", operation.getBatchId());
            batch.add(params);
        }
        return batch;
    }
    
    private String createOperationUri(RestOperation operation){
        String uri =  operation.getBaseUri();
        String suffix = operation.getUri();
        if (suffix.startsWith("/")){
            return uri + suffix;
        }
        return uri + "/" + suffix;
    }

    private Map<Long, Object> convertRequestResultToEntities(RestOperations operations, RequestResult response) {
        Object result = JsonHelper.readJson(response.getEntity());
        if (RestResultException.isExceptionResult(result)) {
            throw new RestResultException(result);
        }
        Collection<Map<String, Object>> responseCollection = (Collection<Map<String, Object>>) result;
        Map<Long, Object> mappedObjects = new HashMap<Long, Object>(responseCollection.size());
        for (Map<String, Object> entry : responseCollection) {
            final Long batchId = getBatchId(entry);
            final RequestResult subResult = RequestResult.extractFrom(entry);      
            RestOperation restOperation = operations.getOperation(batchId);
            if (restOperation.getEntity() != null){
                Object entity = restOperation.getResultConverter().convertFromRepresentation(subResult);
                mappedObjects.put(batchId, entity);
            }
           
        }
        return mappedObjects;
    }

    private Long getBatchId(Map<String, Object> entry) {
        return ((Number) entry.get("id")).longValue();
    }

    private void updateRestOperations(RestOperations operations, Map<Long, Object> mappedObjects) {
        for (RestOperation operation : operations.getRecordedRequests().values()) {
            operation.updateEntity(mappedObjects.get(operation.getBatchId()), this);
        }
    }


    @SuppressWarnings("unchecked")
    public Iterable<Relationship> wrapRelationships(RequestResult requestResult) {
        return (Iterable<Relationship>) new RelationshipIterableConverter(this).convertFromRepresentation(requestResult);
    }

    public RestEntityExtractor createExtractor() {
        return new RestEntityExtractor(this);
    }

    public <S extends PropertyContainer> IndexHits<S> queryIndex(String indexPath, Class<S> entityType) {
        RequestResult response = restRequest.get(indexPath);
        if (response.statusIs(Response.Status.OK)) {
            return new RestIndexHitsConverter(this, entityType).convertFromRepresentation(response);
        } else {
            return new SimpleIndexHits<S>(Collections.emptyList(), 0, entityType, this);
        }
    }
    
    public void deleteEntity(RestEntity entity) {
        entity.getRestRequest().delete( "" );
    }
    public IndexInfo indexInfo(final String indexType) {
        RequestResult response = restRequest.get("index/" + indexType);
        return new RetrievedIndexInfo(response);
    }
    
    public void setPropertyOnEntity( RestEntity entity, String key, Object value ) {
        entity.getRestRequest().put( "properties/" + key, value);
        entity.invalidatePropertyData();
    }
    
    public Map<String, Object> getPropertiesFromEntity(RestEntity entity){
        RequestResult response = entity.getRestRequest().get( "properties" );
        Map<String, Object> properties;
        boolean ok = response.statusIs( Status.OK );
        if ( ok ) {
            properties = (Map<String, Object>) response.toMap(  );
        } else {
            properties = Collections.emptyMap();
        }
       
        return properties;
    }

    
    public void deleteIndex(RestIndex index, String indexPath) {
        index.getRestRequest().delete(indexPath);
    }
    
    public void delete(RestIndex index) {
        deleteIndex(index, index.indexPath(null,null));
    }
    
    public <T> void removeFromIndex( RestIndex index, T entity, String key, Object value ) {
        final String indexPath = index.indexPath(key, value) + "/" + ((RestEntity) entity).getId();
        deleteIndex(index,indexPath);
    }  

    public <T> void removeFromIndex(RestIndex index, T entity, String key) {
        deleteIndex(index, index.indexPath(key, null) + "/" + ((RestEntity) entity).getId());
    }

    public <T> void removeFromIndex(RestIndex index, T entity) {       
        deleteIndex(index, index.indexPath( null, null) + "/" + ( (RestEntity) entity ).getId());
    }


    
    
    public <T> void addToIndex( T entity, RestIndex index,  String key, Object value ) {
        final RestEntity restEntity = (RestEntity) entity;
        final String indexPath = index.indexPath(key, value);
        try {
            addToIndex(restEntity, index.getRestRequest(), indexPath);
        } catch (Exception e) {
          throw new RuntimeException(String.format("Error adding element %d %s %s to index %s", restEntity.getId(), key, value, index.getIndexName()));
        }
    }

    protected void addToIndex(RestEntity restEntity, RestRequest request, String indexPath) {
        String uri = restEntity.getUri();
        final RequestResult response = request.post(indexPath, uri);
        if (response.getStatus() != 201) throw new RuntimeException("Error adding to index");
    }    

}
