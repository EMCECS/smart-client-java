package com.emc.rest.smart;

import java.util.List;

public interface HostListProvider {
    public abstract List<String> getHostList();
}
