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

import java.util.Date;
import java.util.Objects;

/**
 * Some basic statements about response index calculation:
 * <p>
 *     <ul>
 *         <li>lower response index means the host is more likely to be used</li>
 *         <li>should be based primarily on number of open connections to the host</li>
 *         <li>an error will mark the host as unhealthy for <code>errorWaitTime</code> milliseconds</li>
 *         <li>multiple consecutive errors compound the unhealthy (cool down) period up to 16x the errorWaitTime</li>
 *     </ul>
 */
public class Host implements HostStats {
    private static final Logger log = LoggerFactory.getLogger(Host.class);

    public static final int DEFAULT_ERROR_WAIT_MS = 1500;
    public static final int LOG_DELAY = 60000; // 1 minute
    public static final int MAX_COOL_DOWN_EXP = 4;

    private final String name;
    private int port;
    private boolean healthy = true;
    protected int errorWaitTime = DEFAULT_ERROR_WAIT_MS;
    private String logName;

    protected int openConnections;
    protected long lastConnectionTime;
    protected long totalConnections;
    protected long totalErrors;
    protected long consecutiveErrors;
    protected long lastLogTime;

    /**
     * @param name the host name or IP address of this host
     */
    public Host(String name) {
        this(name, -1);
    }

    /**
     * @param name the host name or IP address of this host
     * @param port the port of the service running on this host
     */
    public Host(String name, int port) {
        if (name == null) throw new NullPointerException();
        this.name = name;
        this.port = port;
        if (port < 0) this.logName = name;
        else this.logName = name + ":" + port;
    }

    public synchronized void connectionOpened() {
        openConnections++;
        totalConnections++;
        lastConnectionTime = System.currentTimeMillis();
    }

    public synchronized void connectionClosed() {
        openConnections--;

        // Just in case our stats get out of whack somehow, make sure people know about it
        if (openConnections < 0) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLogTime > LOG_DELAY) {
                log.warn("openConnections for host {} is {} !", this, openConnections);
                lastLogTime = currentTime;
            }
        }
    }

    public synchronized void callComplete(boolean isError) {
        if (isError) {
            totalErrors++;
            consecutiveErrors++;
            log.debug("error tallied for {}; total errors: {}, consecutive errors: {}",
                    logName, totalErrors, consecutiveErrors);
        } else {
            consecutiveErrors = 0;
        }
    }

    public String getName() {
        return name;
    }

    public int getPort() {
        return port;
    }

    public boolean isHealthy() {
        if (!healthy) return false;
        else if (consecutiveErrors == 0) return true;
        else {
            // errorWaitTime * 2 ^ (min(errors-1, 4))
            // i.e. back-off is doubled for each consecutive error up to 4
            long coolDownExp = Math.min(consecutiveErrors - 1, MAX_COOL_DOWN_EXP);
            long msSinceLastUse = System.currentTimeMillis() - lastConnectionTime;
            long errorCoolDown = (long) Math.pow(2, coolDownExp) * errorWaitTime;
            return msSinceLastUse > errorCoolDown;
        }
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public long getResponseIndex() {
        return openConnections;
    }

    /**
     * Resets historical metrics. Use with care!
     */
    public synchronized void resetStats() {
        totalConnections = openConnections;
        totalErrors = 0;
        consecutiveErrors = 0;
    }

    @Override
    public long getTotalConnections() {
        return totalConnections;
    }

    @Override
    public long getTotalErrors() {
        return totalErrors;
    }

    @Override
    public int getOpenConnections() {
        return openConnections;
    }

    @Override
    public Date getLastConnectionTime() {
        return new Date(lastConnectionTime);
    }

    public long getConsecutiveErrors() {
        return consecutiveErrors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Host host = (Host) o;
        return port == host.port && name.equals(host.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, port);
    }

    @Override
    public String toString() {
        return String.format("%s{totalConnections=%d, totalErrors=%d, openConnections=%d, lastConnectionTime=%s}",
                logName, totalConnections, totalErrors, openConnections, new Date(lastConnectionTime));
    }

    public int getErrorWaitTime() {
        return errorWaitTime;
    }

    /**
     * Sets the number of milliseconds that should pass after an error has occurred before anyone should use this host.
     * This time is compounded exponentially (* 2 ^ n) for consecutive errors.
     */
    public void setErrorWaitTime(int errorWaitTime) {
        this.errorWaitTime = errorWaitTime;
    }

    public Host withErrorWaitTime(int errorWaitTime) {
        setErrorWaitTime(errorWaitTime);
        return this;
    }
}
