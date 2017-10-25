package com.lcg.messenger.service;

import com.lcg.messenger.data.DataProvider;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.processor.PrimitiveProcessor;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.PrimitiveSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class DemoPrimitiveProcessor implements PrimitiveProcessor {

    private OData odata;
    private DataProvider dataprovider;
    private ServiceMetadata serviceMetadata;

    public DemoPrimitiveProcessor(DataProvider dataprovider) {
        System.out.println("@DemoPrimitiveProcessor");
        this.dataprovider = dataprovider;
    }

    public void init(OData odata, ServiceMetadata serviceMetadata) {
        System.out.println("init() @DemoPrimitiveProcessor");
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;

    }

    /*
     * In our example, the URL would be: http://localhost:8080/DemoService.svc/Reviews(1)/Name
     * and the response:
     * {
     * 
     * @odata.context: "$metadata#Reviews/Name",
     * value: "Nimasha Hashani"
     * }
     */
    public void readPrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, SerializerException {
        System.out.println("readPrimitive() @DemoPrimitiveProcessor");
        // 1. Retrieve info from URI
        // 1.1. retrieve the info about the requested entity set
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        // Note: only in our example we can rely that the first segment is the EntitySet
        UriResourceEntitySet uriEntitySet = (UriResourceEntitySet) resourceParts.get(0);
        EdmEntitySet edmEntitySet = uriEntitySet.getEntitySet();
        // the key for the entity
        List<UriParameter> keyPredicates = uriEntitySet.getKeyPredicates();

        // 1.2. retrieve the requested (Edm) property
        // the last segment is the Property
        UriResourceProperty uriProperty = (UriResourceProperty) resourceParts.get(resourceParts.size() - 1);
        EdmProperty edmProperty = uriProperty.getProperty();
        String edmPropertyName = edmProperty.getName();
        // in our example, we know we have only primitive types in our model
        EdmPrimitiveType edmPropertyType = (EdmPrimitiveType) edmProperty.getType();

        // 2. retrieve data from backend
        // 2.1. retrieve the entity data, for which the property has to be read
        Entity entity = dataprovider.readEntityData(edmEntitySet, keyPredicates);
        if (entity == null) { // Bad request
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        }

        // 2.2. retrieve the property data from the entity
        Property property = entity.getProperty(edmPropertyName);
        if (property == null) {
            throw new ODataApplicationException("Property not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        }

        // 3. serialize
        Object value = property.getValue();
        if (value != null) {
            // 3.1. configure the serializer
            ODataSerializer serializer = odata.createSerializer(responseFormat);

            ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).navOrPropertyPath(edmPropertyName).build();
            PrimitiveSerializerOptions options = PrimitiveSerializerOptions.with().contextURL(contextUrl).build();
            // 3.2. serialize
            SerializerResult serializerResult = serializer.primitive(serviceMetadata, edmPropertyType, property, options);
            InputStream propertyStream = serializerResult.getContent();

            // 4. configure the response object
            response.setContent(propertyStream);
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } else {
            // in case there's no value for the property, we can skip the serialization
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
        }
    }


    public void updatePrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, DeserializerException, SerializerException {
        System.out.println("updatePrimitive() @DemoPrimitiveProcessor");
        throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
    }

    public void deletePrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException {
        System.out.println("deletePrimitive() @DemoPrimitiveProcessor");
        throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
    }
}
