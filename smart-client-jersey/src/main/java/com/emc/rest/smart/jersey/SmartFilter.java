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
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.Response;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class SmartFilter implements ClientRequestFilter, ClientResponseFilter {
    public static final String BYPASS_LOAD_BALANCER = "com.emc.rest.smart.bypassLoadBalancer";

    private final SmartConfig smartConfig;

    public SmartFilter(SmartConfig smartConfig) {
        this.smartConfig = smartConfig;
    }

    private static final ThreadLocal<Host> currentHost = new ThreadLocal<>();

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        // check for bypass flag
        Boolean bypass = (Boolean) requestContext.getProperty(BYPASS_LOAD_BALANCER);
        if (bypass != null && bypass) {
            return;
        }

        // get highest ranked host for next request - pass null since we can't convert context to Map
        Host host = smartConfig.getLoadBalancer().getTopHost(null);
        currentHost.set(host);

        // replace the host in the request
        URI uri = requestContext.getUri();
        try {
            org.apache.http.HttpHost httpHost = new org.apache.http.HttpHost(host.getName(), uri.getPort(), uri.getScheme());
            // NOTE: flags were added in httpclient 4.5.8 to allow for no normalization (which matches behavior prior to 4.5.7)
            uri = org.apache.http.client.utils.URIUtils.rewriteURI(uri, httpHost, org.apache.http.client.utils.URIUtils.NO_FLAGS);
        } catch (URISyntaxException e) {
            throw new RuntimeException("load-balanced host generated invalid URI", e);
        }
        requestContext.setUri(uri);

        // track requests stats for LB ranking
        host.connectionOpened(); // not really, but we can't (cleanly) intercept any lower than this
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        Host host = currentHost.get();
        if (host == null) return;

        try {
            // capture request stats
            // except for 501 (not implemented), all 50x responses are considered server errors
            int status = responseContext.getStatus();
            host.callComplete(status >= 500 && status != 501);

            // wrap the input stream so we can capture the actual connection close
            if (responseContext.hasEntity()) {
                responseContext.setEntityStream(new WrappedInputStream(responseContext.getEntityStream(), host));
            } else {
                host.connectionClosed();
            }
        } finally {
            currentHost.remove();
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
