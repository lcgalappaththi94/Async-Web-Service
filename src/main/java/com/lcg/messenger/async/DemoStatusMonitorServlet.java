package com.lcg.messenger.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class DemoStatusMonitorServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DemoStatusMonitorServlet.class);

    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        try {
            DemoAsyncService asyncService = DemoAsyncService.getInstance();
            System.out.println("Status monitor request path: " + request.getPathInfo());
            if ("/list".equals(request.getPathInfo())) {
                asyncService.listQueue(response);
            } else if (asyncService.isStatusMonitorResource(request)) {
                asyncService.handle(request, response);
                System.out.println("isStatusMonitorResource");
            }
        } catch (Exception e) {
            LOG.error("Server Error", e);
            e.printStackTrace();
            throw new ServletException(e);
        }
    }
}
