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
package com.emc.rest.smart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Houses configuration for the smart client.
 */
public class SmartConfig {

    private static final Logger log = LoggerFactory.getLogger(SmartConfig.class);

    public static final int DEFAULT_POLL_INTERVAL = 120; // seconds

    private URI proxyUri;
    private String proxyUser;
    private String proxyPass;

    private final LoadBalancer loadBalancer;
    private HostListProvider hostListProvider;
    private int pollInterval = DEFAULT_POLL_INTERVAL;
    private boolean hostUpdateEnabled = true;
    private boolean healthCheckEnabled = true;
    private int maxConnectionIdleTime = 0;

    private final Map<String, Object> properties = new HashMap<>();

    /**
     * @see #SmartConfig(LoadBalancer)
     */
    public SmartConfig(String... initialHostNames) {
        List<Host> hostList = new ArrayList<>();
        for (String hostName : initialHostNames) {
            hostList.add(new Host(hostName));
        }
        this.loadBalancer = new LoadBalancer(hostList);
    }

    /**
     * Constructs a new SmartConfig using a new {@link LoadBalancer} seeded with the specified hosts
     */
    public SmartConfig(List<Host> initialHosts) {
        this(new LoadBalancer(initialHosts));
    }

    public SmartConfig(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
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

    public boolean isHostUpdateEnabled() {
        return hostUpdateEnabled;
    }

    public void setHostUpdateEnabled(boolean hostUpdateEnabled) {
        this.hostUpdateEnabled = hostUpdateEnabled;
    }

    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    public void setHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    public int getMaxConnectionIdleTime() {
        return maxConnectionIdleTime;
    }

    public void setMaxConnectionIdleTime(int maxConnectionIdleTime) {
        this.maxConnectionIdleTime = maxConnectionIdleTime;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Object getProperty(String propName) {
        return properties.get(propName);
    }

    public Integer getIntProperty(String propName, int defaultValue) {
        int ret;
        String pValue = (String)properties.get(propName);
        try {
            ret = Integer.parseInt(pValue);
        } catch (Throwable t) {
            log.debug("cannot parse the config value for " + propName, t);
            return defaultValue;
        }
        return ret;
    }

    /**
     * Allows custom Jersey client properties to be set. These will be passed on in the Jersey ClientConfig
     */
    public void setProperty(String propName, Object value) {
        properties.put(propName, value);
    }

    public SmartConfig withProxyUri(URI proxyUri) {
        setProxyUri(proxyUri);
        return this;
    }

    public SmartConfig withProxyUser(String proxyUser) {
        setProxyUser(proxyUser);
        return this;
    }

    public SmartConfig withProxyPass(String proxyPass) {
        setProxyPass(proxyPass);
        return this;
    }

    public SmartConfig withHostListProvider(HostListProvider hostListProvider) {
        setHostListProvider(hostListProvider);
        return this;
    }

    public SmartConfig withPollInterval(int pollInterval) {
        setPollInterval(pollInterval);
        return this;
    }

    public SmartConfig withHostUpdateEnabled(boolean hostUpdateEnabled) {
        setHostUpdateEnabled(hostUpdateEnabled);
        return this;
    }

    public SmartConfig withHealthCheckEnabled(boolean healthCheckEnabled) {
        setHealthCheckEnabled(healthCheckEnabled);
        return this;
    }

    public SmartConfig withMaxConnectionIdleTime(int maxConnectionIdleTime) {
        setMaxConnectionIdleTime(maxConnectionIdleTime);
        return this;
    }

    public SmartConfig withProperty(String propName, Object value) {
        setProperty(propName, value);
        return this;
    }

}
