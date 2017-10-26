package com.lcg.messenger.async;

import com.lcg.messenger.data.Delete;
import com.lcg.messenger.data.FileSystem;
import com.lcg.messenger.data.PersistentResponse;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.format.PreferenceName;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.Processor;
import org.apache.olingo.server.api.serializer.SerializerException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DemoAsyncService {

    public static final String TEC_ASYNC_SLEEP = "sleep";
    public static final String STATUS_MONITOR_TOKEN = "status";

    public static PersistentResponse persistentResponse = new FileSystem();                                                                      //select the persistence storage

    public static final Map<String, AsyncRunner> LOCATION_2_ASYNC_RUNNER = Collections.synchronizedMap(new HashMap<String, AsyncRunner>());      // status monitor url with runner

    private static final ExecutorService ASYNC_REQUEST_EXECUTOR = Executors.newFixedThreadPool(20);
    public static final ExecutorService DELETE_EXECUTOR = Executors.newFixedThreadPool(20);

    public <T extends Processor> AsyncProcessor<T> register(T processor, Class<T> processorInterface) {
        System.out.println("register() @DemoAsyncService");
        return new AsyncProcessor<>(processor, processorInterface, this);
    }

    public static UUID generateUUID() {
        return UUID.randomUUID();
    }

    public static void updateHeader(ODataResponse response, HttpStatusCode status, String location) {
        System.out.println("updateHeader() @DemoAsyncService");
        response.setStatusCode(status.getStatusCode());
        response.setHeader(HttpHeader.LOCATION, location);
        response.setHeader(HttpHeader.PREFERENCE_APPLIED, PreferenceName.RESPOND_ASYNC.toString());
    }

    public static void acceptedResponse(ODataResponse response, String location) {
        System.out.println("acceptedResponse() @DemoAsyncService");
        updateHeader(response, HttpStatusCode.ACCEPTED, location);
    }

    private static final class AsyncProcessorHolder {
        private static final DemoAsyncService INSTANCE = new DemoAsyncService();
    }

    public static DemoAsyncService getInstance() {
        System.out.println("getInstance() @DemoAsyncService");
        return AsyncProcessorHolder.INSTANCE;
    }

    public void shutdownThreadPools() {
        System.out.println("shutdownThreadPool() @DemoAsyncService");
        ASYNC_REQUEST_EXECUTOR.shutdown();
        DELETE_EXECUTOR.shutdown();
    }

    public boolean isStatusMonitorResource(HttpServletRequest request) {
        System.out.println("isStatusMonitorResource() @DemoAsyncService");
        System.out.println("STATUS_MONITOR_TOKEN: " + STATUS_MONITOR_TOKEN);
        System.out.println("isStatusMonitorResource : " + request.getRequestURL().toString().contains(STATUS_MONITOR_TOKEN));
        return request.getRequestURL() != null && request.getRequestURL().toString().contains(STATUS_MONITOR_TOKEN);
    }

    String processAsynchronous(AsyncProcessor<?> dispatchedProcessor) throws ODataApplicationException, ODataLibraryException, SQLException {
        // use executor thread pool
        System.out.println("processAsynchronous() @DemoAsyncService");
        String location = createNewAsyncLocation(dispatchedProcessor.getRequest());
        dispatchedProcessor.setLocation(location);
        AsyncRunner run = new AsyncRunner(dispatchedProcessor);
        LOCATION_2_ASYNC_RUNNER.put(location, run);
        ASYNC_REQUEST_EXECUTOR.execute(run);
        return location;
    }

    public void handle(HttpServletRequest request, HttpServletResponse response) throws SerializerException, IOException, SQLException {
        System.out.println("handle() @DemoAsyncService");
        String location = getAsyncLocation(request);
        AsyncRunner runner = LOCATION_2_ASYNC_RUNNER.get(location);

        if (runner == null) {
            System.out.println("runner finished and null");
            if (!persistentResponse.find(location)) {
                System.out.println(persistentResponse.getClass().getName() + " data not found");
                response.setStatus(HttpStatusCode.NOT_FOUND.getStatusCode());
            } else {
                String data = persistentResponse.read(location);
                wrapFileToAsyncHttpResponse(data, response);
            }
        } else if (runner.isFinished()) {
            System.out.println("runner finished");

            String data = persistentResponse.read(location);                                   //TODO Check these three lines
            wrapFileToAsyncHttpResponse(data, response);                                       //TODO reading response from file
            //wrapToAsyncHttpResponse(runner.getDispatched().getProcessResponse(),location);   //TODO reading response from runner no content in odata response here

            Delete run = new Delete(location, true);
            DELETE_EXECUTOR.execute(run);
            if (LOCATION_2_ASYNC_RUNNER.get(location) != null)
                LOCATION_2_ASYNC_RUNNER.remove(location);
        } else {
            System.out.println("runner not finished");
            response.setStatus(HttpStatusCode.ACCEPTED.getStatusCode());
            response.setHeader(HttpHeader.LOCATION, location);
        }
    }


    public void listQueue(HttpServletResponse response) {
        System.out.println("listQueue() @DemoAsyncService");
        StringBuilder sb = new StringBuilder();
        sb.append("<html><header/><body><h1>Queued requests</h1><ul>");
        for (Map.Entry<String, AsyncRunner> entry : LOCATION_2_ASYNC_RUNNER.entrySet()) {
            AsyncProcessor<?> asyncProcessor = entry.getValue().getDispatched();                            //get the asynchronous processor
            sb.append("<li><b>ID: </b>").append(entry.getKey()).append("<br/>")                             //get the  status link
                    .append("<b>Location: </b><a href=\"")
                    .append(asyncProcessor.getLocation()).append("\">")                                     //same as entry.getKey())
                    .append(asyncProcessor.getLocation()).append("</a><br/>")
                    .append("<b>Processor: </b>").append(asyncProcessor.getProcessorClass().getSimpleName()).append("<br/>")
                    .append("<b>Finished: </b>").append(entry.getValue().isFinished()).append("<br/>")
                    .append("</li>");
        }
        sb.append("</ul></body></html>");
        writeToResponse(response, sb.toString());
    }


    private static void writeToResponse(HttpServletResponse response, InputStream input) throws IOException {
        System.out.println("writeToResponse(HttpServletResponse response, InputStream input, String location) @DemoAsyncService");
        copy(input, response.getOutputStream());
    }

    private void writeToResponse(HttpServletResponse response, String content) {
        System.out.println("writeToResponse(HttpServletResponse response, String content) @DemoAsyncService");
        writeToResponse(response, content.getBytes());
    }

    private static void writeToResponse(HttpServletResponse response, byte[] content) {
        System.out.println("writeToResponse(HttpServletResponse response, byte[] content) @DemoAsyncService");
        OutputStream output = null;
        try {
            output = response.getOutputStream();
            output.write(content);
        } catch (IOException e) {
            throw new ODataRuntimeException(e);
        } finally {
            closeStream(output);
        }
    }

    static void wrapToAsyncHttpResponse(final ODataResponse odResponse, final HttpServletResponse response) throws SerializerException, IOException {
        System.out.println("wrapToAsyncHttpResponse() @DemoAsyncService");
        OData odata = OData.newInstance();
        InputStream odResponseStream = odata.createFixedFormatSerializer().asyncResponse(odResponse);
        response.setHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_HTTP.toContentTypeString());
        response.setHeader(HttpHeader.CONTENT_ENCODING, "binary");
        response.setStatus(HttpStatusCode.OK.getStatusCode());
        writeToResponse(response, odResponseStream);
    }

    public static void wrapFileToAsyncHttpResponse(final String odResponse, final HttpServletResponse response) throws SerializerException, IOException {
        System.out.println("wrapFileToAsyncHttpResponse() @DemoAsyncService");
        try {
            InputStream stream = new ByteArrayInputStream(odResponse.getBytes());
            response.setHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON.toContentTypeString());
            response.setHeader(HttpHeader.CONTENT_ENCODING, "binary");
            response.setStatus(HttpStatusCode.OK.getStatusCode());
            writeToResponse(response, stream);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void copy(final InputStream input, final OutputStream output) {
        System.out.println("copy() @DemoAsyncService");
        if (output == null || input == null) {
            return;
        }

        try {
            ByteBuffer inBuffer = ByteBuffer.allocate(8192);
            ReadableByteChannel ic = Channels.newChannel(input);
            WritableByteChannel oc = Channels.newChannel(output);
            while (ic.read(inBuffer) > 0) {
                inBuffer.flip();
                oc.write(inBuffer);
                inBuffer.rewind();
            }
        } catch (IOException e) {
            throw new ODataRuntimeException("Error on reading request content");
        } finally {
            closeStream(input);
            closeStream(output);
        }
    }

    private static void closeStream(final Closeable closeable) {
        System.out.println("closeStream() @DemoAsyncService");
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String createNewAsyncLocation(ODataRequest request) throws SQLException {
        System.out.println("createNewAsyncLocation() @DemoAsyncService");
        int pos = request.getRawBaseUri().lastIndexOf("/") + 1;
        String uri = request.getRawBaseUri().substring(0, pos) + STATUS_MONITOR_TOKEN + "/" + generateUUID();
        System.out.println("Async location: " + uri);
        return uri;
    }

    private String getAsyncLocation(HttpServletRequest request) {
        System.out.println("getAsyncLocation() @DemoAsyncService ");
        System.out.println(request.getRequestURL().toString());
        return request.getRequestURL().toString();
    }


    /**
     * Runnable for the AsyncProcessor.
     */
    static class AsyncRunner implements Runnable {
        private static final Pattern PATTERN = Pattern.compile(TEC_ASYNC_SLEEP + "=\\d*");
        private final AsyncProcessor<? extends Processor> dispatched;
        private int defaultSleepTimeInSeconds = 0;
        private boolean finished = false;

        public AsyncRunner(AsyncProcessor<? extends Processor> wrap) {
            this(wrap, 0);
            System.out.println("@AsyncRunner @DemoAsyncService");
        }

        public AsyncRunner(AsyncProcessor<? extends Processor> wrap, int defaultSleepTimeInSeconds) {
            this.dispatched = wrap;
            if (defaultSleepTimeInSeconds > 0) {
                System.out.println("AsyncRunner @DemoAsyncService SleepTime: " + defaultSleepTimeInSeconds);
                this.defaultSleepTimeInSeconds = defaultSleepTimeInSeconds;
            }
        }

        @Override
        public void run() {
            System.out.println("run() @AsyncRunner @DemoAsyncService");
            try {
                int sleep = getSleepTime(dispatched);
                TimeUnit.SECONDS.sleep(sleep);
                dispatched.process();
            } catch (final Exception e) {
                e.printStackTrace();
            }

            finished = true;
            System.out.println("run finished: " + finished + " location: " + dispatched.getLocation());
            final ODataResponse wrapResult = dispatched.getProcessResponse();
            try {
                persistentResponse.write(dispatched.getLocation(), wrapResult);                           //TODO check this line
                Delete run = new Delete(dispatched.getLocation(), false);
                DELETE_EXECUTOR.execute(run);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private int getSleepTime(AsyncProcessor<? extends Processor> wrap) {
            System.out.println("getSleepTime() @AsyncRunner @DemoAsyncService");
            String preferHeader = wrap.getPreferHeader();
            System.out.println("Prefer header" + preferHeader);
            Matcher matcher = PATTERN.matcher(preferHeader);
            if (matcher.find()) {
                String waitTimeAsString = matcher.group(0).replace("sleep=", "");
                System.out.println("match found for sleep: " + waitTimeAsString);
                return Integer.parseInt(waitTimeAsString);
            }
            return defaultSleepTimeInSeconds;                                                                              //0 seconds
        }


        public boolean isFinished() {
            System.out.println("isFinished() @AsyncRunner @DemoAsyncService");
            return finished;
        }

        public AsyncProcessor<? extends Processor> getDispatched() {
            System.out.println("getDispatched() @AsyncRunner @DemoAsyncService");
            return dispatched;
        }

    }
}
