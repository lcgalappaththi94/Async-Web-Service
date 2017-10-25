package com.lcg.messenger.web;

import com.lcg.messenger.data.DataProvider;
import com.lcg.messenger.service.*;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ServiceMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

public class DemoServlet extends HttpServlet {


    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DemoServlet.class);

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Instant start = Instant.now();
            System.out.println("start>>> Service() @DemoServlet");
            HttpSession session = req.getSession(true);
            DataProvider dataprovider = (DataProvider) session.getAttribute(DataProvider.class.getName());
            if (dataprovider == null) {
                dataprovider = new DataProvider();
                session.setAttribute(DataProvider.class.getName(), dataprovider);
            }

            // create odata handler and configure it with EdmProvider and Processor
            OData odata = OData.newInstance();
            ServiceMetadata edm = odata.createServiceMetadata(new DemoEdmProvider(), new ArrayList<>());
            ODataHttpHandler handler = odata.createHandler(edm);
            handler.register(new DemoEntityCollectionProcessor(dataprovider));
            handler.register(new DemoEntityProcessor(dataprovider));
            handler.register(new DemoPrimitiveProcessor(dataprovider));
            handler.register(new DemoActionProcessor(dataprovider));
            handler.register(new DemoBatchProcessor(dataprovider));
            // let the handler do the work
            handler.process(req, resp);
            Instant end = Instant.now();
            LOG.info("Completed Successfully");
            System.out.println("end>>> Service() @DemoServlet with elapsed time: " + Duration.between(start, end).toMillis() + " ms");
        } catch (RuntimeException e) {
            System.out.println("Exception Service() @DemoServlet >>> " + e);
            LOG.error("Server Error occurred in DemoServlet", e);
            throw new ServletException(e);
        }
    }
}
