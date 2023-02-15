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
import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIUtils;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.ext.Provider;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

@Provider
public class SmartFilter implements ClientRequestFilter, ClientResponseFilter {
    public static final String BYPASS_LOAD_BALANCER = "com.emc.rest.smart.bypassLoadBalancer";
    public static final int PRIORITY_SMART = 1400; // the value is decided by filters' order

    private final SmartConfig smartConfig;
    private Host host;

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
        host = smartConfig.getLoadBalancer().getTopHost(requestContext.getConfiguration().getProperties());

        // replace the host in the request
        URI uri = requestContext.getUri();
        try {
            HttpHost httpHost = new HttpHost(host.getName(), uri.getPort(), uri.getScheme());
            // NOTE: flags were added in httpclient 4.5.8 to allow for no normalization (which matches behavior prior to 4.5.7)
            uri = URIUtils.rewriteURI(uri, httpHost, URIUtils.NO_FLAGS);
        } catch (URISyntaxException e) {
            throw new RuntimeException("load-balanced host generated invalid URI", e);
        }
        requestContext.setUri(uri);

        // track requests stats for LB ranking
        host.connectionOpened(); // not really, but we can't (cleanly) intercept any lower than this

    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        try {

            // capture request stats
            // except for 501 (not implemented), all 50x responses are considered server errors
            host.callComplete(responseContext.getStatus() >= 500 && responseContext.getStatus() != 501);
//
//            // wrap the input stream so we can capture the actual connection close
            responseContext.setEntityStream(new WrappedInputStream(responseContext.getEntityStream(), host));

        } catch (RuntimeException e) {
            // capture requests stats (error)
            boolean isServerError = e instanceof SmartClientException && ((SmartClientException) e).isServerError();
            host.callComplete(isServerError);
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
