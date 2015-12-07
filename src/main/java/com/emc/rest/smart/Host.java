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

import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;

import java.util.Date;

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
    private static final Logger l4j = Logger.getLogger(Host.class);

    public static final int DEFAULT_ERROR_WAIT_MS = 1500;
    public static final int LOG_DELAY = 60000; // 1 minute
    public static final int MAX_COOL_DOWN_EXP = 4;

    private String name;
    private boolean healthy = true;
    protected int errorWaitTime = DEFAULT_ERROR_WAIT_MS;

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
        this.name = name;
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
                LogMF.warn(l4j, "openConnections for host %s is %d !", this, openConnections);
                lastLogTime = currentTime;
            }
        }
    }

    public synchronized void callComplete(boolean isError) {
        if (isError) {
            totalErrors++;
            consecutiveErrors++;
            LogMF.debug(l4j, "error tallied for {2}; total errors: {0}, consecutive errors: {1}",
                    totalErrors, consecutiveErrors, name);
        } else {
            consecutiveErrors = 0;
        }
    }

    public String getName() {
        return name;
    }

    public boolean isHealthy() {
        if (!healthy) return false;
        else if (consecutiveErrors == 0) return true;
        else {
            long coolDownExp = consecutiveErrors > MAX_COOL_DOWN_EXP ? MAX_COOL_DOWN_EXP : consecutiveErrors - 1;
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
        if (!(o instanceof Host)) return false;

        Host host = (Host) o;

        return getName().equals(host.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s{totalConnections=%d, totalErrors=%d, openConnections=%d, lastConnectionTime=%s}",
                name, totalConnections, totalErrors, openConnections, new Date(lastConnectionTime).toString());
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
