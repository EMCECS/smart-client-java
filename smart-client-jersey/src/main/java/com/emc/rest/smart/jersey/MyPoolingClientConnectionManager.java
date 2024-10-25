package com.emc.rest.smart.jersey;

import org.apache.http.impl.conn.PoolingClientConnectionManager;

public class MyPoolingClientConnectionManager extends PoolingClientConnectionManager {
    /* For each request, a new connector instance could be created.
     * We want to share the PoolingClientConnectionManager among all the Connector instances.
     * So we basically need to look after the PoolingClientConnectionManager lifecycle.
     */
    @Override
    public void shutdown() {
        // Disable shutdown of the pool. This will be done later, when this factory is closed
        // This is a workaround for finalize method on jerseys ClientRuntime which
        // closes the client and shuts down the connection pool when it is GCed.
    }

    // make sure to call realShutDown at the end
    public void realShutDown() {
        super.shutdown();
    }
}
