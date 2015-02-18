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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Houses configuration for the smart client.
 */
public class SmartConfig {
    public static final int DEFAULT_POLL_INTERVAL = 120; // seconds

    private URI proxyUri;
    private String proxyUser;
    private String proxyPass;

    private List<String> initialHosts;
    private LoadBalancer loadBalancer;
    private HostListProvider hostListProvider;
    private int pollInterval = DEFAULT_POLL_INTERVAL;
    private boolean disablePolling = false;

    private Map<String, Object> properties = new HashMap<String, Object>();

    public SmartConfig(List<String> initialHosts) {
        this.initialHosts = initialHosts;
        this.loadBalancer = new LoadBalancer(initialHosts);
    }

    public List<String> getInitialHosts() {
        return initialHosts;
    }

    public synchronized LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public URI getProxyUri() {
        return proxyUri;
    }

    public void setProxyUri(URI proxyUri) {
        this.proxyUri = proxyUri;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public String getProxyPass() {
        return proxyPass;
    }

    public void setProxyPass(String proxyPass) {
        this.proxyPass = proxyPass;
    }

    public synchronized HostListProvider getHostListProvider() {
        return hostListProvider;
    }

    public synchronized void setHostListProvider(HostListProvider hostListProvider) {
        this.hostListProvider = hostListProvider;
    }

    public int getPollInterval() {
        return pollInterval;
    }

    /**
     * Set the interval in seconds to wait between polling for active nodes. Defaults to 120 seconds (2 minutes).
     */
    public void setPollInterval(int pollInterval) {
        this.pollInterval = pollInterval;
    }

    public boolean isDisablePolling() {
        return disablePolling;
    }

    public void setDisablePolling(boolean disablePolling) {
        this.disablePolling = disablePolling;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Object property(String propName) {
        return properties.get(propName);
    }

    /**
     * Allows custom Jersey client properties to be set. These will be passed on in the Jersey ClientConfig
     */
    public void property(String propName, Object value) {
        properties.put(propName, value);
    }
}
