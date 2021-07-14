/*
 * Copyright (c) 2015, EMC Corporation.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     + Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     + Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     + The name of EMC Corporation may not be used to endorse or promote
 *       products derived from this software without specific prior written
 *       permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.emc.rest.smart;

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

    private SmartConfig smartConfig;

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
            if (response.getStatus() >= 500 && response.getStatus() != 501) {
                // except for 501 (not implemented), all 50x responses are considered server errors
                host.callComplete(true);
            } else {
                host.callComplete(false);
            }

            // wrap the input stream so we can capture the actual connection close
            response.setEntityInputStream(new WrappedInputStream(response.getEntityInputStream(), host));

            return response;
        } catch (RuntimeException e) {

            // capture requests stats (error)
            host.callComplete(true);
            host.connectionClosed();

            throw e;
        }
    }

    /**
     * captures closure in host statistics
     */
    protected class WrappedInputStream extends FilterInputStream {
        private Host host;
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
