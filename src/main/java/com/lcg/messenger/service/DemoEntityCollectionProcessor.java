package com.lcg.messenger.service;

import com.lcg.messenger.async.AsyncHelper;
import com.lcg.messenger.async.AsyncProcessor;
import com.lcg.messenger.async.DemoAsyncService;
import com.lcg.messenger.data.DataProvider;
import com.lcg.messenger.util.Util;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.CountEntityCollectionProcessor;
import org.apache.olingo.server.api.processor.EntityCollectionProcessor;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

public class DemoEntityCollectionProcessor implements EntityCollectionProcessor, CountEntityCollectionProcessor {


    private OData odata;
    private ServiceMetadata serviceMetadata;
    private DataProvider dataprovider;

    public DemoEntityCollectionProcessor(DataProvider dataprovider) {
        System.out.println("@DemoEntityCollectionProcessor");
        this.dataprovider = dataprovider;
    }

    public void init(OData odata, ServiceMetadata serviceMetadata) {
        System.out.println("init() @DemoEntityCollectionProcessor");
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }


    public void readEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        System.out.println("readEntityCollection() @DemoEntityCollectionProcessor");
        Instant start = Instant.now();

        if (odata.createPreferences(request.getHeaders(HttpHeader.PREFER)).hasRespondAsync()) {
            System.out.println("hasRespondAsync in readEntityCollection");
            DemoEntityCollectionProcessor processor = new DemoEntityCollectionProcessor(dataprovider);
            processor.init(odata, serviceMetadata);
            DemoAsyncService asyncService = DemoAsyncService.getInstance();
            AsyncProcessor<EntityCollectionProcessor> asyncProcessor = asyncService.register(processor, EntityCollectionProcessor.class);
            asyncProcessor.prepareFor().readEntityCollection(request, response, uriInfo, responseFormat);
            String location;
            try {
                location = asyncProcessor.processAsync();
                AsyncHelper.wait(odata, request, response, location, start, responseFormat);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        final UriResource firstResourceSegment = uriInfo.getUriResourceParts().get(0);

        if (firstResourceSegment instanceof UriResourceEntitySet) {
            readEntityCollectionInternal(request, response, uriInfo, responseFormat);
        } else if (firstResourceSegment instanceof UriResourceFunction) {
            readFunctionImportCollection(request, response, uriInfo, responseFormat);
        } else {
            throw new ODataApplicationException("Not implemented", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }
    }


    private void readFunctionImportCollection(final ODataRequest request, final ODataResponse response, final UriInfo uriInfo, final ContentType responseFormat) throws ODataApplicationException, SerializerException {
        System.out.println("readFunctionImportCollection() @DemoEntityCollectionProcessor");
        // 1st step: Analyze the URI and fetch the entity collection returned by the function import
        // Function Imports are always the first segment of the resource path
        final UriResource firstSegment = uriInfo.getUriResourceParts().get(0);

        if (!(firstSegment instanceof UriResourceFunction)) {
            throw new ODataApplicationException("Not implemented", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }

        final UriResourceFunction uriResourceFunction = (UriResourceFunction) firstSegment;
        final EntityCollection entityCol = dataprovider.readFunctionImportCollection(uriResourceFunction, serviceMetadata);

        // 2nd step: Serialize the response entity
        final EdmEntityType edmEntityType = (EdmEntityType) uriResourceFunction.getFunction().getReturnType().getType();
        final ContextURL contextURL = ContextURL.with().asCollection().type(edmEntityType).build();
        EntityCollectionSerializerOptions opts = EntityCollectionSerializerOptions.with().contextURL(contextURL).build();
        final ODataSerializer serializer = odata.createSerializer(responseFormat);
        final SerializerResult serializerResult = serializer.entityCollection(serviceMetadata, edmEntityType, entityCol, opts);

        // 3rd configure the response object
        response.setContent(serializerResult.getContent());
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }


    public void readEntityCollectionInternal(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, SerializerException {
        System.out.println("readEntityCollectionInternal() @DemoEntityCollectionProcessor");
        EdmEntitySet edmEntitySet = null; // we'll need this to build the ContextURL
        EntityCollection entityCollection = null; // we'll need this to set the response body

        // 1st retrieve the requested EntitySet from the uriInfo (representation of the parsed URI)
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        int segmentCount = resourceParts.size();

        UriResource uriResource = resourceParts.get(0); // in our example, the first segment is the EntitySet
        if (!(uriResource instanceof UriResourceEntitySet)) {
            throw new ODataApplicationException("Only EntitySet is supported", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
        }

        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResource;
        EdmEntitySet startEdmEntitySet = uriResourceEntitySet.getEntitySet();


        // 3rd: apply system query options
        SelectOption selectOption = uriInfo.getSelectOption();
        ExpandOption expandOption = uriInfo.getExpandOption();


        if (segmentCount == 1) {              // this is the case for: DemoService/DemoService.svc/Profiles
            edmEntitySet = startEdmEntitySet; // the response body is built from the first (and only) entitySet

            // 2nd: fetch the data from backend for this requested EntitySetName and deliver as EntitySet
            entityCollection = dataprovider.readEntitySetData(startEdmEntitySet);

        } else if (segmentCount == 2) {                                 // in case of navigation: DemoService.svc/Profiles(3)/Reviews

            UriResource lastSegment = resourceParts.get(1);             // in our example we don't support more complex URIs
            if (lastSegment instanceof UriResourceNavigation) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) lastSegment;
                EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                EdmEntityType targetEntityType = edmNavigationProperty.getType();
                // from Profiles(1) to Reviews
                edmEntitySet = Util.getNavigationTargetEntitySet(startEdmEntitySet, edmNavigationProperty);

                // 2nd: fetch the data from backend
                // first fetch the entity where the first segment of the URI points to
                List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
                // e.g. for Profiles(3)/Reviews we have to find the single entity: Category with ID 3
                Entity sourceEntity = dataprovider.readEntityData(startEdmEntitySet, keyPredicates);
                // error handling for e.g. DemoService.svc/Profiles(99)/Reviews
                if (sourceEntity == null) {
                    throw new ODataApplicationException("Entity not found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
                }
                // then fetch the entity collection where the entity navigates to
                // note: we don't need to check uriResourceNavigation.isCollection(),
                // because we are the EntityCollectionProcessor
                entityCollection = dataprovider.getRelatedEntityCollection(sourceEntity, targetEntityType);


            }
        }


        // 3rd: Check if filter system query option is provided and apply the expression if necessary
        FilterOption filterOption = uriInfo.getFilterOption();
        if (filterOption != null) {
            // Apply $filter system query option
            try {
                List<Entity> entityList = entityCollection.getEntities();
                Iterator<Entity> entityIterator = entityList.iterator();

                // Evaluate the expression for each entity
                // If the expression is evaluated to "true", keep the entity otherwise remove it from the entityList
                while (entityIterator.hasNext()) {
                    // To evaluate the the expression, create an instance of the Filter Expression Visitor and pass
                    // the current entity to the constructor
                    Entity currentEntity = entityIterator.next();
                    Expression filterExpression = filterOption.getExpression();
                    FilterExpressionVisitor expressionVisitor = new FilterExpressionVisitor(currentEntity);

                    // Start evaluating the expression
                    Object visitorResult = filterExpression.accept(expressionVisitor);

                    // The result of the filter expression must be of type Edm.Boolean
                    if (visitorResult instanceof Boolean) {
                        if (!Boolean.TRUE.equals(visitorResult)) {
                            // The expression evaluated to false (or null), so we have to remove the currentEntity from entityList
                            entityIterator.remove();
                        }
                    } else {
                        throw new ODataApplicationException("A filter expression must evaluate to type Edm.Boolean", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
                    }
                }

            } catch (ExpressionVisitException e) {
                throw new ODataApplicationException("Exception in filter evaluation", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
            }
        }


        // handle $expand
        // in our example: http://localhost:8080/DemoService/DemoService.svc/Profiles/$expand=Reviews
        // or http://localhost:8080/DemoService/DemoService.svc/Reviews?$expand=Category
        if (expandOption != null) {
            // retrieve the EdmNavigationProperty from the expand expression
            // Note: in our example, we have only one NavigationProperty, so we can directly access it
            EdmNavigationProperty edmNavigationProperty = null;
            ExpandItem expandItem = expandOption.getExpandItems().get(0);
            if (expandItem.isStar()) {
                List<EdmNavigationPropertyBinding> bindings = edmEntitySet.getNavigationPropertyBindings();
                // we know that there are navigation bindings
                // however normally in this case a check if navigation bindings exists is done
                if (!bindings.isEmpty()) {
                    // can in our case only be 'Category' or 'Reviews', so we can take the first
                    EdmNavigationPropertyBinding binding = bindings.get(0);
                    EdmElement property = edmEntitySet.getEntityType().getProperty(binding.getPath());
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
                String navPropName = edmNavigationProperty.getName();
                EdmEntityType expandEdmEntityType = edmNavigationProperty.getType();

                List<Entity> entityList = entityCollection.getEntities();
                for (Entity entity : entityList) {
                    Link link = new Link();
                    link.setTitle(navPropName);
                    link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
                    link.setRel(Constants.NS_ASSOCIATION_LINK_REL + navPropName);

                    if (edmNavigationProperty.isCollection()) { // in case of Profiles/$expand=Reviews
                        // fetch the data for the $expand (to-many navigation) from backend
                        EntityCollection expandEntityCollection = dataprovider.getRelatedEntityCollection(entity, expandEdmEntityType);
                        link.setInlineEntitySet(expandEntityCollection);
                        link.setHref(expandEntityCollection.getId().toASCIIString());
                    } else { // in case of Reviews?$expand=Category
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
        }

        List<Entity> entityList = entityCollection.getEntities();               //These two are important for all the bellow operations.Finally an entity list is returned which is add
        EntityCollection returnEntityCollection = new EntityCollection();
        // 3rd apply $orderBy
        OrderByOption orderByOption = uriInfo.getOrderByOption();
        if (orderByOption != null) {
            List<OrderByItem> orderItemList = orderByOption.getOrders();
            final OrderByItem orderByItem = orderItemList.get(0);               // in our example we support only one
            Expression expression = orderByItem.getExpression();
            if (expression instanceof Member) {
                UriInfoResource resourcePath = ((Member) expression).getResourcePath();
                uriResource = resourcePath.getUriResourceParts().get(0);
                if (uriResource instanceof UriResourcePrimitiveProperty) {
                    EdmProperty edmProperty = ((UriResourcePrimitiveProperty) uriResource).getProperty();
                    final String sortPropertyName = edmProperty.getName();

                    // do the sorting for the list of entities
                    Collections.sort(entityList, new Comparator<Entity>() {

                        // we delegate the sorting to the native sorter of Integer and String
                        public int compare(Entity entity1, Entity entity2) {
                            int compareResult = 0;

                            if (sortPropertyName.equals("ID")) {
                                Integer integer1 = (Integer) entity1.getProperty(sortPropertyName).getValue();
                                Integer integer2 = (Integer) entity2.getProperty(sortPropertyName).getValue();

                                compareResult = integer1.compareTo(integer2);
                            } else {
                                String propertyValue1 = (String) entity1.getProperty(sortPropertyName).getValue();
                                String propertyValue2 = (String) entity2.getProperty(sortPropertyName).getValue();

                                compareResult = propertyValue1.compareTo(propertyValue2);
                            }

                            // if 'desc' is specified in the URI, change the order of the list
                            if (orderByItem.isDescending()) {
                                return -compareResult; // just convert the result to negative value to change the order
                            }

                            return compareResult;
                        }
                    });
                }
            }
        }


        // 3rd: apply System Query Options
        // modify the result set according to the query options, specified by the end user

        // handle $count: always return the original number of entities, without considering $top and $skip
        CountOption countOption = uriInfo.getCountOption();
        if (countOption != null) {
            boolean isCount = countOption.getValue();
            if (isCount) {
                System.out.println("Count is " + entityList.size());
                returnEntityCollection.setCount(entityList.size());
            }
        }

        // handle $skip
        SkipOption skipOption = uriInfo.getSkipOption();
        if (skipOption != null) {
            int skipNumber = skipOption.getValue();
            if (skipNumber >= 0) {
                if (skipNumber <= entityList.size()) {
                    entityList = entityList.subList(skipNumber, entityList.size());
                } else {
                    // The client skipped all entities
                    entityList.clear();
                }
            } else {
                throw new ODataApplicationException("Invalid value for $skip", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ROOT);
            }
        }

        // handle $top
        TopOption topOption = uriInfo.getTopOption();
        if (topOption != null) {
            int topNumber = topOption.getValue();
            if (topNumber >= 0) {
                if (topNumber <= entityList.size()) {
                    entityList = entityList.subList(0, topNumber);
                }  // else the client has requested more entities than available => return what we have
            } else {
                throw new ODataApplicationException("Invalid value for $top", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ROOT);
            }
        }

        // after applying the system query options, create the EntityCollection based on the reduced list
        for (Entity entity : entityList) {
            returnEntityCollection.getEntities().add(entity);
        }

        // 4th: create a serializer based on the requested format (json)
        ODataSerializer serializer = odata.createSerializer(responseFormat);

        // and serialize the content: transform from the EntitySet object to InputStream
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType, expandOption, selectOption);
        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).selectList(selectList).build();

        final String id = request.getRawBaseUri() + "/" + edmEntitySet.getName();
        EntityCollectionSerializerOptions opts = EntityCollectionSerializerOptions.with().contextURL(contextUrl).select(selectOption).expand(expandOption).id(id).count(countOption).build();
        SerializerResult serializerResult = serializer.entityCollection(serviceMetadata, edmEntityType, returnEntityCollection, opts);
        InputStream serializedContent = serializerResult.getContent();

        // 5th: configure the response object: set the body, headers and status code
        response.setContent(serializedContent);
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }

    /**
     * Counts entities from persistence and puts serialized content and status into the response.
     * Response content type is <code>text/plain</code> by default.
     *
     * @param request  OData request object containing raw HTTP information.
     * @param response OData response object for collecting response data
     * @param uriInfo  information of a parsed OData URI
     * @throws ODataApplicationException if the service implementation encounters a failure
     * @throws ODataLibraryException
     */
    @Override
    public void countEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException, ODataLibraryException {
        System.out.println("countEntityCollection() @DemoEntityCollectionProcessor");
        // 1st we have retrieve the requested EntitySet from the uriInfo object (representation of the parsed service URI)
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0); // in our example, the first segment is the EntitySet
        System.out.println("uri resource set " + uriResourceEntitySet);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();                         //find what is the entity set Messages or Comments
        System.out.println("edm entity set " + edmEntitySet.getName());                          //matched entity set

        // 2nd: fetch the data from backend for this requested EntitySetName                     // it has to be delivered as EntitySet object
        EntityCollection entitySet = dataprovider.readEntitySetData(edmEntitySet);               //fetch data messages or comments

        // 3rd: apply System Query Options
        // modify the result set according to the query options, specified by the end user
        List<Entity> entityList = entitySet.getEntities();
        EntityCollection returnEntityCollection = new EntityCollection();                        //returning list

        // handle $count: always return the original number of entities, without considering $top and $skip
        CountOption countOption = uriInfo.getCountOption();                                      //get count option
        if (countOption != null) {
            boolean isCount = countOption.getValue();
            System.out.println("count is " + isCount);
            if (isCount) {
                returnEntityCollection.setCount(entityList.size());
            }
        }
        InputStream is = new ByteArrayInputStream(new String(entityList.size() + "").getBytes());
        response.setContent(is);
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
    }
}