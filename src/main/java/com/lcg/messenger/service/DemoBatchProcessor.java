package com.lcg.messenger.service;

import com.lcg.messenger.async.AsyncHelper;
import com.lcg.messenger.async.AsyncProcessor;
import com.lcg.messenger.async.DemoAsyncService;
import com.lcg.messenger.data.DataProvider;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.batch.BatchFacade;
import org.apache.olingo.server.api.deserializer.batch.BatchOptions;
import org.apache.olingo.server.api.deserializer.batch.BatchRequestPart;
import org.apache.olingo.server.api.deserializer.batch.ODataResponsePart;
import org.apache.olingo.server.api.prefer.PreferencesApplied;
import org.apache.olingo.server.api.processor.BatchProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DemoBatchProcessor extends DemoEntityProcessor implements BatchProcessor {

    public DemoBatchProcessor(final DataProvider dataProvider) {
        super(dataProvider);

    }

    @Override
    public void processBatch(final BatchFacade facade, final ODataRequest request, final ODataResponse response) throws ODataApplicationException, ODataLibraryException {
        // only the first batch call (process batch) must be handled in a separate way for async support
        // because a changeset has to be wrapped within a process batch call
        System.out.println("processBatch() @DemoBatchProcessor");
        Instant start = Instant.now();
        ContentType responseFormat = ContentType.TEXT_PLAIN;
        if (odata.createPreferences(request.getHeaders(HttpHeader.PREFER)).hasRespondAsync()) {
            System.out.println("hasRespondAsync in processBatch " + true);
            DemoAsyncService asyncService = DemoAsyncService.getInstance();
            BatchProcessor processor = new DemoBatchProcessor(dataprovider);
            processor.init(odata, serviceMetadata);
            AsyncProcessor<BatchProcessor> asyncProcessor = asyncService.register(processor, BatchProcessor.class);    //register demo batch processor as a async processor
            asyncProcessor.prepareFor().processBatch(facade, request, response);
            String location = null;
            try {
                location = asyncProcessor.processAsync();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            try {
                AsyncHelper.wait(odata, request, response, location, start, responseFormat);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        final boolean continueOnError = odata.createPreferences(request.getHeaders(HttpHeader.PREFER)).hasContinueOnError();
        System.out.println("hasContinueOnError " + continueOnError);

        final String boundary = facade.extractBoundaryFromContentType(request.getHeader(HttpHeader.CONTENT_TYPE));
        final BatchOptions options = BatchOptions.with().rawBaseUri(request.getRawBaseUri()).rawServiceResolutionUri(request.getRawServiceResolutionUri()).build();
        final List<BatchRequestPart> parts = odata.createFixedFormatDeserializer().parseBatchRequest(request.getBody(), boundary, options);
        final List<ODataResponsePart> responseParts = new ArrayList<>();

        for (BatchRequestPart part : parts) {
            final ODataResponsePart responsePart = facade.handleBatchRequest(part);
            responseParts.add(responsePart);                                              // Also add failed responses.
            final int statusCode = responsePart.getResponses().get(0).getStatusCode();

            if ((statusCode >= 400 && statusCode <= 600) && !continueOnError) {
                break;                                                                    // Stop processing, but serialize responses to all recent requests.
            }
        }

        final String responseBoundary = "batch_" + UUID.randomUUID().toString();
        final InputStream responseContent = odata.createFixedFormatSerializer().batchResponse(responseParts, responseBoundary);
        response.setHeader(HttpHeader.CONTENT_TYPE, ContentType.MULTIPART_MIXED + ";boundary=" + responseBoundary);
        response.setContent(responseContent);
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        if (continueOnError) {
            response.setHeader(HttpHeader.PREFERENCE_APPLIED, PreferencesApplied.with().continueOnError().build().toValueString());
        }
    }

    @Override
    public ODataResponsePart processChangeSet(final BatchFacade facade, final List<ODataRequest> requests) throws ODataApplicationException, ODataLibraryException {
        System.out.println("processChangeSet() @DemoBatchProcessor");
        List<ODataResponse> responses = new ArrayList<>();

        for (ODataRequest request : requests) {
            final ODataResponse oDataResponse = facade.handleODataRequest(request);             //separate processors will handle each request
            final int statusCode = oDataResponse.getStatusCode();                               //get response code
            System.out.println("request method: " + request.getMethod() + " status code: " + statusCode);

            if (statusCode < 400) {
                responses.add(oDataResponse);
            } else {
                // Rollback
                // OData Version 4.0 Part 1: Protocol Plus Errata 01
                // 11.7.4 Responding to a Batch Request
                //
                // When a request within a change set fails, the change set response is not represented using
                // the multipart/mixed media type. Instead, a single response, using the application/http media type
                // and a Content-Transfer-Encoding header with a value of binary, is returned that applies to all requests
                // in the change set and MUST be formatted according to the Error Handling defined
                // for the particular response format.

                return new ODataResponsePart(oDataResponse, false);
            }
        }

        return new ODataResponsePart(responses, true);
    }
}
