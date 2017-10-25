package com.lcg.messenger.data;

import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.http.HttpStatusCode;

import java.util.ArrayList;
import java.util.List;

public class RequestValidator {
    private final DataProvider provider;
    private final boolean isInsert;
    private final boolean isPatch;
    private final String rawServiceRoot;

    public RequestValidator(final DataProvider provider, final String rawServiceRoot) {

        this(provider, false, false, rawServiceRoot);
    }

    public RequestValidator(final DataProvider provider, final boolean isUpdate, final boolean isPatch, final String rawServiceRoot) {
        System.out.println("@RequestValidator");
        this.provider = provider;
        this.isInsert = !isUpdate;
        this.isPatch = isPatch;
        this.rawServiceRoot = rawServiceRoot;
    }

    public void validate(final EdmBindingTarget edmBindingTarget, final Entity entity) throws DataProvider.DataProviderException {
        System.out.println("validate() @RequestValidator");
        final List<String> path = new ArrayList<>();

        validateEntitySetProperties(entity.getProperties(), edmBindingTarget, edmBindingTarget.getEntityType(), path);
        validateNavigationProperties(entity, edmBindingTarget, edmBindingTarget.getEntityType(), path);
    }

    private void validateNavigationProperties(final Linked entity, final EdmBindingTarget edmBindingTarget, final EdmStructuredType edmType, final List<String> path) throws DataProvider.DataProviderException {
        System.out.println("validateNavigationProperties() @RequestValidator");
        for (final String navPropertyName : edmType.getNavigationPropertyNames()) {
            final EdmNavigationProperty edmProperty = edmType.getNavigationProperty(navPropertyName);
            if (entity == null && !edmProperty.isNullable()) {
                throw new DataProvider.DataProviderException("Navigation property " + navPropertyName + " must not be null", HttpStatusCode.BAD_REQUEST);
            } else if (entity != null) {
                final Link navigationBinding = entity.getNavigationBinding(navPropertyName);
                final Link navigationLink = entity.getNavigationLink(navPropertyName);
                final List<String> newPath = new ArrayList<>(path);
                newPath.add(edmProperty.getName());
                final EdmBindingTarget target = edmBindingTarget.getRelatedBindingTarget(buildPath(newPath));

                final ValidationResult bindingResult = validateBinding(navigationBinding, edmProperty);
                final ValidationResult linkResult = validateNavigationLink(navigationLink, edmProperty, target);

                if ((isInsert && !edmProperty.isNullable() && (bindingResult != ValidationResult.FOUND && linkResult != ValidationResult.FOUND))
                        || (!(isInsert && isPatch) && !edmProperty.isNullable() && linkResult == ValidationResult.EMPTY)) {
                    throw new DataProvider.DataProviderException("Navigation property " + navPropertyName + " must not be null", HttpStatusCode.BAD_REQUEST);
                }
            }
        }
    }

    private String buildPath(final List<String> path) {
        System.out.println("buildPath() @RequestValidator");
        final StringBuilder builder = new StringBuilder();

        for (final String segment : path) {
            if (builder.length() > 0) {
                builder.append("/");
            }

            builder.append(segment);
        }

        return builder.toString();
    }

    private ValidationResult validateBinding(final Link navigationBinding, final EdmNavigationProperty edmProperty) throws DataProvider.DataProviderException {
        System.out.println("validateBinding() @RequestValidator");
        if (navigationBinding == null) {
            return ValidationResult.NOT_FOUND;
        }

        if (edmProperty.isCollection()) {
            if (navigationBinding.getBindingLinks().size() == 0) {
                return ValidationResult.EMPTY;
            }

            for (final String bindingLink : navigationBinding.getBindingLinks()) {
                // validateLink(bindingLink);
            }
        } else {
            if (navigationBinding.getBindingLink() == null) {
                return ValidationResult.EMPTY;
            }
            //validateLink(navigationBinding.getBindingLink());
        }
        return ValidationResult.FOUND;
    }

    private ValidationResult validateNavigationLink(final Link navigationLink, final EdmNavigationProperty edmProperty, final EdmBindingTarget edmBindingTarget) throws DataProvider.DataProviderException {
        System.out.println("validateNavigationLink() @RequestValidator");
        if (navigationLink == null) {
            return ValidationResult.NOT_FOUND;
        }

        if (edmProperty.isCollection()) {
            final EntityCollection inlineEntitySet = navigationLink.getInlineEntitySet();
            if (inlineEntitySet != null) {
                if (!isInsert && inlineEntitySet.getEntities().size() > 0) {
                    throw new DataProvider.DataProviderException("Deep update is not allowed", HttpStatusCode.BAD_REQUEST);
                } else {
                    for (final Entity entity : navigationLink.getInlineEntitySet().getEntities()) {
                        validate(edmBindingTarget, entity);
                    }
                }
            }
        } else {
            final Entity inlineEntity = navigationLink.getInlineEntity();
            if (!isInsert && inlineEntity != null) {
                throw new DataProvider.DataProviderException("Deep update is not allowed", HttpStatusCode.BAD_REQUEST);
            } else if (inlineEntity != null) {
                validate(edmBindingTarget, navigationLink.getInlineEntity());
            }
        }

        return ValidationResult.FOUND;
    }


    private void validateEntitySetProperties(final List<Property> properties, final EdmBindingTarget edmBindingTarget, final EdmEntityType edmType, final List<String> path) throws DataProvider.DataProviderException {
        System.out.println("validateEntitySetProperties() @RequestValidator");
        validateProperties(properties, edmBindingTarget, edmType, edmType.getKeyPredicateNames(), path);
    }

    private void validateProperties(final List<Property> properties, final EdmBindingTarget edmBindingTarget, final EdmStructuredType edmType, final List<String> keyPredicateNames, final List<String> path) throws DataProvider.DataProviderException {
        System.out.println("validateProperties() @RequestValidator");
        for (final String propertyName : edmType.getPropertyNames()) {
            final EdmProperty edmProperty = (EdmProperty) edmType.getProperty(propertyName);

            // Ignore key properties, they are set automatically
            if (!keyPredicateNames.contains(propertyName)) {
                final Property property = getProperty(properties, propertyName);

                // Check if all "not nullable" properties are set
                if (!edmProperty.isNullable()) {
                    if ((property != null && property.isNull()) // Update,insert; Property is explicit set to null
                            || (isInsert && property == null) // Insert; Property not provided
                            || (!isInsert && !isPatch && property == null)) { // Insert(Put); Property not provided
                        throw new DataProvider.DataProviderException("Property " + propertyName + " must not be null", HttpStatusCode.BAD_REQUEST);
                    }
                }
                // Validate property value
                validatePropertyValue(property, edmProperty, edmBindingTarget, path);
            }
        }
    }

    private void validatePropertyValue(final Property property, final EdmProperty edmProperty, final EdmBindingTarget edmBindingTarget, final List<String> path) throws DataProvider.DataProviderException {
        System.out.println("validatePropertyValue() @RequestValidator");
        final ArrayList<String> newPath = new ArrayList<>(path);
        newPath.add(edmProperty.getName());

        if (edmProperty.isCollection()) {
            if (edmProperty.getType() instanceof EdmComplexType && property != null) {
                for (final Object value : property.asCollection()) {
                    validateComplexValue((ComplexValue) value, edmBindingTarget, (EdmComplexType) edmProperty.getType(), newPath);
                }
            }
        } else if (edmProperty.getType() instanceof EdmComplexType) {
            validateComplexValue((property == null) ? null : property.asComplex(), edmBindingTarget, (EdmComplexType) edmProperty.getType(), newPath);
        }
    }

    private void validateComplexValue(final ComplexValue value, final EdmBindingTarget edmBindingTarget, final EdmComplexType edmType, final List<String> path) throws DataProvider.DataProviderException {
        System.out.println("validateComplexValue() @RequestValidator");
        // The whole complex property can be nullable but nested primitive, navigation properties can be not nullable
        final List<Property> properties = (value == null) ? new ArrayList<>() : value.getValue();

        validateProperties(properties, edmBindingTarget, edmType, new ArrayList<>(0), path);
        validateNavigationProperties(value, edmBindingTarget, edmType, path);
    }

    private Property getProperty(final List<Property> properties, final String name) {
        System.out.println("getProperty() @RequestValidator");
        for (final Property property : properties) {
            if (property.getName().equals(name)) {
                return property;
            }
        }

        return null;
    }

    private enum ValidationResult {
        FOUND,
        NOT_FOUND,
        EMPTY
    }
}
