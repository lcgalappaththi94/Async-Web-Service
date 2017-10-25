package com.lcg.messenger.service;

import com.lcg.messenger.data.DataProvider;
import org.apache.olingo.commons.api.data.Parameter;
import org.apache.olingo.commons.api.edm.EdmAction;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.processor.ActionVoidProcessor;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResourceAction;

import java.util.Locale;
import java.util.Map;

public class DemoActionProcessor implements ActionVoidProcessor {

    private OData odata;
    private DataProvider dataprovider;

    public DemoActionProcessor(final DataProvider dataprovider) {
        System.out.println("@DemoActionProcessor");
        this.dataprovider = dataprovider;
    }

    @Override
    public void init(final OData odata, final ServiceMetadata serviceMetadata) {
        System.out.println("init() @DemoActionProcessor");
        this.odata = odata;
    }

    @Override
    public void processActionVoid(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat) throws ODataApplicationException, ODataLibraryException {
        System.out.println("processActionVoid() @DemoActionProcessor");
        // 1st Get the action from the resource path
        final EdmAction edmAction = ((UriResourceAction) uriInfo.asUriInfoResource().getUriResourceParts().get(0)).getAction();

        // 2nd Deserialize the parameter
        // In our case there is only one action. So we can be sure that parameter "Value" has been provided by the client
        if (requestFormat == null) {
            throw new ODataApplicationException("The content type has not been set in the request.", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ROOT);
        }

        final ODataDeserializer deserializer = odata.createDeserializer(requestFormat);
        final Map<String, Parameter> actionParameter = deserializer.actionParameters(request.getBody(), edmAction).getActionParameters();
        final Parameter parameterValue = actionParameter.get(DemoEdmProvider.PARAMETER_VALUE);

        // The parameter value is nullable
        if (parameterValue.isNull()) {
            dataprovider.resetDataSet();
        } else {
            final Integer value = (Integer) parameterValue.asPrimitive();
            dataprovider.resetDataSet(value);
        }

        response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    }
}
