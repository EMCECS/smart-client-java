package com.emc.rest.smart;

import java.util.Date;

public interface HostStats {
    long getTotalConnections();

    long getTotalErrors();

    int getOpenConnections();

    Date getLastConnectionTime();

    long getResponseQueueAverage();
}
