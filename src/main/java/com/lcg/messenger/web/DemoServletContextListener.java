package com.lcg.messenger.web;

import com.lcg.messenger.async.DemoAsyncService;
import com.lcg.messenger.data.CleanerTask;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class DemoServletContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        CleanerTask.startTask();
        System.out.println("contextInitialized() @DemoServletContextListener");
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        System.out.println("contextDestroyed() @DemoServletContextListener");
        DemoAsyncService.getInstance().shutdownThreadPool();
    }
}
