package gov.bnl.channelfinder;

import static gov.bnl.channelfinder.CFResourceDescriptors.ES_CHANNEL_INDEX;
import static gov.bnl.channelfinder.CFResourceDescriptors.ES_CHANNEL_TYPE;
import static gov.bnl.channelfinder.CFResourceDescriptors.ES_TAG_INDEX;
import static gov.bnl.channelfinder.CFResourceDescriptors.ES_TAG_TYPE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class TagRepository implements CrudRepository<XmlTag, String> {

    @Autowired
    ElasticSearchClient esService;

    ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unused")
    public <S extends XmlTag> S index(S tag) {
        RestHighLevelClient client = esService.getIndexClient();
        try {
            IndexRequest indexRequest = new IndexRequest(ES_TAG_INDEX, ES_TAG_TYPE).id(tag.getName())
                    .source(objectMapper.writeValueAsBytes(tag), XContentType.JSON);
            indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            IndexResponse indexResponse = client.index(indexRequest, RequestOptions.DEFAULT);
            /// verify the creation of the tag
            Result result = indexResponse.getResult();
            if (result.equals(Result.CREATED) || result.equals(Result.UPDATED)) {
                client.indices().refresh(new RefreshRequest(ES_TAG_INDEX), RequestOptions.DEFAULT);
                return (S) findById(tag.getName()).get();
            }
        } catch (Exception e) {
        	e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to index tag" + tag, null);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <S extends XmlTag> Iterable<S> indexAll(Iterable<S> tags) {
        RestHighLevelClient client = esService.getIndexClient();
        try {
            BulkRequest bulkRequest = new BulkRequest();
            for (XmlTag tag : tags) {
                IndexRequest indexRequest = new IndexRequest(ES_TAG_INDEX, ES_TAG_TYPE).id(tag.getName())
                        .source(objectMapper.writeValueAsBytes(tag), XContentType.JSON);
                bulkRequest.add(indexRequest);
            }

            bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
            /// verify the creation of the tag
            if (bulkResponse.hasFailures()) {
                // Failed to create all the tags

            } else {
                List<String> createdTagIds = new ArrayList<String>();
                for (BulkItemResponse bulkItemResponse : bulkResponse) {
                    if (bulkItemResponse.getResponse().getResult().equals(Result.CREATED)) {
                        createdTagIds.add(bulkItemResponse.getId());
                    }
                }
                return (Iterable<S>) findAllById(createdTagIds);
            }
        } catch (Exception e) {
        	e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to index tags" + tags, null);        }
        return null;

    }

    @Override
    public <S extends XmlTag> S save(S tag) {
        RestHighLevelClient client = esService.getIndexClient();
        try {

            UpdateRequest updateRequest = new UpdateRequest(ES_TAG_INDEX, ES_TAG_TYPE, tag.getName());

            Optional<XmlTag> existingTag = findById(tag.getName());
            if(existingTag.isPresent()) {
                XmlTag newTag = existingTag.get();
                updateRequest.doc(objectMapper.writeValueAsBytes(newTag), XContentType.JSON);
            } else {
                IndexRequest indexRequest = new IndexRequest(ES_TAG_INDEX, ES_TAG_TYPE).id(tag.getName())
                        .source(objectMapper.writeValueAsBytes(tag), XContentType.JSON);
                updateRequest.doc(objectMapper.writeValueAsBytes(tag), XContentType.JSON).upsert(indexRequest);
            }
            updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            UpdateResponse updateResponse = client.update(updateRequest, RequestOptions.DEFAULT);
            /// verify the creation of the tag
            Result result = updateResponse.getResult();
            if (result.equals(Result.CREATED) || result.equals(Result.UPDATED) || result.equals(Result.NOOP)) {
                // client.get(, options)
                return (S) findById(tag.getName()).get();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update/save tag" + tag, null);
        }
        return null;
    }

    @Override
    public <S extends XmlTag> Iterable<S> saveAll(Iterable<S> tags) {
        
    	RestHighLevelClient client = esService.getIndexClient();
        BulkRequest bulkRequest = new BulkRequest();
        try {
            for (XmlTag tag : tags) {
                UpdateRequest updateRequest = new UpdateRequest(ES_TAG_INDEX, ES_TAG_TYPE, tag.getName());

                Optional<XmlTag> existingTag = findById(tag.getName());
                if (existingTag.isPresent()) {
                	XmlTag newTag = existingTag.get();
                    updateRequest.doc(objectMapper.writeValueAsBytes(newTag), XContentType.JSON);
                } else {
                	IndexRequest indexRequest = new IndexRequest(ES_TAG_INDEX, ES_TAG_TYPE).id(tag.getName())
                            .source(objectMapper.writeValueAsBytes(tag), XContentType.JSON);
                    updateRequest.doc(objectMapper.writeValueAsBytes(tag), XContentType.JSON).upsert(indexRequest);
                }
                bulkRequest.add(updateRequest);
            }

            bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
            if (bulkResponse.hasFailures()) {
                // Failed to create/update all the tags

            } else {
                List<String> createdTagIds = new ArrayList<String>();
                for (BulkItemResponse bulkItemResponse : bulkResponse) {
                    Result result = bulkItemResponse.getResponse().getResult();
                    if (result.equals(Result.CREATED) || result.equals(Result.UPDATED) || result.equals(Result.NOOP)) {
                        createdTagIds.add(bulkItemResponse.getId());
                    }
                }
                return (Iterable<S>) findAllById(createdTagIds);
            }
        } catch (Exception e) {
        	e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update/save tags" + tags, null);
        }
        return null;
    }

    @Override
    public Optional<XmlTag> findById(String id) {
        return findById(id, false);
    }

    public Optional<XmlTag> findById(String id, boolean withChannels) {
        RestHighLevelClient client = esService.getSearchClient();
        GetRequest getRequest = new GetRequest(ES_TAG_INDEX, ES_TAG_TYPE, id);
        try {
            GetResponse response = client.get(getRequest, RequestOptions.DEFAULT);
            if (response.isExists()) {
                XmlTag tag = objectMapper.readValue(response.getSourceAsBytesRef().streamInput(), XmlTag.class);
                return Optional.of(tag);
            }
        } catch (IOException e) {
        	e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to find tag by id" + id, null);
        }
        return Optional.empty();
    }

    @Override
    public boolean existsById(String id) {

    	RestHighLevelClient client = esService.getSearchClient();
    	GetRequest getRequest = new GetRequest(ES_TAG_INDEX, ES_TAG_TYPE, id);
    	getRequest.fetchSourceContext(new FetchSourceContext(false));
    	getRequest.storedFields("_none_");
    	try {
    		return client.exists(getRequest, RequestOptions.DEFAULT);
    	} catch (IOException e) {
    		e.printStackTrace();
    		throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
    				"Failed to check if tag exists by id" + id, null); 
    	}
    }

    @Override
    public Iterable<XmlTag> findAll() {

        RestHighLevelClient client = esService.getSearchClient();
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(ES_TAG_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // TODO use of scroll will be necessary
        searchSourceBuilder.size(10000);
        searchRequest.source(searchSourceBuilder.query(QueryBuilders.matchAllQuery()));
        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            if (searchResponse.status().equals(RestStatus.OK)) {
                List<XmlTag> result = new ArrayList<XmlTag>();
                for (SearchHit hit : searchResponse.getHits()) {
                    result.add(objectMapper.readValue(hit.getSourceRef().streamInput(), XmlTag.class));
                }
                return result;
            }
        } catch (IOException e) {
        	e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fihd all tags", null);
        }
        return null;
    }

    @Override
    public Iterable<XmlTag> findAllById(Iterable<String> ids) {

        MultiGetRequest request = new MultiGetRequest();
        for (String id : ids) {
            request.add(new MultiGetRequest.Item(ES_TAG_INDEX, ES_TAG_TYPE, id));
        }
        try {
            List<XmlTag> foundTags = new ArrayList<XmlTag>();
            MultiGetResponse response = esService.getSearchClient().mget(request, RequestOptions.DEFAULT);
            for (MultiGetItemResponse multiGetItemResponse : response) {
                if (!multiGetItemResponse.isFailed()) {
                    foundTags.add(objectMapper.readValue(
                            multiGetItemResponse.getResponse().getSourceAsBytesRef().streamInput(), XmlTag.class));
                } else {
                    // failed to fetch all the listed tags
                }
            }
            return foundTags;
        } catch (IOException e) {
        	e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to find all tags by ids" + ids, null);
        }
    }

    @Override
    public long count() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void deleteById(String tag) {
        RestHighLevelClient client = esService.getIndexClient();
        DeleteRequest request = new DeleteRequest(ES_TAG_INDEX, ES_TAG_TYPE, tag);
        try {
            DeleteResponse response = client.delete(request, RequestOptions.DEFAULT);
            Result result = response.getResult();
            if (!result.equals(Result.DELETED)) {
                // Failed to delete the requested tag
            }
        } catch (IOException e) {
        	e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to index tag" + tag, null);
        }
    }

    @Override
    public void delete(XmlTag tag) {
        deleteById(tag.getName());
    }

    @Override
    public void deleteAll(Iterable<? extends XmlTag> entities) {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteAll() {
        // TODO Auto-generated method stub

    }

}
