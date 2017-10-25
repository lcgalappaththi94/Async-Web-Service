package com.lcg.messenger.service;

import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.*;
import org.apache.olingo.commons.api.ex.ODataException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DemoEdmProvider extends CsdlAbstractEdmProvider {

    // Service Namespace
    public static final String NAMESPACE = "OData.Demo";

    // EDM Container
    public static final String CONTAINER_NAME = "Container";
    public static final FullQualifiedName CONTAINER = new FullQualifiedName(NAMESPACE, CONTAINER_NAME);

    // Entity Types Names
    public static final String ET_REVIEW_NAME = "Review";
    public static final FullQualifiedName ET_REVIEW_FQN = new FullQualifiedName(NAMESPACE, ET_REVIEW_NAME);

    public static final String ET_PROFILE_NAME = "Profile";
    public static final FullQualifiedName ET_PROFILE_FQN = new FullQualifiedName(NAMESPACE, ET_PROFILE_NAME);

    // Entity Set Names
    public static final String ES_REVIEWS_NAME = "Reviews";
    public static final String ES_PROFILES_NAME = "Profiles";


    // Action
    public static final String ACTION_RESET = "Reset";
    public static final FullQualifiedName ACTION_RESET_FQN = new FullQualifiedName(NAMESPACE, ACTION_RESET);

    // Function
    public static final String FUNCTION_COUNT_PROFILES = "CountProfiles";
    public static final FullQualifiedName FUNCTION_COUNT_PROFILES_FQN = new FullQualifiedName(NAMESPACE, FUNCTION_COUNT_PROFILES);

    // Function/Action Parameters
    public static final String PARAMETER_VALUE = "Value";


    @Override
    public CsdlEntityType getEntityType(FullQualifiedName entityTypeName) {

        // this method is called for each EntityType that are configured in the Schema
        CsdlEntityType entityType = null;

        if (entityTypeName.equals(ET_REVIEW_FQN)) {
            // create EntityType properties
            CsdlProperty id = new CsdlProperty().setName("ID").setType(EdmPrimitiveTypeKind.Int32.getFullQualifiedName());
            CsdlProperty name = new CsdlProperty().setName("Name").setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());
            CsdlProperty message = new CsdlProperty().setName("Message").setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());
            CsdlProperty rating = new CsdlProperty().setName("Rating").setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());

            // create PropertyRef for Key element
            CsdlPropertyRef propertyRef = new CsdlPropertyRef();
            propertyRef.setName("ID");

            // navigation property: many-to-one, null not allowed (product must have a category)
            CsdlNavigationProperty navProp = new CsdlNavigationProperty().setName("Profile").setType(ET_PROFILE_FQN).setNullable(false).setPartner("Reviews");
            List<CsdlNavigationProperty> navPropList = new ArrayList<>();
            navPropList.add(navProp);

            // configure EntityType
            entityType = new CsdlEntityType();
            entityType.setName(ET_REVIEW_NAME);
            entityType.setProperties(Arrays.asList(id, name, message, rating));
            entityType.setKey(Arrays.asList(propertyRef));
            entityType.setNavigationProperties(navPropList);

        } else if (entityTypeName.equals(ET_PROFILE_FQN)) {
            // create EntityType properties
            CsdlProperty id = new CsdlProperty().setName("ID").setType(EdmPrimitiveTypeKind.Int32.getFullQualifiedName());
            CsdlProperty age = new CsdlProperty().setName("Age").setType(EdmPrimitiveTypeKind.Int32.getFullQualifiedName());
            CsdlProperty name = new CsdlProperty().setName("Name").setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());
            CsdlProperty info = new CsdlProperty().setName("Info").setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());
            CsdlProperty country = new CsdlProperty().setName("Country").setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());
            CsdlProperty title = new CsdlProperty().setName("Title").setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());

            // create PropertyRef for Key element
            CsdlPropertyRef propertyRef = new CsdlPropertyRef();
            propertyRef.setName("ID");

            // navigation property: one-to-many
            CsdlNavigationProperty navProp = new CsdlNavigationProperty().setName("Reviews").setType(ET_REVIEW_FQN).setCollection(true).setPartner("Profile");
            List<CsdlNavigationProperty> navPropList = new ArrayList<>();
            navPropList.add(navProp);

            // configure EntityType
            entityType = new CsdlEntityType();
            entityType.setName(ET_PROFILE_NAME);
            entityType.setProperties(Arrays.asList(id, age, name, info, country, title));
            entityType.setKey(Arrays.asList(propertyRef));
            entityType.setNavigationProperties(navPropList);
        }
        System.out.println("getEntityType() @DemoEdmProvider  entityName: " + entityType.getName());
        return entityType;

    }

    @Override
    public CsdlEntitySet getEntitySet(FullQualifiedName entityContainer, String entitySetName) {
        System.out.println("getEntitySet() @DemoEdmProvider " + " entitySetName: " + entitySetName);
        CsdlEntitySet entitySet = null;

        if (entityContainer.equals(CONTAINER)) {
            if (entitySetName.equals(ES_REVIEWS_NAME)) {
                System.out.println("ES_REVIEWS_NAME");
                entitySet = new CsdlEntitySet();
                entitySet.setName(ES_REVIEWS_NAME);
                entitySet.setType(ET_REVIEW_FQN);

                // navigation
                CsdlNavigationPropertyBinding navPropBinding = new CsdlNavigationPropertyBinding();
                navPropBinding.setTarget("Profiles");                                         // the target entity set, where the navigation property points to
                navPropBinding.setPath("Profile");                                            // the path from entity type to navigation property
                List<CsdlNavigationPropertyBinding> navPropBindingList = new ArrayList<>();
                navPropBindingList.add(navPropBinding);
                entitySet.setNavigationPropertyBindings(navPropBindingList);

            } else if (entitySetName.equals(ES_PROFILES_NAME)) {
                System.out.println("ES_PROFILES_NAME");
                entitySet = new CsdlEntitySet();
                entitySet.setName(ES_PROFILES_NAME);
                entitySet.setType(ET_PROFILE_FQN);

                // navigation
                CsdlNavigationPropertyBinding navPropBinding = new CsdlNavigationPropertyBinding();
                navPropBinding.setTarget("Reviews"); // the target entity set, where the navigation property points to
                navPropBinding.setPath("Reviews"); // the path from entity type to navigation property
                List<CsdlNavigationPropertyBinding> navPropBindingList = new ArrayList<CsdlNavigationPropertyBinding>();
                navPropBindingList.add(navPropBinding);
                entitySet.setNavigationPropertyBindings(navPropBindingList);
            }
        }
        System.out.println("Entity set is returned");
        return entitySet;

    }

    @Override
    public CsdlEntityContainerInfo getEntityContainerInfo(FullQualifiedName entityContainerName) {
        System.out.println("getEntityContainerInfo() @DemoEdmProvider");
        // This method is invoked when displaying the service document at
        // e.g. http://localhost:8080/DemoService/DemoService.svc
        if (entityContainerName == null || entityContainerName.equals(CONTAINER)) {
            CsdlEntityContainerInfo entityContainerInfo = new CsdlEntityContainerInfo();
            entityContainerInfo.setContainerName(CONTAINER);
            System.out.println("ContainerName: " + entityContainerInfo.getContainerName());
            return entityContainerInfo;
        }
        return null;
    }

    @Override
    public List<CsdlSchema> getSchemas() throws ODataException {
        // create Schema
        System.out.println("getSchemas() @DemoEdmProvider");
        CsdlSchema schema = new CsdlSchema();
        schema.setNamespace(NAMESPACE);

        // add EntityTypes
        List<CsdlEntityType> entityTypes = new ArrayList<>();
        entityTypes.add(getEntityType(ET_REVIEW_FQN));
        entityTypes.add(getEntityType(ET_PROFILE_FQN));
        schema.setEntityTypes(entityTypes);

        // add EntityContainer
        schema.setEntityContainer(getEntityContainer());

        // add actions
        List<CsdlAction> actions = new ArrayList<>();
        actions.addAll(getActions(ACTION_RESET_FQN));
        schema.setActions(actions);

        // add functions
        List<CsdlFunction> functions = new ArrayList<>();
        functions.addAll(getFunctions(FUNCTION_COUNT_PROFILES_FQN));
        schema.setFunctions(functions);

        // finally
        List<CsdlSchema> schemas = new ArrayList<>();
        schemas.add(schema);

        return schemas;
    }

    @Override
    public CsdlEntityContainer getEntityContainer() {
        System.out.println("getEntityContainer() @DemoEdmProvider");
        // create EntitySets
        List<CsdlEntitySet> entitySets = new ArrayList<>();
        entitySets.add(getEntitySet(CONTAINER, ES_REVIEWS_NAME));
        entitySets.add(getEntitySet(CONTAINER, ES_PROFILES_NAME));

        // create EntityContainer
        CsdlEntityContainer entityContainer = new CsdlEntityContainer();
        entityContainer.setName(CONTAINER_NAME);
        entityContainer.setEntitySets(entitySets);


        // Create function imports
        List<CsdlFunctionImport> functionImports = new ArrayList<>();
        try {
            functionImports.add(getFunctionImport(CONTAINER, FUNCTION_COUNT_PROFILES));
        } catch (ODataException e) {
            e.printStackTrace();
        }

        // Create action imports
        List<CsdlActionImport> actionImports = new ArrayList<>();
        try {
            actionImports.add(getActionImport(CONTAINER, ACTION_RESET));
        } catch (ODataException e) {
            e.printStackTrace();
        }

        entityContainer.setFunctionImports(functionImports);
        entityContainer.setActionImports(actionImports);

        return entityContainer;
    }

    // Adding Actions and functions

    @Override
    public List<CsdlFunction> getFunctions(final FullQualifiedName functionName) throws ODataException {
        System.out.println("getFunctions() @DemoEdmProvider");
        if (functionName.equals(FUNCTION_COUNT_PROFILES_FQN)) {
            System.out.println("FUNCTION_COUNT_PROFILES_FQN");
            // It is allowed to overload functions, so we have to provide a list of functions for each function name
            final List<CsdlFunction> functions = new ArrayList<>();

            // Create the parameter for the function
            final CsdlParameter parameterAmount = new CsdlParameter();
            parameterAmount.setName(PARAMETER_VALUE);
            parameterAmount.setNullable(false);
            parameterAmount.setType(EdmPrimitiveTypeKind.Int32.getFullQualifiedName());

            // Create the return type of the function
            final CsdlReturnType returnType = new CsdlReturnType();
            returnType.setCollection(true);
            returnType.setType(ET_PROFILE_FQN);

            // Create the function
            final CsdlFunction function = new CsdlFunction();
            function.setName(FUNCTION_COUNT_PROFILES_FQN.getName()).setParameters(Arrays.asList(parameterAmount)).setReturnType(returnType);
            functions.add(function);

            return functions;
        }

        return null;
    }

    @Override
    public CsdlFunctionImport getFunctionImport(FullQualifiedName entityContainer, String functionImportName) throws ODataException {
        System.out.println("getFunctionImport() @DemoEdmProvider");
        if (entityContainer.equals(CONTAINER)) {
            if (functionImportName.equals(FUNCTION_COUNT_PROFILES_FQN.getName())) {
                return new CsdlFunctionImport().setName(functionImportName).setFunction(FUNCTION_COUNT_PROFILES_FQN).setEntitySet(ES_PROFILES_NAME).setIncludeInServiceDocument(true);
            }
        }
        return null;
    }


    @Override
    public List<CsdlAction> getActions(final FullQualifiedName actionName) throws ODataException {
        System.out.println("getActions() @DemoEdmProvider");
        if (actionName.equals(ACTION_RESET_FQN)) {
            System.out.println("ACTION_RESET_FQN");
            // It is allowed to overload actions, so we have to provide a list of Actions for each action name
            final List<CsdlAction> actions = new ArrayList<>();

            // Create parameters
            final List<CsdlParameter> parameters = new ArrayList<>();
            final CsdlParameter parameter = new CsdlParameter();
            parameter.setName(PARAMETER_VALUE);
            parameter.setType(EdmPrimitiveTypeKind.Int32.getFullQualifiedName());
            parameters.add(parameter);

            // Create the Csdl Action
            final CsdlAction action = new CsdlAction();
            action.setName(ACTION_RESET_FQN.getName());
            action.setParameters(parameters);
            actions.add(action);

            return actions;
        }
        return null;
    }

    @Override
    public CsdlActionImport getActionImport(final FullQualifiedName entityContainer, final String actionImportName) throws ODataException {
        System.out.println("getActionImport() @DemoEdmProvider");
        if (entityContainer.equals(CONTAINER)) {
            if (actionImportName.equals(ACTION_RESET_FQN.getName())) {
                return new CsdlActionImport().setName(actionImportName).setAction(ACTION_RESET_FQN);
            }
        }
        return null;
    }


}
