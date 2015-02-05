/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.rest.smart;

import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.LinkedList;
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

    protected static final int DEFAULT_RESPONSE_WINDOW_SIZE = 20;
    protected static final int DEFAULT_ERROR_COOL_DOWN_SECS = 30;

    private String name;
    protected int responseWindowSize;
    protected int errorCoolDownSecs;

    protected int openConnections;
    protected long lastConnectionTime;
    protected long totalConnections;
    protected long totalErrors;
    protected long consecutiveErrors;
    protected long responseQueueAverage;

    protected Queue<Long> responseQueue = new LinkedList<>();

    /**
     * Uses a default error cool down of 30 secs and a response window size of 20.
     */
    public Host(String name) {
        this(name, DEFAULT_RESPONSE_WINDOW_SIZE, DEFAULT_ERROR_COOL_DOWN_SECS);
    }

    /**
     * @param name              the host name or IP address of this host
     * @param errorCoolDownSecs the cool down period for errors (number of seconds after an error when the host is
     *                          considered normalized). compounded for multiple consecutive errors
     */
    public Host(String name, int responseWindowSize, int errorCoolDownSecs) {
        this.name = name;
        this.responseWindowSize = responseWindowSize;
        this.errorCoolDownSecs = errorCoolDownSecs;
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
        if (responseQueue.size() > responseWindowSize)
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

    public synchronized long getResponseIndex() {
        // error adjustment adjust the index up based on the number of consecutive errors
        long errorAdjustment = consecutiveErrors * errorCoolDownSecs * 1000;

        // open connection adjustment adjusts the index up based on the number of open connections to the host
        long openConnectionAdjustment = openConnections * errorCoolDownSecs; // cool down secs as ms instead

        // dormant adjustment adjusts the index down based on how long it's been since the host was last used
        long msSinceLastUse = System.currentTimeMillis() - lastConnectionTime;

        return responseQueueAverage + errorAdjustment + openConnectionAdjustment - msSinceLastUse;
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

    @Override
    public String toString() {
        return String.format("%s{totalConnections=%d, totalErrors=%d, openConnections=%d, lastConnectionTime=%s, responseQueueAverage=%d}",
                name, totalConnections, totalErrors, openConnections, new Date(lastConnectionTime).toString(), responseQueueAverage);
    }
}
