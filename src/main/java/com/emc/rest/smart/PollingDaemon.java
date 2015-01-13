package com.emc.rest.smart;

import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;

/**
 * Polling thread that will terminate automatically when the application exits
 */
public class PollingDaemon extends Thread {
    private static final Logger l4j = Logger.getLogger(PollingDaemon.class);

    private SmartConfig smartConfig;
    private boolean running = true;

    public PollingDaemon(SmartConfig smartConfig) {
        this.smartConfig = smartConfig;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (running) {
            long start = System.currentTimeMillis();
            l4j.debug("polling daemon running");

            LoadBalancer loadBalancer = smartConfig.getLoadBalancer();
            HostListProvider hostListProvider = smartConfig.getHostListProvider();

            if (smartConfig.isDisablePolling()) {
                l4j.info("host polling is disabled; not updating hosts");
            } else if (hostListProvider == null) {
                l4j.info("no host list provider; not updating hosts");
            } else {
                try {
                    loadBalancer.updateHosts(hostListProvider.getHostList());

                    // TODO: detect non-ViPR system and disable polling
                } catch (Throwable t) {
                    l4j.warn("Unable to enumerate servers", t);
                }
            }

            long callTime = System.currentTimeMillis() - start;
            try {
                LogMF.debug(l4j, "polling daemon finished; sleeping for {0} seconds..", smartConfig.getPollInterval());
                Thread.sleep(smartConfig.getPollInterval() * 1000 - callTime);
            } catch (InterruptedException e) {
                l4j.warn("Interrupted while sleeping");
            }
        }
    }

    public void terminate() {
        running = false;
    }
}
