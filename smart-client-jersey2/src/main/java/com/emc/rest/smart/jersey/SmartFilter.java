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
import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIUtils;
import org.glassfish.jersey.client.InjectionManagerClientProvider;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.Providers;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class SmartFilter implements ClientRequestFilter {
    public static final String BYPASS_LOAD_BALANCER = "com.emc.rest.smart.bypassLoadBalancer";
    private final SmartConfig smartConfig;

    public SmartFilter(SmartConfig smartConfig) {
        this.smartConfig = smartConfig;
    }

    @Override
    public void filter(ClientRequestContext context) throws IOException {
        final InjectionManager injectionManager = InjectionManagerClientProvider.getInjectionManager(context);
        // check for bypass flag
        Boolean bypass = (Boolean) context.getProperty(BYPASS_LOAD_BALANCER);
        Iterable<ClientRequestFilter> requestFilters = Providers.getAllProviders(injectionManager, ClientRequestFilter.class);
        if (bypass != null && bypass) {
            if (requestFilters.iterator().hasNext())
                requestFilters.iterator().next().filter(context);
        }

        // get highest ranked host for next request
        Host host = smartConfig.getLoadBalancer().getTopHost(context.getConfiguration().getProperties());

        // replace the host in the request
        URI uri = context.getUri();
        try {
            HttpHost httpHost = new HttpHost(host.getName(), uri.getPort(), uri.getScheme());
            // NOTE: flags were added in httpclient 4.5.8 to allow for no normalization (which matches behavior prior to 4.5.7)
            uri = URIUtils.rewriteURI(uri, httpHost, URIUtils.NO_FLAGS);
        } catch (URISyntaxException e) {
            throw new RuntimeException("load-balanced host generated invalid URI", e);
        }
        context.setUri(uri);

        // track requests stats for LB ranking
        host.connectionOpened(); // not really, but we can't (cleanly) intercept any lower than this
        try {
            requestFilters.iterator().next().filter(context);
            // capture request stats
            // except for 501 (not implemented), all 50x responses are considered server errors

            //TODO: wrap response entity
//            host.callComplete(response.getStatus() >= 500 && response.getStatus() != 501);
//
//            // wrap the input stream so we can capture the actual connection close
//            context.setEntityStream(new WrappedInputStream(response.getEntityInputStream(), host));

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
