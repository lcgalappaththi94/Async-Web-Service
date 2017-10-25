package com.lcg.messenger.async;

import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.processor.Processor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Async processor "wraps" an Processor (or subclass of) to provide asynchronous support functionality
 * in combination with the DemoAsyncService.
 *
 * @param <T> "wrapped" Processor
 */
public class AsyncProcessor<T extends Processor> {
    private final ProcessorInvocationHandler handler;
    private final DemoAsyncService service;
    private final T proxyProcessor;
    private String location;
    private String preferHeader;

    /**
     * InvocationHandler which is used as proxy for the Processor method.
     */
    private static class ProcessorInvocationHandler implements InvocationHandler {
        private final Object wrappedInstance;
        private Method invokeMethod;
        private Object[] invokeParameters;
        private ODataRequest processRequest;
        private ODataResponse processResponse;

        public ProcessorInvocationHandler(Object wrappedInstance) {
            this.wrappedInstance = wrappedInstance;
            System.out.println("@ProcessorInvocationHandler @AsyncProcessor");
        }

        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
            System.out.println("invoke() @ProcessorInvocationHandler @AsyncProcessor");
            if (Processor.class.isAssignableFrom(method.getDeclaringClass())) {
                invokeMethod = method;
                invokeParameters = Arrays.copyOf(objects, objects.length);
            } else {
                throw new ODataRuntimeException("Invalid class '" + method.getDeclaringClass() + "' can not wrapped for asynchronous processing.");
            }
            return null;
        }

        /**
         * Prepare the handler for the <code>process()</code> call (which is asynchronous and can be at any time in
         * the future).
         */
        void prepareForAsync() {
            System.out.println("prepareForAsync() @ProcessorInvocationHandler @AsyncProcessor");
            processRequest = copyRequest(getParameter(ODataRequest.class));
            processResponse = createODataResponse(getParameter(ODataResponse.class));
        }

        Object process() throws InvocationTargetException, IllegalAccessException {
            System.out.println("process() @ProcessorInvocationHandler @AsyncProcessor");
            if (processRequest == null || processResponse == null) {
                throw new ODataRuntimeException("ProcessInvocationHandler was not correct prepared for async processing.");
            }
            replaceInvokeParameter(processRequest);
            replaceInvokeParameter(processResponse);
            return invokeMethod.invoke(wrappedInstance, invokeParameters);
        }

        <P> void replaceInvokeParameter(P replacement) {
            System.out.println("replaceInvokeParameter() @ProcessorInvocationHandler @AsyncProcessor");
            if (replacement == null) {
                return;
            }

            List<Object> copy = new ArrayList<>();
            for (Object parameter : invokeParameters) {
                if (replacement.getClass() == parameter.getClass()) {
                    copy.add(replacement);
                } else {
                    copy.add(parameter);
                }
            }
            invokeParameters = copy.toArray();
        }

        /**
         * Get the ODataResponse which is used when this ProcessorInvocationHandler
         * is called (via its <code>process()</code> method)
         *
         * @return ODataResponse which is used when this ProcessorInvocationHandler is called
         */
        ODataResponse getProcessResponse() {
            System.out.println("getProcessResponse() @ProcessorInvocationHandler @AsyncProcessor");
            return processResponse;
        }

        Object getWrappedInstance() {
            System.out.println("getWrappedInstance() @ProcessorInvocationHandler @AsyncProcessor");
            return this.wrappedInstance;
        }

        <P> P getParameter(Class<P> parameterClass) {
            System.out.println("getParameter() @ProcessorInvocationHandler @AsyncProcessor");
            for (Object parameter : invokeParameters) {
                if (parameter != null && parameterClass == parameter.getClass()) {
                    return parameterClass.cast(parameter);
                }
            }
            return null;
        }
    }


    public AsyncProcessor(T processor, Class<T> processorInterface, DemoAsyncService service) {
        System.out.println("@AsyncProcessor");
        Class<? extends Processor> aClass = processor.getClass();
        Class<?>[] interfaces = aClass.getInterfaces();
        handler = new ProcessorInvocationHandler(processor);
        Object proxyInstance = Proxy.newProxyInstance(aClass.getClassLoader(), interfaces, handler);
        proxyProcessor = processorInterface.cast(proxyInstance);
        this.service = service;
    }

    public T prepareFor() {
        System.out.println("prepareFor() @AsyncProcessor");
        return proxyProcessor;
    }

    public ODataRequest getRequest() {
        System.out.println("getRequest() @AsyncProcessor");
        return handler.getParameter(ODataRequest.class);
    }

    public ODataResponse getResponse() {
        System.out.println("getResponse() @AsyncProcessor");
        return handler.getParameter(ODataResponse.class);
    }

    public ODataResponse getProcessResponse() {
        System.out.println("getProcessResponse() @AsyncProcessor");
        return handler.getProcessResponse();
    }

    public String getPreferHeader() {
        System.out.println("getPreferHeader() @AsyncProcessor");
        return preferHeader;
    }

    public String getLocation() {
        System.out.println("getLocation() @AsyncProcessor");
        return location;
    }

    public Class<?> getProcessorClass() {
        System.out.println("getProcessorClass() @AsyncProcessor");
        return handler.getWrappedInstance().getClass();
    }

    /**
     * Start the asynchronous processing and returns the id for this process
     *
     * @return the id for this process
     * @throws ODataApplicationException
     * @throws ODataLibraryException
     */
    public String processAsync() throws ODataApplicationException, ODataLibraryException, SQLException {
        System.out.println("processAsync() @AsyncProcessor");
        preferHeader = getRequest().getHeader(HttpHeader.PREFER);
        handler.prepareForAsync();
        String hh = service.processAsynchronous(this);
        return hh;
    }

    private static ODataResponse createODataResponse(ODataResponse response) {
        System.out.println("createODataResponse() @AsyncProcessor");
        ODataResponse created = new ODataResponse();
        for (Map.Entry<String, List<String>> header : response.getAllHeaders().entrySet()) {
            created.addHeader(header.getKey(), header.getValue());
        }
        return created;
    }

    Object process() throws InvocationTargetException, IllegalAccessException {
        System.out.println("process() @AsyncProcessor");
        return handler.process();
    }

    void setLocation(String loc) {
        this.location = loc;
    }

    static ODataRequest copyRequest(ODataRequest request) {
        System.out.println("copyRequest() @AsyncProcessor");
        ODataRequest req = new ODataRequest();
        req.setBody(copyRequestBody(request));
        req.setMethod(request.getMethod());
        req.setRawBaseUri(request.getRawBaseUri());
        req.setRawODataPath(request.getRawODataPath());
        req.setRawQueryPath(request.getRawQueryPath());
        req.setRawRequestUri(request.getRawRequestUri());
        req.setRawServiceResolutionUri(request.getRawServiceResolutionUri());

        for (Map.Entry<String, List<String>> header : request.getAllHeaders().entrySet()) {
            if (!HttpHeader.PREFER.toLowerCase().equals(header.getKey().toLowerCase())) {
                req.addHeader(header.getKey(), header.getValue());
            }
        }
        return req;
    }

    static InputStream copyRequestBody(ODataRequest request) {
        System.out.println("copyRequestBody() @AsyncProcessor");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        InputStream input = request.getBody();
        if (input != null) {
            try {
                ByteBuffer inBuffer = ByteBuffer.allocate(8192);
                ReadableByteChannel ic = Channels.newChannel(input);
                WritableByteChannel oc = Channels.newChannel(buffer);
                while (ic.read(inBuffer) > 0) {
                    inBuffer.flip();
                    oc.write(inBuffer);
                    inBuffer.rewind();
                }
                return new ByteArrayInputStream(buffer.toByteArray());
            } catch (IOException e) {
                throw new ODataRuntimeException("Error on reading request content");
            }
        }
        return null;
    }
}
