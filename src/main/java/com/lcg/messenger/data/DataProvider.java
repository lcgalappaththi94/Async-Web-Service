package com.lcg.messenger.data;

import com.lcg.messenger.service.DemoEdmProvider;
import com.lcg.messenger.util.Util;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmKeyPropertyRef;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResourceFunction;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DataProvider {

    // represent our database
    private List<Entity> reviewList;
    private List<Entity> profileList;

    public DataProvider() {
        System.out.println("@DataProvider");
        reviewList = new ArrayList<>();
        profileList = new ArrayList<>();

        // creating some sample data
        initReviewSampleData();
        initProfileSampleData();
    }

  /* PUBLIC FACADE */

    public EntityCollection readEntitySetData(EdmEntitySet edmEntitySet) {
        System.out.println("readEntitySetData() @DataProvider");
        EntityCollection entitySet = null;

        if (edmEntitySet.getName().equals(DemoEdmProvider.ES_REVIEWS_NAME)) {
            entitySet = getReviews();
        } else if (edmEntitySet.getName().equals(DemoEdmProvider.ES_PROFILES_NAME)) {
            entitySet = getProfiles();
        }

        return entitySet;
    }

    public Entity readEntityData(EdmEntitySet edmEntitySet, List<UriParameter> keyParams) {
        System.out.println("readEntityData() @DataProvider");
        Entity entity = null;

        EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        if (edmEntityType.getName().equals(DemoEdmProvider.ET_REVIEW_NAME)) {
            entity = getReview(edmEntityType, keyParams);
        } else if (edmEntityType.getName().equals(DemoEdmProvider.ET_PROFILE_NAME)) {
            entity = getProfile(edmEntityType, keyParams);
        }

        return entity;
    }

    public Entity createEntityData(EdmEntitySet edmEntitySet, Entity entityToCreate) {
        System.out.println("createEntityData() @DataProvider");
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        // actually, this is only required if we have more than one Entity Type
        if (edmEntityType.getName().equals(DemoEdmProvider.ET_REVIEW_NAME)) {
            return createReview(edmEntityType, entityToCreate);
        }
        if (edmEntityType.getName().equals(DemoEdmProvider.ET_PROFILE_NAME)) {
            return createProfile(edmEntityType, entityToCreate);
        }

        return null;
    }

    /**
     * This method is invoked for PATCH or PUT requests
     */
    public void updateEntityData(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, Entity updateEntity, HttpMethod httpMethod) throws ODataApplicationException {
        System.out.println("updateEntityData() @DataProvider");
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        // actually, this is only required if we have more than one Entity Type
        if (edmEntityType.getName().equals(DemoEdmProvider.ET_REVIEW_NAME)) {
            updateReview(edmEntityType, keyParams, updateEntity, httpMethod);
        }
        if (edmEntityType.getName().equals(DemoEdmProvider.ET_PROFILE_NAME)) {
            updateProfile(edmEntityType, keyParams, updateEntity, httpMethod);
        }
    }

    public void deleteEntityData(EdmEntitySet edmEntitySet, List<UriParameter> keyParams) throws ODataApplicationException {
        System.out.println("deleteEntityData() @DataProvider");
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        // actually, this is only required if we have more than one Entity Type
        if (edmEntityType.getName().equals(DemoEdmProvider.ET_REVIEW_NAME)) {
            deleteReview(edmEntityType, keyParams);
        }
        if (edmEntityType.getName().equals(DemoEdmProvider.ET_PROFILE_NAME)) {
            deleteProfile(edmEntityType, keyParams);
        }
    }


    private Entity createReview(EdmEntityType edmEntityType, Entity entity) {
        System.out.println("createReview() @DataProvider");
        // the ID of the newly created review entity is generated automatically
        int newId = 1;
        while (reviewIdExists(newId)) {
            newId++;
        }

        Property idProperty = entity.getProperty("ID");
        if (idProperty != null) {
            idProperty.setValue(ValueType.PRIMITIVE, Integer.valueOf(newId));
        } else {
            // as of OData v4 spec, the key property can be omitted from the POST request body
            entity.getProperties().add(new Property(null, "ID", ValueType.PRIMITIVE, newId));
        }
        entity.setId(createId("Reviews", newId));
        this.reviewList.add(entity);

        return entity;

    }

    private boolean reviewIdExists(int id) {
        System.out.println("reviewIdExists() @DataProvider");
        for (Entity entity : this.reviewList) {
            Integer existingID = (Integer) entity.getProperty("ID").getValue();
            if (existingID.intValue() == id) {
                return true;
            }
        }
        return false;
    }

    private void updateReview(EdmEntityType edmEntityType, List<UriParameter> keyParams, Entity entity, HttpMethod httpMethod) throws ODataApplicationException {
        System.out.println("updateReview() @DataProvider");
        Entity reviewEntity = getReview(edmEntityType, keyParams);
        if (reviewEntity == null) {
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        }

        // loop over all properties and replace the values with the values of the given payload
        // Note: ignoring ComplexType, as we don't have it in our odata model
        List<Property> existingProperties = reviewEntity.getProperties();
        for (Property existingProp : existingProperties) {
            String propName = existingProp.getName();

            // ignore the key properties, they aren't updateable
            if (isKey(edmEntityType, propName)) {
                continue;
            }

            Property updateProperty = entity.getProperty(propName);
            // the request payload might not consider ALL properties, so it can be null
            if (updateProperty == null) {
                // if a property has NOT been added to the request payload
                // depending on the HttpMethod, our behavior is different
                if (httpMethod.equals(HttpMethod.PATCH)) {
                    // as of the OData spec, in case of PATCH, the existing property is not touched
                    continue; // do nothing
                } else if (httpMethod.equals(HttpMethod.PUT)) {
                    // as of the OData spec, in case of PUT, the existing property is set to null (or to default value)
                    existingProp.setValue(existingProp.getValueType(), null);
                    continue;
                }
            }

            // change the value of the properties
            existingProp.setValue(existingProp.getValueType(), updateProperty.getValue());
        }
    }

    private void deleteReview(EdmEntityType edmEntityType, List<UriParameter> keyParams) throws ODataApplicationException {
        System.out.println("deleteReview() @DataProvider");
        Entity reviewEntity = getReview(edmEntityType, keyParams);
        if (reviewEntity == null) {
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        }
        this.reviewList.remove(reviewEntity);
    }

  /* HELPER */

    private boolean isKey(EdmEntityType edmEntityType, String propertyName) {
        System.out.println("isKey() @DataProvider");
        List<EdmKeyPropertyRef> keyPropertyRefs = edmEntityType.getKeyPropertyRefs();
        for (EdmKeyPropertyRef propRef : keyPropertyRefs) {
            String keyPropertyName = propRef.getName();
            if (keyPropertyName.equals(propertyName)) {
                return true;
            }
        }
        return false;
    }


    private Entity createProfile(EdmEntityType edmEntityType, Entity entity) {
        System.out.println("createProfile() @DataProvider");
        // the ID of the newly created profile entity is generated automatically
        int newId = 1;
        while (profileIdExists(newId)) {
            newId++;
        }

        Property idProperty = entity.getProperty("ID");
        if (idProperty != null) {
            idProperty.setValue(ValueType.PRIMITIVE, Integer.valueOf(newId));
        } else {
            // as of OData v4 spec, the key property can be omitted from the POST request body
            entity.getProperties().add(new Property(null, "ID", ValueType.PRIMITIVE, newId));
        }
        entity.setId(createId("Profiles", newId));
        this.profileList.add(entity);

        return entity;

    }

    private boolean profileIdExists(int id) {
        System.out.println("profileIdExists() @DataProvider");
        for (Entity entity : this.profileList) {
            Integer existingID = (Integer) entity.getProperty("ID").getValue();
            if (existingID.intValue() == id) {
                return true;
            }
        }
        return false;
    }

    private void updateProfile(EdmEntityType edmEntityType, List<UriParameter> keyParams, Entity entity, HttpMethod httpMethod) throws ODataApplicationException {
        System.out.println("updateProfile() @DataProvider");
        Entity profileEntity = getProfile(edmEntityType, keyParams);
        if (profileEntity == null) {
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        }

        // loop over all properties and replace the values with the values of the given payload
        // Note: ignoring ComplexType, as we don't have it in our odata model
        List<Property> existingProperties = profileEntity.getProperties();
        for (Property existingProp : existingProperties) {
            String propName = existingProp.getName();

            // ignore the key properties, they aren't updateable
            if (isKey(edmEntityType, propName)) {
                continue;
            }

            Property updateProperty = entity.getProperty(propName);
            // the request payload might not consider ALL properties, so it can be null
            if (updateProperty == null) {
                // if a property has NOT been added to the request payload
                // depending on the HttpMethod, our behavior is different
                if (httpMethod.equals(HttpMethod.PATCH)) {
                    // as of the OData spec, in case of PATCH, the existing property is not touched
                    continue; // do nothing
                } else if (httpMethod.equals(HttpMethod.PUT)) {
                    // as of the OData spec, in case of PUT, the existing property is set to null (or to default value)
                    existingProp.setValue(existingProp.getValueType(), null);
                    continue;
                }
            }

            // change the value of the properties
            existingProp.setValue(existingProp.getValueType(), updateProperty.getValue());
        }
    }

    private void deleteProfile(EdmEntityType edmEntityType, List<UriParameter> keyParams) throws ODataApplicationException {
        System.out.println("deleteProfile() @DataProvider");
        Entity profileEntity = getProfile(edmEntityType, keyParams);
        if (profileEntity == null) {
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        }
        this.profileList.remove(profileEntity);
    }


    // Navigation

    public Entity getRelatedEntity(Entity entity, EdmEntityType relatedEntityType) {
        System.out.println("getRelatedEntity(Entity entity, EdmEntityType relatedEntityType) @DataProvider");
        EntityCollection collection = getRelatedEntityCollection(entity, relatedEntityType);
        if (collection.getEntities().isEmpty()) {
            return null;
        }
        return collection.getEntities().get(0);
    }

    public Entity getRelatedEntity(Entity entity, EdmEntityType relatedEntityType, List<UriParameter> keyPredicates) {
        System.out.println("getRelatedEntity(Entity entity, EdmEntityType relatedEntityType, List<UriParameter> keyPredicates) @DataProvider");
        EntityCollection relatedEntities = getRelatedEntityCollection(entity, relatedEntityType);
        return Util.findEntity(relatedEntityType, relatedEntities, keyPredicates);
    }

    public EntityCollection getRelatedEntityCollection(Entity sourceEntity, EdmEntityType targetEntityType) {
        System.out.println("getRelatedEntityCollection() @DataProvider");
        EntityCollection navigationTargetEntityCollection = new EntityCollection();
        FullQualifiedName relatedEntityFqn = targetEntityType.getFullQualifiedName();
        String sourceEntityFqn = sourceEntity.getType();

        System.out.println("sourceEntity: " + sourceEntity.getType() + "  sourceEntityProperty: " + sourceEntity.getProperty("ID") + "  sourceEntity: " + targetEntityType.getName());

        if (sourceEntityFqn.equals(DemoEdmProvider.ET_REVIEW_FQN.getFullQualifiedNameAsString()) && relatedEntityFqn.equals(DemoEdmProvider.ET_PROFILE_FQN)) {
            // relation Reviews->Profile (result all categories)
            int ReviewID = (Integer) sourceEntity.getProperty("ID").getValue();
            if (ReviewID == 1) {
                navigationTargetEntityCollection.getEntities().add(profileList.get(0));
            } else if (ReviewID == 2 || ReviewID == 3 || ReviewID == 4) {
                navigationTargetEntityCollection.getEntities().add(profileList.get(1));
            } else if (ReviewID == 5 || ReviewID == 6) {
                navigationTargetEntityCollection.getEntities().add(profileList.get(2));
            }
        } else if (sourceEntityFqn.equals(DemoEdmProvider.ET_PROFILE_FQN.getFullQualifiedNameAsString())
                && relatedEntityFqn.equals(DemoEdmProvider.ET_REVIEW_FQN)) {
            // relation Profile->Reviews (result all Reviews)
            int profileID = (Integer) sourceEntity.getProperty("ID").getValue();
            if (profileID == 1) {
                // the first 2 Reviews are notebooks
                navigationTargetEntityCollection.getEntities().addAll(reviewList.subList(0, 1));
            } else if (profileID == 2) {
                // the next 2 Reviews are organizers
                navigationTargetEntityCollection.getEntities().addAll(reviewList.subList(1, 4));
            } else if (profileID == 3) {
                // the first 2 Reviews are monitors
                navigationTargetEntityCollection.getEntities().addAll(reviewList.subList(4, 6));
            }
        }

        if (navigationTargetEntityCollection.getEntities().isEmpty()) {
            return null;
        }

        return navigationTargetEntityCollection;
    }

  /* INTERNAL */

    private EntityCollection getReviews() {
        System.out.println("getReviews() @DataProvider");
        EntityCollection retEntitySet = new EntityCollection();

        for (Entity ReviewEntity : this.reviewList) {
            retEntitySet.getEntities().add(ReviewEntity);
        }
        return retEntitySet;
    }

    private Entity getReview(EdmEntityType edmEntityType, List<UriParameter> keyParams) {
        System.out.println("getReview() @DataProvider");
        // the list of entities at runtime
        EntityCollection entityCollection = getReviews();

        /* generic approach to find the requested entity */
        return Util.findEntity(edmEntityType, entityCollection, keyParams);
    }

    private EntityCollection getProfiles() {
        System.out.println("getProfiles() @DataProvider");
        EntityCollection entitySet = new EntityCollection();

        for (Entity profileEntity : this.profileList) {
            entitySet.getEntities().add(profileEntity);
        }
        return entitySet;
    }

    private Entity getProfile(EdmEntityType edmEntityType, List<UriParameter> keyParams) {
        System.out.println("getProfile() @DataProvider");
        // the list of entities at runtime
        EntityCollection entitySet = getProfiles();

    /* generic approach to find the requested entity */
        return Util.findEntity(edmEntityType, entitySet, keyParams);
    }

  /* HELPER */

    private void initReviewSampleData() {
        System.out.println("initReviewSampleData() @DataProvider");
        Entity entity = new Entity();
        entity.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, 1));
        entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Nimasha Hashani"));
        entity.addProperty(new Property(null, "Message", ValueType.PRIMITIVE, "A good profile"));
        entity.addProperty(new Property(null, "Rating", ValueType.PRIMITIVE, 5));
        entity.setType(DemoEdmProvider.ET_REVIEW_FQN.getFullQualifiedNameAsString());
        entity.setId(createId(entity, "ID"));
        reviewList.add(entity);

        entity = new Entity();
        entity.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, 2));
        entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Hasanga Eshani"));
        entity.addProperty(new Property(null, "Message", ValueType.PRIMITIVE, "Very funny"));
        entity.addProperty(new Property(null, "Rating", ValueType.PRIMITIVE, 4));
        entity.setType(DemoEdmProvider.ET_REVIEW_FQN.getFullQualifiedNameAsString());
        entity.setId(createId(entity, "ID"));
        reviewList.add(entity);

        entity = new Entity();
        entity.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, 3));
        entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Hansi Annie"));
        entity.addProperty(new Property(null, "Message", ValueType.PRIMITIVE, "Good work Keep it up"));
        entity.addProperty(new Property(null, "Rating", ValueType.PRIMITIVE, 3));
        entity.setType(DemoEdmProvider.ET_REVIEW_FQN.getFullQualifiedNameAsString());
        entity.setId(createId(entity, "ID"));
        reviewList.add(entity);

        entity = new Entity();
        entity.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, 4));
        entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Robert Apachee"));
        entity.addProperty(new Property(null, "Message", ValueType.PRIMITIVE, "Very nice and quality"));
        entity.addProperty(new Property(null, "Rating", ValueType.PRIMITIVE, 5));
        entity.setType(DemoEdmProvider.ET_REVIEW_FQN.getFullQualifiedNameAsString());
        entity.setId(createId(entity, "ID"));
        reviewList.add(entity);

        entity = new Entity();
        entity.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, 5));
        entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Kasun Chamara"));
        entity.addProperty(new Property(null, "Message", ValueType.PRIMITIVE, "Keep it going"));
        entity.addProperty(new Property(null, "Rating", ValueType.PRIMITIVE, 4));
        entity.setType(DemoEdmProvider.ET_REVIEW_FQN.getFullQualifiedNameAsString());
        entity.setId(createId(entity, "ID"));
        reviewList.add(entity);

        entity = new Entity();
        entity.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, 6));
        entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Arshan Fauzil"));
        entity.addProperty(new Property(null, "Message", ValueType.PRIMITIVE, "Oh this looks good"));
        entity.addProperty(new Property(null, "Rating", ValueType.PRIMITIVE, 3));
        entity.setType(DemoEdmProvider.ET_REVIEW_FQN.getFullQualifiedNameAsString());
        entity.setId(createId(entity, "ID"));
        reviewList.add(entity);
    }

    private void initProfileSampleData() {
        System.out.println("initProfileSampleData() @DataProvider");
        Entity entity = new Entity();
        entity.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, 1));
        entity.addProperty(new Property(null, "Age", ValueType.PRIMITIVE, 25));
        entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Chathura Galappaththi"));
        entity.addProperty(new Property(null, "Info", ValueType.PRIMITIVE, "An undergraduate of UOM"));
        entity.addProperty(new Property(null, "Country", ValueType.PRIMITIVE, "Sri Lanka"));
        entity.addProperty(new Property(null, "Title", ValueType.PRIMITIVE, "Software Engineer"));
        entity.setType(DemoEdmProvider.ET_PROFILE_FQN.getFullQualifiedNameAsString());
        entity.setId(createId(entity, "ID"));
        profileList.add(entity);

        entity = new Entity();
        entity.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, 2));
        entity.addProperty(new Property(null, "Age", ValueType.PRIMITIVE, 20));
        entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Anne Fernando"));
        entity.addProperty(new Property(null, "Info", ValueType.PRIMITIVE, "A hard working individual"));
        entity.addProperty(new Property(null, "Country", ValueType.PRIMITIVE, "USA"));
        entity.addProperty(new Property(null, "Title", ValueType.PRIMITIVE, "Associate Software Engineeer"));
        entity.setType(DemoEdmProvider.ET_PROFILE_FQN.getFullQualifiedNameAsString());
        entity.setId(createId(entity, "ID"));
        profileList.add(entity);

        entity = new Entity();
        entity.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, 3));
        entity.addProperty(new Property(null, "Age", ValueType.PRIMITIVE, 19));
        entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Jorge Washington"));
        entity.addProperty(new Property(null, "Info", ValueType.PRIMITIVE, "Former president of USA"));
        entity.addProperty(new Property(null, "Country", ValueType.PRIMITIVE, "Canada"));
        entity.addProperty(new Property(null, "Title", ValueType.PRIMITIVE, "President"));
        entity.setType(DemoEdmProvider.ET_PROFILE_FQN.getFullQualifiedNameAsString());
        entity.setId(createId(entity, "ID"));
        profileList.add(entity);
    }

    private URI createId(Entity entity, String idPropertyName) {
        System.out.println("createId(Entity entity, String idPropertyName) @DataProvider");
        return createId(entity, idPropertyName, null);
    }

    private URI createId(Entity entity, String idPropertyName, String navigationName) {
        System.out.println("createId(Entity entity, String idPropertyName, String navigationName) @DataProvider");
        try {
            StringBuilder sb = new StringBuilder(getEntitySetName(entity)).append("(");
            final Property property = entity.getProperty(idPropertyName);
            sb.append(property.asPrimitive()).append(")");
            if (navigationName != null) {
                sb.append("/").append(navigationName);
            }
            return new URI(sb.toString());
        } catch (URISyntaxException e) {
            throw new ODataRuntimeException("Unable to create (Atom) id for entity: " + entity, e);
        }
    }

    private URI createId(String entitySetName, Object id) {
        System.out.println("createId(String entitySetName, Object id) @DataProvider");
        try {
            return new URI(entitySetName + "(" + String.valueOf(id) + ")");
        } catch (URISyntaxException e) {
            throw new ODataRuntimeException("Unable to create id for entity: " + entitySetName, e);
        }
    }

    private String getEntitySetName(Entity entity) {
        System.out.println("getEntitySetName() @DataProvider");
        if (DemoEdmProvider.ET_PROFILE_FQN.getFullQualifiedNameAsString().equals(entity.getType())) {
            return DemoEdmProvider.ES_PROFILES_NAME;
        } else if (DemoEdmProvider.ET_REVIEW_FQN.getFullQualifiedNameAsString().equals(entity.getType())) {
            return DemoEdmProvider.ES_REVIEWS_NAME;
        }
        return entity.getType();
    }

    // For functions and actions
    //2 methods for readFunctionImportEntity

    public Entity readFunctionImportEntity(final UriResourceFunction uriResourceFunction, final ServiceMetadata serviceMetadata) throws ODataApplicationException {
        System.out.println("readFunctionImportEntity() @DataProvider");
        final EntityCollection entityCollection = readFunctionImportCollection(uriResourceFunction, serviceMetadata);
        final EdmEntityType edmEntityType = (EdmEntityType) uriResourceFunction.getFunction().getReturnType().getType();

        return Util.findEntity(edmEntityType, entityCollection, uriResourceFunction.getKeyPredicates());
    }

    public EntityCollection readFunctionImportCollection(final UriResourceFunction uriResourceFunction, final ServiceMetadata serviceMetadata) throws ODataApplicationException {
        System.out.println("readFunctionImportCollection() @DataProvider");
        if (DemoEdmProvider.FUNCTION_COUNT_PROFILES.equals(uriResourceFunction.getFunctionImport().getName())) {
            // Get the parameter of the function
            final UriParameter parameterAmount = uriResourceFunction.getParameters().get(0);
            // Try to convert the parameter to an Integer.
            // We have to take care, that the type of parameter fits to its EDM declaration
            int amount;
            try {
                amount = Integer.parseInt(parameterAmount.getText());
            } catch (NumberFormatException e) {
                throw new ODataApplicationException("Type of parameter Amount must be Edm.Int32", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
            }

            final EdmEntityType reviewEntityType = serviceMetadata.getEdm().getEntityType(DemoEdmProvider.ET_REVIEW_FQN);
            final List<Entity> resultEntityList = new ArrayList<Entity>();

            // Loop over all categories and check how many reviews are linked
            for (final Entity profile : profileList) {
                final EntityCollection reviews = getRelatedEntityCollection(profile, reviewEntityType);
                if (reviews.getEntities().size() == amount) {
                    resultEntityList.add(profile);
                }
            }

            final EntityCollection resultCollection = new EntityCollection();
            resultCollection.getEntities().addAll(resultEntityList);
            return resultCollection;
        } else {
            throw new ODataApplicationException("Function not implemented", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
        }
    }

    //two methods for resetDataSet
    public void resetDataSet() {
        System.out.println("resetDataSet() @DataProvider");
        resetDataSet(Integer.MAX_VALUE);
    }

    public void resetDataSet(final int amount) {
        System.out.println("resetDataSet(" + amount + ") @DataProvider");
        // Replace the old lists with empty ones
        reviewList = new ArrayList<>();
        profileList = new ArrayList<>();

        // Create new sample data
        initProfileSampleData();
        initReviewSampleData();

        // Truncate the lists
        if (amount < reviewList.size()) {
            reviewList = reviewList.subList(0, amount);
            profileList = profileList.subList(0, (amount / 2) + 1);
        }

    }

    public static class DataProviderException extends ODataApplicationException {
        private static final long serialVersionUID = 5098059649321796156L;

        public DataProviderException(final String message, final HttpStatusCode statusCode) {
            super(message, statusCode.getStatusCode(), Locale.ROOT);
            System.out.println("@DataProviderException(final String message, final HttpStatusCode statusCode)");
        }

        public DataProviderException(final String message, final HttpStatusCode statusCode, final Throwable throwable) {
            super(message, statusCode.getStatusCode(), Locale.ROOT, throwable);
            System.out.println("@DataProviderException(final String message, final HttpStatusCode statusCode, final Throwable throwable)");
        }
    }
}
