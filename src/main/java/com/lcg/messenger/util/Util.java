package com.lcg.messenger.util;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriInfoResource;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;

import java.util.List;
import java.util.Locale;

public class Util {

    public static Entity findEntity(EdmEntityType edmEntityType, EntityCollection entitySet, List<UriParameter> keyParams) {
        System.out.println("findEntity() @Util");
        List<Entity> entityList = entitySet.getEntities();

        // loop over all entities in order to find that one that matches
        // all keys in request e.g. contacts(ContactID=1, CompanyID=1)
        for (Entity entity : entityList) {
            boolean foundEntity = entityMatchesAllKeys(edmEntityType, entity, keyParams);
            if (foundEntity) {
                System.out.println("foundEntity that matches keys");
                return entity;
            }
        }
        return null;
    }

    public static boolean entityMatchesAllKeys(EdmEntityType edmEntityType, Entity rt_entity, List<UriParameter> keyParams) {
        System.out.println("entityMatchesAllKeys() @Util");
        // loop over all keys
        for (final UriParameter key : keyParams) {
            String keyName = key.getName();
            String keyText = key.getText();
            System.out.println("keyName: " + keyName + "  KeyText: " + keyText);

            // note: below line doesn't consider: keyProp can be part of a complexType in V4
            // in such case, it would be required to access it via getKeyPropertyRef()
            // but since this isn't the case in our model, we ignore it in our implementation
            EdmProperty edmKeyProperty = (EdmProperty) edmEntityType.getProperty(keyName);
            // Edm: we need this info for the comparison below
            Boolean isNullable = edmKeyProperty.isNullable();
            Integer maxLength = edmKeyProperty.getMaxLength();
            Integer precision = edmKeyProperty.getPrecision();
            Boolean isUnicode = edmKeyProperty.isUnicode();
            Integer scale = edmKeyProperty.getScale();
            // get the EdmType in order to compare
            EdmType edmType = edmKeyProperty.getType();
            // if(EdmType instanceof EdmPrimitiveType) // do we need this?
            EdmPrimitiveType edmPrimitiveType = (EdmPrimitiveType) edmType;

            // Runtime data: the value of the current entity
            // don't need to check for null, this is done in FWK
            Object valueObject = rt_entity.getProperty(keyName).getValue();
            // TODO if the property is a complex type

            // now need to compare the valueObject with the keyText String
            // this is done using the type.valueToString
            String valueAsString = null;
            try {
                valueAsString = edmPrimitiveType.valueToString(valueObject, isNullable, maxLength, precision, scale, isUnicode);
            } catch (EdmPrimitiveTypeException e) {
                return false; // TODO proper Exception handling
            }

            if (valueAsString == null) {
                return false;
            }

            boolean matches = valueAsString.equals(keyText);
            if (matches) {
                // if the given key value is found in the current entity, continue with the next key
                continue;
            } else {
                // if any of the key properties is not found in the entity, we don't need to search further
                return false;
            }
        }

        return true;
    }


    public static EdmEntitySet getNavigationTargetEntitySet(EdmEntitySet startEdmEntitySet, EdmNavigationProperty edmNavigationProperty) throws ODataApplicationException {
        EdmEntitySet navigationTargetEntitySet = null;
        System.out.println("getNavigationTargetEntitySet() @Util");
        String navPropName = edmNavigationProperty.getName();
        EdmBindingTarget edmBindingTarget = startEdmEntitySet.getRelatedBindingTarget(navPropName);
        if (edmBindingTarget == null) {
            throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
        }

        if (edmBindingTarget instanceof EdmEntitySet) {
            navigationTargetEntitySet = (EdmEntitySet) edmBindingTarget;
        } else {
            throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
        }
        System.out.println("getNavigationTargetEntitySet() @Util navigationTargetEntitySet: " + navigationTargetEntitySet.getName());
        return navigationTargetEntitySet;
    }

    public static EdmEntitySet getEdmEntitySet(UriInfoResource uriInfo) throws ODataApplicationException {
        System.out.println("getEdmEntitySet() @Util");
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        // To get the entity set we have to interpret all URI segments
        if (!(resourcePaths.get(0) instanceof UriResourceEntitySet)) {
            // Here we should interpret the whole URI but in this example we do not support navigation so we throw an
            // exception
            throw new ODataApplicationException("Invalid resource type for first segment.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }
        UriResourceEntitySet uriResource = (UriResourceEntitySet) resourcePaths.get(0);
        return uriResource.getEntitySet();
    }

}
