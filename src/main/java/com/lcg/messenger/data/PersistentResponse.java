package com.lcg.messenger.data;

import org.apache.olingo.server.api.ODataResponse;

public interface PersistentResponse {

    void write(String location, ODataResponse response);

    String read(String location);

    void cleanUp();

    boolean find(String location);

    void delete(String location);

}
