/*
 * Copyright (c) 2015-2021 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.emc.rest.smart.jersey;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.rest.smart.Host;
import com.emc.rest.smart.SmartClientException;
import com.emc.rest.smart.SmartConfig;

public class SmartFilter implements Connector {
    private static final Logger log = LoggerFactory.getLogger(SmartFilter.class);

    public static final String BYPASS_LOAD_BALANCER = "com.emc.rest.smart.bypassLoadBalancer";

    private final Connector delegate;
    private final SmartConfig smartConfig;

    public SmartFilter(Connector delegate, SmartConfig smartConfig) {
        this.delegate = delegate;
        this.smartConfig = smartConfig;
    }

    @Override
    public ClientResponse apply(ClientRequest request) {
        // check for bypass flag
        Boolean bypass = (Boolean) request.getProperty(BYPASS_LOAD_BALANCER);
        if (bypass != null && bypass) {
            return delegate.apply(request);
        }

        int maxRetries = smartConfig.getMaxRetryAttempts();
        RuntimeException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            // get highest ranked host for next request
            Map<String, Object> requestProperties = getRequestProperties(request);
            Host host = smartConfig.getLoadBalancer().getTopHost(requestProperties);

            // replace the host in the request
            rewriteUri(request, host);

            // track requests stats for LB ranking
            host.connectionOpened(); // not really, but we can't (cleanly) intercept any lower than this
            try {
                // call to delegate
                ClientResponse response = delegate.apply(request);

                // capture request stats
                // except for 501 (not implemented), all 50x responses are considered server errors
                host.callComplete(response.getStatus() >= 500 && response.getStatus() != 501);

                // wrap the input stream so we can capture the actual connection close
                response.setEntityStream(new WrappedInputStream(response.getEntityStream(), host));

                return response;
            } catch (RuntimeException e) {
                // Connection-level failures (timeouts, refused, etc.) and unknown errors
                // should be counted against the host. Only client-side (4xx) errors from
                // SmartClientException are not the host's fault.
                boolean isError = isHostError(e);
                host.callComplete(isError);
                host.connectionClosed();

                // retry transparently on connection-level errors
                if (isConnectError(e) && attempt < maxRetries) {
                    log.warn("connection error to host {} (attempt {}/{}), retrying with next host: {}",
                            host.getName(), attempt + 1, maxRetries + 1, e.toString());
                    lastException = e;
                    continue;
                }

                throw e;
            }
        }
        // all retry attempts exhausted
        throw lastException;
    }

    @Override
    public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
        // check for bypass flag
        Boolean bypass = (Boolean) request.getProperty(BYPASS_LOAD_BALANCER);
        if (bypass != null && bypass) {
            return delegate.apply(request, callback);
        }

        return applyAsync(request, callback, 0);
    }

    private Future<?> applyAsync(ClientRequest request, AsyncConnectorCallback callback, int attempt) {
        int maxRetries = smartConfig.getMaxRetryAttempts();

        // get highest ranked host for next request
        Map<String, Object> requestProperties = getRequestProperties(request);
        Host host = smartConfig.getLoadBalancer().getTopHost(requestProperties);

        // replace the host in the request
        rewriteUri(request, host);

        // track requests stats for LB ranking
        host.connectionOpened();

        return delegate.apply(request, new AsyncConnectorCallback() {
            @Override
            public void response(ClientResponse response) {
                host.callComplete(response.getStatus() >= 500 && response.getStatus() != 501);
                response.setEntityStream(new WrappedInputStream(response.getEntityStream(), host));
                callback.response(response);
            }

            @Override
            public void failure(Throwable failure) {
                boolean isError = isHostError(failure);
                host.callComplete(isError);
                host.connectionClosed();

                // retry transparently on connection-level errors
                if (isConnectError(failure) && attempt < maxRetries) {
                    log.warn("async connection error to host {} (attempt {}/{}), retrying with next host: {}",
                            host.getName(), attempt + 1, maxRetries + 1, failure.toString());
                    applyAsync(request, callback, attempt + 1);
                    return;
                }

                callback.failure(failure);
            }
        });
    }

    @Override
    public String getName() {
        return "SmartFilter(" + delegate.getName() + ")";
    }

    @Override
    public void close() {
        delegate.close();
    }

    private void rewriteUri(ClientRequest request, Host host) {
        URI uri = request.getUri();
        try {
            org.apache.http.HttpHost httpHost = new org.apache.http.HttpHost(host.getName(), uri.getPort(), uri.getScheme());
            // NOTE: flags were added in httpclient 4.5.8 to allow for no normalization (which matches behavior prior to 4.5.7)
            uri = org.apache.http.client.utils.URIUtils.rewriteURI(uri, httpHost, org.apache.http.client.utils.URIUtils.NO_FLAGS);
        } catch (URISyntaxException e) {
            throw new RuntimeException("load-balanced host generated invalid URI", e);
        }
        request.setUri(uri);
    }

    private Map<String, Object> getRequestProperties(ClientRequest request) {
        Map<String, Object> props = new HashMap<>();
        for (String name : request.getPropertyNames()) {
            props.put(name, request.getProperty(name));
        }
        return props;
    }

    /**
     * Determines if an exception should be counted as a host error for load-balancing purposes.
     * All exceptions are considered host errors except SmartClientException with ErrorType.Client
     * (4xx), which indicates a caller error, not a host problem.
     */
    public static boolean isHostError(Throwable t) {
        if (t instanceof SmartClientException) {
            return ((SmartClientException) t).getErrorType() != SmartClientException.ErrorType.Client;
        }
        return true;
    }

    /**
     * Checks whether the given exception (or any cause in its chain) is a connection-level error
     * that occurred before any request data was sent, making it safe to retry on a different host.
     */
    public static boolean isConnectError(Throwable t) {
        while (t != null) {
            if (t instanceof ConnectException
                    || t instanceof NoRouteToHostException
                    || t instanceof UnknownHostException) {
                return true;
            }
            // Socket timeout during connection phase (not read timeout)
            if (t instanceof SocketTimeoutException
                    && t.getMessage() != null
                    && t.getMessage().toLowerCase(java.util.Locale.ROOT).contains("connect")) {
                return true;
            }
            // Apache HttpClient connect timeout
            // (org.apache.http.conn.ConnectTimeoutException extends InterruptedIOException)
            if ("org.apache.http.conn.ConnectTimeoutException".equals(t.getClass().getName())) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * captures closure in host statistics
     */
    protected static class WrappedInputStream extends FilterInputStream {
        private final Host host;
        private boolean closed = false;

        public WrappedInputStream(InputStream in, Host host) {
            super(in);
            this.host = host;
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                if (!closed) {
                    host.connectionClosed(); // capture closure
                    closed = true;
                }
            }
            super.close();
        }
    }
}
