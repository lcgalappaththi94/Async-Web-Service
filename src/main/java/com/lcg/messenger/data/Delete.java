package com.lcg.messenger.data;

import com.lcg.messenger.async.DemoAsyncService;

public class Delete extends Thread {
    private String location;

    public Delete(String location) {
        this.location = location;
    }

    @Override
    public void run() {
        DemoAsyncService.persistentResponse.delete(getLocation());
    }

    public String getLocation() {
        return location;
    }

}
