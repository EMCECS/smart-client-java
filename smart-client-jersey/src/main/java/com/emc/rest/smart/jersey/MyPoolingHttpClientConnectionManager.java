package com.emc.rest.smart.jersey;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class MyPoolingHttpClientConnectionManager extends PoolingHttpClientConnectionManager {

    /* For each request, a new connector instance could be created.
    * We want to share the PoolingHttpClientConnectionManager among all the Connector instances.
    * So we basically need to look after the PoolingHttpClientConnectionManager lifecycle.
    */
    @Override
    public void shutdown() {
        // do nothing
    }
    public void realShutDown() {
        super.shutdown();
    }
}
