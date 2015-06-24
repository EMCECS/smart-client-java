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

import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;

/**
 * Some basic statements about response index calculation:
 * <p/>
 * - lower response index means the host is more likely to be used
 * - should be based primarily on average response time
 * - an error bumps up the response index based on the error cool down time
 * - multiple consecutive errors compounds the bump in response index (takes longer to cool down)
 * - open connections bump up the index, but not as much as other factors
 * - if a host has not been used in a while, that bumps down the index gradually the longer it has not been used (cool down)
 */
public class Host implements HostStats {
    private static final Logger l4j = Logger.getLogger(Host.class);

    public static final int DEFAULT_RESPONSE_WINDOW_SIZE = 25;
    public static final int DEFAULT_ERROR_COOL_DOWN_SECS = 10;

    private String name;
    private boolean healthy = true;
    protected int responseWindowSize = DEFAULT_RESPONSE_WINDOW_SIZE;
    protected int errorCoolDownSecs = DEFAULT_ERROR_COOL_DOWN_SECS;

    protected int openConnections;
    protected long lastConnectionTime;
    protected long totalConnections;
    protected long totalErrors;
    protected long consecutiveErrors;
    protected long responseQueueAverage;

    protected Queue<Long> responseQueue = new ArrayDeque<Long>();

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
    }

    public synchronized void callComplete(long duration, boolean isError) {
        if (isError) {
            totalErrors++;
            consecutiveErrors++;
            LogMF.debug(l4j, "error tallied for {2}; total errors: {0}, consecutive errors: {1}",
                    totalErrors, consecutiveErrors, name);
        } else {
            consecutiveErrors = 0;
        }

        // log response time
        responseQueue.add(duration);
        while (responseQueue.size() > responseWindowSize)
            responseQueue.remove();

        // recalculate average
        long responseTotal = 0;
        for (long response : responseQueue) {
            responseTotal += response;
        }
        responseQueueAverage = responseTotal / responseQueue.size();

        LogMF.debug(l4j, "call complete for {3}; duration: {0}, queue size: {1}, new average: {2}",
                duration, responseQueue.size(), responseQueueAverage, name);
    }

    public String getName() {
        return name;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public long getResponseIndex() {
        long currentTime = System.currentTimeMillis();

        synchronized (this) {
            // error adjustment adjust the index up based on the number of consecutive errors
            long errorAdjustment = consecutiveErrors * errorCoolDownSecs * 1000;

            // open connection adjustment adjusts the index up based on the number of open connections to the host
            long openConnectionAdjustment = openConnections * errorCoolDownSecs; // cool down secs as ms instead

            // dormant adjustment adjusts the index down based on how long it's been since the host was last used
            long msSinceLastUse = currentTime - lastConnectionTime;

            return responseQueueAverage + errorAdjustment + openConnectionAdjustment - msSinceLastUse;
        }
    }

    /**
     * Resets historical metrics. Use with care!
     */
    public synchronized void resetStats() {
        totalConnections = openConnections;
        totalErrors = 0;
        consecutiveErrors = 0;
        responseQueueAverage = 0;
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

    @Override
    public long getResponseQueueAverage() {
        return responseQueueAverage;
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
        return String.format("%s{totalConnections=%d, totalErrors=%d, openConnections=%d, lastConnectionTime=%s, responseQueueAverage=%d}",
                name, totalConnections, totalErrors, openConnections, new Date(lastConnectionTime).toString(), responseQueueAverage);
    }

    public int getResponseWindowSize() {
        return responseWindowSize;
    }

    public void setResponseWindowSize(int responseWindowSize) {
        this.responseWindowSize = responseWindowSize;
    }

    public int getErrorCoolDownSecs() {
        return errorCoolDownSecs;
    }

    public void setErrorCoolDownSecs(int errorCoolDownSecs) {
        this.errorCoolDownSecs = errorCoolDownSecs;
    }

    public Host withResponseWindowSize(int responseWindowSize) {
        setResponseWindowSize(responseWindowSize);
        return this;
    }

    public Host withErrorCoolDownSecs(int errorCoolDownSecs) {
        setErrorCoolDownSecs(errorCoolDownSecs);
        return this;
    }
}
