package com.lcg.messenger.async;

import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

public class AsyncHelper {

    public static void wait(OData odata, ODataRequest request, ODataResponse response, String location, Instant start, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException, SQLException {
        int wait = getWait(request, odata);
        System.out.println("wait value : " + wait);

        System.out.println("------------------------------------------waiting started----------------------------------------");
        while (!DemoAsyncService.persistentResponse.find(location) && Duration.between(start, Instant.now()).toMillis() < wait * 1000)
            ;
        System.out.println("------------------------------------------waiting ended------------------------------------------");


        if (!DemoAsyncService.persistentResponse.find(location)) {
            System.out.println("file or database data  not found!!!");
            DemoAsyncService.acceptedResponse(response, location);                                        //send location
        } else {
            String data = DemoAsyncService.persistentResponse.read(location);
            InputStream is = new ByteArrayInputStream(data.getBytes());
            response.setContent(is);
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        }
    }

    public static int getWait(ODataRequest request, OData odata) {
        try {
            Integer wait = odata.createPreferences(request.getHeaders(HttpHeader.PREFER)).getWait();
            return wait;
        } catch (Exception e) {
            return 0;
        }
    }

}
