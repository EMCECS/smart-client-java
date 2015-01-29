package com.emc.rest.smart;

import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Future;

public class SmartConnector implements Connector {
    public static final String BYPASS_LOAD_BALANCER = "com.emc.rest.smart.bypassLoadBalancer";

    private Connector delegated;
    private LoadBalancer loadBalancer;

    public SmartConnector(Connector delegated, LoadBalancer loadBalancer) {
        this.delegated = delegated;
        this.loadBalancer = loadBalancer;
    }

    @Override
    public ClientResponse apply(ClientRequest request) {
        // check for bypass flag
        Boolean bypass = (Boolean) request.getProperty(BYPASS_LOAD_BALANCER);
        if (bypass != null && bypass) {
            return delegated.apply(request);
        }

        // get highest ranked host for next request
        Host host = loadBalancer.getTopHost();

        // replace the host in the request
        URI uri = request.getUri();
        try {
            uri = new URI(uri.getScheme(), uri.getUserInfo(), host.getName(), uri.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException("load-balanced host generated invalid URI", e);
        }
        request.setUri(uri);

        // track requests stats for LB ranking
        Long startTime = System.currentTimeMillis();
        host.connectionOpened(); // not really, but we can't (cleanly) intercept any lower than this
        try {
            // call to delegate
            ClientResponse response = delegated.apply(request);

            // capture request stats
            host.callComplete((int) (System.currentTimeMillis() - startTime), false);

            // wrap the input stream so we can capture the actual connection close
            response.setEntityStream(new WrappedInputStream(response.getEntityStream(), host));

            return response;
        } catch (RuntimeException e) {

            // capture requests stats (error)
            host.callComplete((int) (System.currentTimeMillis() - startTime), true);
            host.connectionClosed();

            throw e;
        }
    }

    /**
     * We assume here that the delegated implementation calls the standard apply method asynchronously. If it does
     * not, load balancing logic will not apply to asynchronous calls.
     */
    @Override
    public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
        return delegated.apply(request, callback);
    }

    @Override
    public String getName() {
        return delegated.getName();
    }

    @Override
    public void close() {
        delegated.close();
    }

    /**
     * captures closure in host statistics
     */
    protected class WrappedInputStream extends InputStream {
        private InputStream delegated;
        private Host host;
        private boolean closed = false;

        public WrappedInputStream(InputStream delegated, Host host) {
            this.delegated = delegated;
            this.host = host;
        }

        @Override
        public int read() throws IOException {
            return delegated.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return delegated.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegated.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return delegated.skip(n);
        }

        @Override
        public int available() throws IOException {
            return delegated.available();
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                if (!closed) {
                    host.connectionClosed(); // capture closure
                    closed = true;
                }
            }
            delegated.close();
        }

        @Override
        public void mark(int readlimit) {
            delegated.mark(readlimit);
        }

        @Override
        public void reset() throws IOException {
            delegated.reset();
        }

        @Override
        public boolean markSupported() {
            return delegated.markSupported();
        }
    }
}
