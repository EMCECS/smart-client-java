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
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class SmartFilter extends ClientFilter {
    public static final String BYPASS_LOAD_BALANCER = "com.emc.rest.smart.bypassLoadBalancer";

    private final SmartConfig smartConfig;

    public SmartFilter(SmartConfig smartConfig) {
        this.smartConfig = smartConfig;
    }

    @Override
    public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
        // check for bypass flag
        Boolean bypass = (Boolean) request.getProperties().get(BYPASS_LOAD_BALANCER);
        if (bypass != null && bypass) {
            return getNext().handle(request);
        }

        // get highest ranked host for next request
        Host host = smartConfig.getLoadBalancer().getTopHost(request.getProperties());

        // replace the host in the request
        URI uri = request.getURI();
        try {
            org.apache.http.HttpHost httpHost = new org.apache.http.HttpHost(host.getName(), uri.getPort(), uri.getScheme());
            // NOTE: flags were added in httpclient 4.5.8 to allow for no normalization (which matches behavior prior to 4.5.7)
            uri = org.apache.http.client.utils.URIUtils.rewriteURI(uri, httpHost, org.apache.http.client.utils.URIUtils.NO_FLAGS);
        } catch (URISyntaxException e) {
            throw new RuntimeException("load-balanced host generated invalid URI", e);
        }
        request.setURI(uri);

        // track requests stats for LB ranking
        host.connectionOpened(); // not really, but we can't (cleanly) intercept any lower than this
        try {
            // call to delegate
            ClientResponse response = getNext().handle(request);

            // capture request stats
            // except for 501 (not implemented), all 50x responses are considered server errors
            host.callComplete(response.getStatus() >= 500 && response.getStatus() != 501);

            // wrap the input stream so we can capture the actual connection close
            response.setEntityInputStream(new WrappedInputStream(response.getEntityInputStream(), host));

            return response;
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
