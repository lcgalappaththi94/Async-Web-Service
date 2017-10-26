package com.lcg.messenger.data;

import com.lcg.messenger.async.DemoAsyncService;

import java.util.concurrent.TimeUnit;

public class Delete implements Runnable {
    private String location;
    private boolean fileDbDelete;
    private int sleep = 2;

    public Delete(String location, boolean fileDbDelete) {
        this.fileDbDelete = fileDbDelete;
        this.location = location;
    }

    @Override
    public void run() {
        if (fileDbDelete) {
            DemoAsyncService.persistentResponse.delete(getLocation());
        } else {
            System.out.println("------------------------------------------Runner remove initialized-----------------------------");
            try {
                TimeUnit.MINUTES.sleep(sleep);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            DemoAsyncService.LOCATION_2_ASYNC_RUNNER.remove(getLocation());
            System.out.println("------------------------------------------Runner removed----------------------------------------");
        }
    }

    public String getLocation() {
        return location;
    }

}
