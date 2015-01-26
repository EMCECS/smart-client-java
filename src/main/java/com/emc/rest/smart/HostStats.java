package com.emc.rest.smart;

import java.util.Date;

public interface HostStats {
    @SuppressWarnings("unused")
    long getTotalConnections();

    @SuppressWarnings("unused")
    long getTotalErrors();

    @SuppressWarnings("unused")
    int getOpenConnections();

    @SuppressWarnings("unused")
    Date getLastConnectionTime();

    @SuppressWarnings("unused")
    long getResponseQueueAverage();
}
