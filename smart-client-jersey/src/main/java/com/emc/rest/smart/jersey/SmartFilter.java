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

import com.emc.rest.smart.Host;
import com.emc.rest.smart.SmartClientException;
import com.emc.rest.smart.SmartConfig;
import org.glassfish.jersey.client.spi.Connector;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Configuration;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.ConnectorProvider;

import javax.ws.rs.client.Client;

public class SmartFilter implements Connector {
    public static final String BYPASS_LOAD_BALANCER = "com.emc.rest.smart.bypassLoadBalancer";

    private final SmartConfig smartConfig;
    private final Connector delegate;

    public SmartFilter(SmartConfig smartConfig, Connector delegate) {
        this.smartConfig = smartConfig;
        this.delegate = delegate;
    }

    @Override
    public ClientResponse apply(ClientRequest request) {
        // check for bypass flag
        Boolean bypass = (Boolean) request.getProperty(BYPASS_LOAD_BALANCER);
        if (bypass != null && bypass) {
            return delegate.apply(request);
        }

        // build properties map for veto rules
        Map<String, Object> requestProperties = new HashMap<>();
        for (String name : request.getPropertyNames()) {
            requestProperties.put(name, request.getProperty(name));
        }

        // get highest ranked host for next request
        Host host = smartConfig.getLoadBalancer().getTopHost(requestProperties);

        // replace the host in the request
        URI uri = request.getUri();
        try {
            org.apache.http.HttpHost httpHost = new org.apache.http.HttpHost(host.getName(), uri.getPort(), uri.getScheme());
            // NOTE: flags were added in httpclient 4.5.8 to allow for no normalization (which matches behavior prior to 4.5.7)
            uri = org.apache.http.client.utils.URIUtils.rewriteURI(uri, httpHost, org.apache.http.client.utils.URIUtils.NO_FLAGS);
        } catch (URISyntaxException e) {
            throw new RuntimeException("load-balanced host generated invalid URI", e);
        }
        request.setUri(uri);

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
            // capture requests stats (error)
            boolean isServerError = e instanceof SmartClientException && ((SmartClientException) e).isServerError();
            host.callComplete(isServerError);
            host.connectionClosed();

            throw e;
        }
    }

    @Override
    public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
        return delegate.apply(request, callback);
    }

    @Override
    public String getName() {
        return "SmartFilter(" + delegate.getName() + ")";
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * A ConnectorProvider that wraps another provider with SmartFilter load balancing.
     */
    public static class SmartConnectorProvider implements ConnectorProvider {
        private final SmartConfig smartConfig;
        private final ConnectorProvider delegateProvider;

        public SmartConnectorProvider(SmartConfig smartConfig, ConnectorProvider delegateProvider) {
            this.smartConfig = smartConfig;
            this.delegateProvider = delegateProvider;
        }

        @Override
        public Connector getConnector(Client client, Configuration runtimeConfig) {
            return new SmartFilter(smartConfig, delegateProvider.getConnector(client, runtimeConfig));
        }
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
