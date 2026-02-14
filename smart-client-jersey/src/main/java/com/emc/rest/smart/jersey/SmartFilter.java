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
import com.emc.rest.smart.SmartConfig;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class SmartFilter implements ClientRequestFilter, ClientResponseFilter {
    public static final String BYPASS_LOAD_BALANCER = "com.emc.rest.smart.bypassLoadBalancer";
    private static final String HOST_PROPERTY = "com.emc.rest.smart.currentHost";

    private final SmartConfig smartConfig;

    public SmartFilter(SmartConfig smartConfig) {
        this.smartConfig = smartConfig;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        // check for bypass flag
        Boolean bypass = (Boolean) requestContext.getProperty(BYPASS_LOAD_BALANCER);
        if (bypass != null && bypass) {
            return;
        }

        // get highest ranked host for next request
        Host host = smartConfig.getLoadBalancer().getTopHost(null);

        // replace the host in the request
        URI uri = requestContext.getUri();
        try {
            URI newUri = new URI(uri.getScheme(), uri.getUserInfo(), host.getName(), 
                uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
            requestContext.setUri(newUri);
        } catch (URISyntaxException e) {
            throw new RuntimeException("load-balanced host generated invalid URI", e);
        }

        // track request stats for LB ranking
        host.connectionOpened();
        requestContext.setProperty(HOST_PROPERTY, host);
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        Host host = (Host) requestContext.getProperty(HOST_PROPERTY);
        if (host == null) {
            return;
        }

        try {
            // capture request stats
            int status = responseContext.getStatus();
            host.callComplete(status >= 500 && status != 501);

            // wrap the input stream to capture connection close
            if (responseContext.hasEntity()) {
                InputStream originalStream = responseContext.getEntityStream();
                if (originalStream != null) {
                    responseContext.setEntityStream(new WrappedInputStream(originalStream, host));
                } else {
                    host.connectionClosed();
                }
            } else {
                host.connectionClosed();
            }
        } catch (Exception e) {
            host.connectionClosed();
            throw e;
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
