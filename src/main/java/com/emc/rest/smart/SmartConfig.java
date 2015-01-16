/*
 * Copyright 2014 EMC Corporation. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.emc.rest.smart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Houses configuration for the smart client.
 */
public class SmartConfig {
    public static final int DEFAULT_POLL_INTERVAL = 120; // seconds

    private List<String> initialHosts;
    private LoadBalancer loadBalancer;
    private HostListProvider hostListProvider;
    private int pollInterval = DEFAULT_POLL_INTERVAL;
    private boolean disablePolling = false;

    private Map<String, Object> clientProperties = new HashMap<>();

    public SmartConfig(List<String> initialHosts) {
        this.initialHosts = initialHosts;
        this.loadBalancer = new LoadBalancer(initialHosts);
    }

    public List<String> getInitialHosts() {
        return initialHosts;
    }

    /**
     * Set the initial list of data services nodes in the ViPR cluster. These nodes will be queried at regular intervals
     * to get the full current list of active nodes.
     */
    public void setInitialHosts(List<String> initialHosts) {
        this.initialHosts = initialHosts;
    }

    public synchronized LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public synchronized void setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
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
     * Set the interval in seconds to wait between queries for active nodes. Defaults to 120 seconds (2 minutes).
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

    public Map<String, Object> getClientProperties() {
        return clientProperties;
    }

    /**
     * Allows custom Jersey client properties to be set. These will be passed on in the Jersey ClientConfig
     */
    public void setClientProperties(Map<String, Object> clientProperties) {
        this.clientProperties = clientProperties;
    }
}
