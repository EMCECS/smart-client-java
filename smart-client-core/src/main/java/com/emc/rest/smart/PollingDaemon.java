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

/**
 * Polling thread that will terminate automatically when the application exits
 */
public class PollingDaemon extends Thread {
    public static final String PROPERTY_KEY = "com.emc.rest.smart.pollingDaemon";

    private static final Logger log = LoggerFactory.getLogger(PollingDaemon.class);

    private final SmartConfig smartConfig;
    private boolean running = true;

    public PollingDaemon(SmartConfig smartConfig) {
        this.smartConfig = smartConfig;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (running) {
            long start = System.currentTimeMillis();
            log.debug("polling daemon running");

            LoadBalancer loadBalancer = smartConfig.getLoadBalancer();
            HostListProvider hostListProvider = smartConfig.getHostListProvider();

            if (!smartConfig.isHostUpdateEnabled()) {
                log.info("host update is disabled; not updating hosts");
            } else if (hostListProvider == null) {
                log.info("no host list provider; not updating hosts");
            } else {
                try {
                    loadBalancer.updateHosts(hostListProvider.getHostList());
                } catch (Throwable t) {
                    log.warn("unable to enumerate servers", t);
                }
            }

            if (!smartConfig.isHealthCheckEnabled()) {
                log.info("health check is disabled; not checking hosts");
            } else if (hostListProvider == null) {
                log.info("no host list provider; not checking hosts");
            } else {
                for (Host host : loadBalancer.getAllHosts()) {
                    try {
                        hostListProvider.runHealthCheck(host);
                        host.setHealthy(true);
                        log.debug("health check successful for {}; host is marked healthy", host.getName());
                    } catch (Throwable t) {
                        host.setHealthy(false);
                        log.warn("health check failed for " + host.getName() + "; host is marked unhealthy", t);
                    }
                }
            }

            long callTime = System.currentTimeMillis() - start;
            try {
                long sleepTime = smartConfig.getPollInterval() * 1000L - callTime;
                if (sleepTime < 0) sleepTime = 0;
                log.debug("polling daemon finished; will poll again in {}ms..", sleepTime);
                if (sleepTime > 0) Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                log.warn("interrupted while sleeping", e);
            }
        }
    }

    public void terminate() {
        running = false;
    }

    public SmartConfig getSmartConfig() {
        return smartConfig;
    }

    public boolean isRunning() {
        return running;
    }
}
