package com.lcg.messenger.service;

import com.lcg.messenger.async.AsyncHelper;
import com.lcg.messenger.async.AsyncProcessor;
import com.lcg.messenger.async.DemoAsyncService;
import com.lcg.messenger.data.DataProvider;
import com.lcg.messenger.data.RequestValidator;
import com.lcg.messenger.util.Util;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.ContextURL.Suffix;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.prefer.Preferences;
import org.apache.olingo.server.api.prefer.PreferencesApplied;
import org.apache.olingo.server.api.processor.EntityProcessor;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public class DemoEntityProcessor implements EntityProcessor {

    protected final DataProvider dataprovider;
    protected OData odata;
    protected ServiceMetadata serviceMetadata;

    public DemoEntityProcessor(DataProvider dataprovider) {
        System.out.println("@DemoEntityProcessor");
        this.dataprovider = dataprovider;
    }

    public void init(OData odata, ServiceMetadata serviceMetadata) {
        System.out.println("init() @DemoEntityProcessor");
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }


    public void readEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        System.out.println("readEntity() @DemoEntityProcessor");
        // The sample service supports only functions imports and entity sets.
        // We do not care about bound functions and composable functions.
        Instant start = Instant.now();
        UriResource uriResource = uriInfo.getUriResourceParts().get(0);

        if (odata.createPreferences(request.getHeaders(HttpHeader.PREFER)).hasRespondAsync()) {
            System.out.println("hasRespondAsync in readEntity");
            DemoAsyncService asyncService = DemoAsyncService.getInstance();
            DemoEntityProcessor processor = new DemoEntityProcessor(dataprovider);
            processor.init(odata, serviceMetadata);
            AsyncProcessor<EntityProcessor> asyncProcessor = asyncService.register(processor, EntityProcessor.class);
            asyncProcessor.prepareFor().readEntity(request, response, uriInfo, responseFormat);
            String location;
            try {
                location = asyncProcessor.processAsync();
                AsyncHelper.wait(odata, request, response, location, start, responseFormat);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (uriResource instanceof UriResourceEntitySet) {
            readEntityInternal(request, response, uriInfo, responseFormat);
        } else if (uriResource instanceof UriResourceFunction) {
            readFunctionImportInternal(request, response, uriInfo, responseFormat);
        } else {
            throw new ODataApplicationException("Only EntitySet is supported", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }
    }


    public void readEntityInternal(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, SerializerException {
        System.out.println("readEntityInternal() @DemoEntityProcessor");
        EdmEntityType responseEdmEntityType = null; // we'll need this to build the ContextURL
        Entity responseEntity = null; // required for serialization of the response body
        EdmEntitySet responseEdmEntitySet = null; // we need this for building the contextUrl

        // 1st step: retrieve the requested Entity: can be "normal" read operation, or navigation (to-one)
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        int segmentCount = resourceParts.size();

        UriResource uriResource = resourceParts.get(0); // in our example, the first segment is the EntitySet
        if (!(uriResource instanceof UriResourceEntitySet)) {
            throw new ODataApplicationException("Only EntitySet is supported", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }

        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResource;
        EdmEntitySet startEdmEntitySet = uriResourceEntitySet.getEntitySet();

        // Analyze the URI segments
        if (segmentCount == 1) { // no navigation
            responseEdmEntityType = startEdmEntitySet.getEntityType();
            responseEdmEntitySet = startEdmEntitySet; // since we have only one segment

            // 2. step: retrieve the data from backend
            List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
            responseEntity = dataprovider.readEntityData(startEdmEntitySet, keyPredicates);
        } else if (segmentCount == 2) { // navigation
            UriResource navSegment = resourceParts.get(1); // in our example we don't support more complex URIs
            if (navSegment instanceof UriResourceNavigation) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) navSegment;
                EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                responseEdmEntityType = edmNavigationProperty.getType();
                // contextURL displays the last segment
                responseEdmEntitySet = Util.getNavigationTargetEntitySet(startEdmEntitySet, edmNavigationProperty);

                // 2nd: fetch the data from backend.
                // e.g. for the URI: Reviews(1)/Category we have to find the correct Category entity
                List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
                // e.g. for Reviews(1)/Category we have to find first the Reviews(1)
                Entity sourceEntity = dataprovider.readEntityData(startEdmEntitySet, keyPredicates);

                // now we have to check if the navigation is
                // a) to-one: e.g. Reviews(1)/Category
                // b) to-many with key: e.g. Profiles(3)/Reviews(5)
                // the key for nav is used in this case: Profiles(3)/Reviews(5)
                List<UriParameter> navKeyPredicates = uriResourceNavigation.getKeyPredicates();

                if (navKeyPredicates.isEmpty()) { // e.g. DemoService.svc/Reviews(1)/Category
                    responseEntity = dataprovider.getRelatedEntity(sourceEntity, responseEdmEntityType);
                } else { // e.g. DemoService.svc/Profiles(3)/Reviews(5)
                    responseEntity = dataprovider.getRelatedEntity(sourceEntity, responseEdmEntityType, navKeyPredicates);
                }
            }
        } else {
            // this would be the case for e.g. Reviews(1)/Category/Reviews(1)/Category
            throw new ODataApplicationException("Not supported", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
        }

        if (responseEntity == null) {
            // this is the case for e.g. DemoService.svc/Profiles(4) or DemoService.svc/Profiles(3)/Reviews(999)
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }

        List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
        Entity entity = dataprovider.readEntityData(responseEdmEntitySet, keyPredicates);

        // 3. apply system query options

        // handle $select
        SelectOption selectOption = uriInfo.getSelectOption();
        // in our example, we don't have performance issues, so we can rely upon the handling in the Olingo lib
        // nothing else to be done

        // handle $expand
        ExpandOption expandOption = uriInfo.getExpandOption();
        // in our example: http://localhost:8080/DemoService/DemoService.svc/Profiles(1)/$expand=Reviews
        // or http://localhost:8080/DemoService/DemoService.svc/Reviews(1)?$expand=Category
        if (expandOption != null) {
            // retrieve the EdmNavigationProperty from the expand expression
            // Note: in our example, we have only one NavigationProperty, so we can directly access it
            EdmNavigationProperty edmNavigationProperty = null;
            ExpandItem expandItem = expandOption.getExpandItems().get(0);
            if (expandItem.isStar()) {
                List<EdmNavigationPropertyBinding> bindings = responseEdmEntitySet.getNavigationPropertyBindings();
                // we know that there are navigation bindings
                // however normally in this case a check if navigation bindings exists is done
                if (!bindings.isEmpty()) {
                    // can in our case only be 'Category' or 'Reviews', so we can take the first
                    EdmNavigationPropertyBinding binding = bindings.get(0);
                    EdmElement property = responseEdmEntitySet.getEntityType().getProperty(binding.getPath());
                    // we don't need to handle error cases, as it is done in the Olingo library
                    if (property instanceof EdmNavigationProperty) {
                        edmNavigationProperty = (EdmNavigationProperty) property;
                    }
                }
            } else {
                // can be 'Category' or 'Reviews', no path supported
                uriResource = expandItem.getResourcePath().getUriResourceParts().get(0);
                // we don't need to handle error cases, as it is done in the Olingo library
                if (uriResource instanceof UriResourceNavigation) {
                    edmNavigationProperty = ((UriResourceNavigation) uriResource).getProperty();
                }
            }

            // can be 'Category' or 'Reviews', no path supported
            // we don't need to handle error cases, as it is done in the Olingo library
            if (edmNavigationProperty != null) {
                EdmEntityType expandEdmEntityType = edmNavigationProperty.getType();
                String navPropName = edmNavigationProperty.getName();

                // build the inline data
                Link link = new Link();
                link.setTitle(navPropName);
                link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
                link.setRel(Constants.NS_ASSOCIATION_LINK_REL + navPropName);

                if (edmNavigationProperty.isCollection()) { // in case of Profiles(1)/$expand=Reviews
                    // fetch the data for the $expand (to-many navigation) from backend
                    // here we get the data for the expand
                    EntityCollection expandEntityCollection = dataprovider.getRelatedEntityCollection(entity, expandEdmEntityType);
                    link.setInlineEntitySet(expandEntityCollection);
                    link.setHref(expandEntityCollection.getId().toASCIIString());
                } else {  // in case of Reviews(1)?$expand=Category
                    // fetch the data for the $expand (to-one navigation) from backend
                    // here we get the data for the expand
                    Entity expandEntity = dataprovider.getRelatedEntity(entity, expandEdmEntityType);
                    link.setInlineEntity(expandEntity);
                    link.setHref(expandEntity.getId().toASCIIString());
                }

                // set the link - containing the expanded data - to the current entity
                entity.getNavigationLinks().add(link);
            }
        }

        EdmEntityType edmEntityType = responseEdmEntitySet.getEntityType();
        String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType, expandOption, selectOption);
        // 3. serialize
        ContextURL contextUrl = ContextURL.with().entitySet(responseEdmEntitySet).selectList(selectList).suffix(Suffix.ENTITY).build();
        EntitySerializerOptions opts = EntitySerializerOptions.with().contextURL(contextUrl).select(selectOption).expand(expandOption).build();

        ODataSerializer serializer = this.odata.createSerializer(responseFormat);
        SerializerResult serializerResult = serializer.entity(this.serviceMetadata, responseEdmEntityType, responseEntity, opts);

        // 4. configure the response object
        response.setContent(serializerResult.getContent());
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }

    private void readFunctionImportInternal(final ODataRequest request, final ODataResponse response, final UriInfo uriInfo, final ContentType responseFormat) throws ODataApplicationException, SerializerException {
        System.out.println("readFunctionImportInternal() @DemoEntityProcessor");
        // 1st step: Analyze the URI and fetch the entity returned by the function import
        // Function Imports are always the first segment of the resource path
        final UriResource firstSegment = uriInfo.getUriResourceParts().get(0);

        if (!(firstSegment instanceof UriResourceFunction)) {
            throw new ODataApplicationException("Not implemented", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }

        final UriResourceFunction uriResourceFunction = (UriResourceFunction) firstSegment;
        final Entity entity = dataprovider.readFunctionImportEntity(uriResourceFunction, serviceMetadata);

        if (entity == null) {
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }

        // 2nd step: Serialize the response entity
        final EdmEntityType edmEntityType = (EdmEntityType) uriResourceFunction.getFunction().getReturnType().getType();
        final ContextURL contextURL = ContextURL.with().type(edmEntityType).build();
        final EntitySerializerOptions opts = EntitySerializerOptions.with().contextURL(contextURL).build();
        final ODataSerializer serializer = odata.createSerializer(responseFormat);
        final SerializerResult serializerResult = serializer.entity(serviceMetadata, edmEntityType, entity, opts);

        // 3rd configure the response object
        response.setContent(serializerResult.getContent());
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }


    @Override
    public void createEntity(final ODataRequest request, final ODataResponse response, final UriInfo uriInfo, final ContentType requestFormat, final ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        System.out.println("createEntity() @DemoEntityProcessor");
        Instant start = Instant.now();
        if (uriInfo.asUriInfoResource().getUriResourceParts().size() > 1) {
            throw new ODataApplicationException("Invalid resource type.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
        }

        if (odata.createPreferences(request.getHeaders(HttpHeader.PREFER)).hasRespondAsync()) {
            System.out.println("hasRespondAsync in createEntity");
            DemoAsyncService asyncService = DemoAsyncService.getInstance();
            DemoEntityProcessor processor = new DemoEntityProcessor(dataprovider);
            processor.init(odata, serviceMetadata);
            AsyncProcessor<EntityProcessor> asyncProcessor = asyncService.register(processor, EntityProcessor.class);
            asyncProcessor.prepareFor().createEntity(request, response, uriInfo, requestFormat, responseFormat);
            String location = null;
            try {
                location = asyncProcessor.processAsync();
                AsyncHelper.wait(odata, request, response, location, start, responseFormat);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        final UriResourceEntitySet resourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
        final EdmEntitySet edmEntitySet = resourceEntitySet.getEntitySet();
        final EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        final Entity entity;
        ExpandOption expand = null;

        final DeserializerResult deserializerResult = odata.createDeserializer(requestFormat).entity(request.getBody(), edmEntityType);
        new RequestValidator(dataprovider, request.getRawBaseUri()).validate(edmEntitySet, deserializerResult.getEntity());
        Entity entityResult = deserializerResult.getEntity();
        List<UriParameter> keyPredicates = resourceEntitySet.getKeyPredicates();
        entity = dataprovider.createEntityData(edmEntitySet, entityResult);
        HttpMethod httpMethod = request.getMethod();
        dataprovider.updateEntityData(edmEntitySet, keyPredicates, entityResult, httpMethod);
        expand = deserializerResult.getExpandTree();


        final String location = request.getRawBaseUri() + '/' + odata.createUriHelper().buildCanonicalURL(edmEntitySet, entity);
        final Preferences.Return returnPreference = odata.createPreferences(request.getHeaders(HttpHeader.PREFER)).getReturn();
        if (returnPreference == null || returnPreference == Preferences.Return.REPRESENTATION) {

            ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).build();
            EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).build(); // expand and select currently not supported

            ODataSerializer serializer = this.odata.createSerializer(responseFormat);
            SerializerResult serializedResponse = serializer.entity(serviceMetadata, edmEntityType, entity, options);

            // 3rd configure the response object
            response.setContent(serializedResponse.getContent());

            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
            response.setStatusCode(HttpStatusCode.CREATED.getStatusCode());
        } else {
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            response.setHeader(HttpHeader.ODATA_ENTITY_ID, location);
        }
        if (returnPreference != null) {
            response.setHeader(HttpHeader.PREFERENCE_APPLIED, PreferencesApplied.with().returnRepresentation(returnPreference).build().toValueString());
        }
        response.setHeader(HttpHeader.LOCATION, location);
        if (entity.getETag() != null) {
            response.setHeader(HttpHeader.ETAG, entity.getETag());
        }
    }

    public void updateEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        System.out.println("updateEntity() @DemoEntityProcessor");
        Instant start = Instant.now();

        if (odata.createPreferences(request.getHeaders(HttpHeader.PREFER)).hasRespondAsync()) {
            System.out.println("hasRespondAsync in updateEntity");
            DemoAsyncService asyncService = DemoAsyncService.getInstance();
            DemoEntityProcessor processor = new DemoEntityProcessor(dataprovider);
            processor.init(odata, serviceMetadata);
            AsyncProcessor<EntityProcessor> asyncProcessor = asyncService.register(processor, EntityProcessor.class);
            asyncProcessor.prepareFor().updateEntity(request, response, uriInfo, requestFormat, responseFormat);
            String location;
            try {
                location = asyncProcessor.processAsync();
                AsyncHelper.wait(odata, request, response, location, start, responseFormat);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }


        // 1. Retrieve the entity set which belongs to the requested entity
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        // Note: only in our example we can assume that the first segment is the EntitySet
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        // 2. update the data in backend
        // 2.1. retrieve the payload from the PUT request for the entity to be updated
        InputStream requestInputStream = request.getBody();
        ODataDeserializer deserializer = this.odata.createDeserializer(requestFormat);
        DeserializerResult result = deserializer.entity(requestInputStream, edmEntityType);
        Entity requestEntity = result.getEntity();
        // 2.2 do the modification in backend
        List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
        // Note that this updateEntity()-method is invoked for both PUT or PATCH operations
        HttpMethod httpMethod = request.getMethod();
        dataprovider.updateEntityData(edmEntitySet, keyPredicates, requestEntity, httpMethod);

        //3. configure the response object
        response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    }


    public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException, ODataLibraryException {
        System.out.println("deleteEntity() @DemoEntityProcessor");
        Instant start = Instant.now();
        ContentType responseFormat = ContentType.TEXT_PLAIN;
        if (odata.createPreferences(request.getHeaders(HttpHeader.PREFER)).hasRespondAsync()) {
            System.out.println("hasRespondAsync in deleteEntity");
            DemoAsyncService asyncService = DemoAsyncService.getInstance();
            DemoEntityProcessor processor = new DemoEntityProcessor(dataprovider);
            processor.init(odata, serviceMetadata);
            AsyncProcessor<EntityProcessor> asyncProcessor = asyncService.register(processor, EntityProcessor.class);  //register the processor in async service
            asyncProcessor.prepareFor().deleteEntity(request, response, uriInfo);
            String location;
            try {
                location = asyncProcessor.processAsync();
                AsyncHelper.wait(odata, request, response, location, start, responseFormat);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        // 1. Retrieve the entity set which belongs to the requested entity
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        // Note: only in our example we can assume that the first segment is the EntitySet
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

        // 2. delete the data in backend
        List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
        dataprovider.deleteEntityData(edmEntitySet, keyPredicates);

        //3. configure the response object
        response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    }
}
