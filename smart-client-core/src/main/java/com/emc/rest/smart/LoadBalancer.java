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

import java.util.*;

public class LoadBalancer {
    private final Deque<Host> hosts = new ArrayDeque<>();
    private List<HostVetoRule> vetoRules;

    public LoadBalancer(List<Host> initialHosts) {

        // seed the host map
        hosts.addAll(initialHosts);
    }

    /**
     * Returns the host with the lowest response index.
     */
    public Host getTopHost(Map<String, Object> requestProperties) {
        Host topHost = null, topHealthyHost = null;

        long lowestIndex = Long.MAX_VALUE, lowestHealthyIndex = Long.MAX_VALUE;

        synchronized (hosts) {
            for (Host host : hosts) {

                // apply any veto rules
                if (shouldVeto(host, requestProperties)) continue;

                // get response index for a host
                long hostIndex = host.getResponseIndex();

                // remember the host with the lowest index
                if (hostIndex < lowestIndex) {
                    topHost = host;
                    lowestIndex = hostIndex;
                }

                // also keep track of the top *healthy* host
                if (host.isHealthy() && hostIndex < lowestHealthyIndex) {
                    topHealthyHost = host;
                    lowestHealthyIndex = hostIndex;
                }
            }

            // if there are no healthy hosts, we still need a host to contact
            if (topHealthyHost != null) topHost = topHealthyHost;

            // move the top host to the end of the host list as an extra tie-breaker
            hosts.remove(topHost);
            hosts.add(topHost);
        }

        return topHost;
    }

    protected boolean shouldVeto(Host host, Map<String, Object> requestProperties) {
        if (vetoRules != null) {
            for (HostVetoRule vetoRule : vetoRules) {
                if (vetoRule.shouldVeto(host, requestProperties)) return true;
            }
        }
        return false;
    }

    /**
     * Returns a list of all known hosts. This list is a clone; modification will not affect the load balancer
     */
    public synchronized List<Host> getAllHosts() {
        return new ArrayList<>(hosts);
    }

    /**
     * Returns stats for all active hosts in this load balancer
     */
    public synchronized HostStats[] getHostStats() {
        return hosts.toArray(new HostStats[0]);
    }

    /**
     * Resets connection metrics. Use with care!
     */
    public void resetStats() {
        for (Host host : getAllHosts()) {
            host.resetStats();
        }
    }

    public long getTotalConnections() {
        long totalConnections = 0;
        for (Host host : getAllHosts()) {
            totalConnections += host.getTotalConnections();
        }
        return totalConnections;
    }

    public long getTotalErrors() {
        long totalErrors = 0;
        for (Host host : getAllHosts()) {
            totalErrors += host.getTotalErrors();
        }
        return totalErrors;
    }

    public long getOpenConnections() {
        long openConnections = 0;
        for (Host host : getAllHosts()) {
            openConnections += host.getOpenConnections();
        }
        return openConnections;
    }

    /**
     * Ensure this method is called sparingly as it will block getTopHost() calls, pausing all new connections!
     */
    protected void updateHosts(List<Host> updatedHosts) {
        // don't modify the parameter
        List<Host> hostList = new ArrayList<>(updatedHosts);

        // remove hosts from stored list that are not present in updated list
        // remove hosts in updated list that are already present in stored list
        synchronized (hosts) {
            Iterator<Host> hostI = hosts.iterator();
            while (hostI.hasNext()) {
                Host host = hostI.next();
                boolean stillThere = false;
                Iterator<Host> hostListI = hostList.iterator();
                while (hostListI.hasNext()) {
                    Host hostFromUpdate = hostListI.next();
                    if (host.equals(hostFromUpdate)) {

                        // this host is in both the stored list and the updated list
                        stillThere = true;
                        hostListI.remove();
                        break;
                    }
                }

                // this host doesn't appear in the updated list, so remove it
                if (!stillThere) hostI.remove();
            }

            // what's left in the updated list are new hosts, so add them
            hosts.addAll(hostList);
        }
    }

    public List<HostVetoRule> getVetoRules() {
        return vetoRules;
    }

    public void setVetoRules(List<HostVetoRule> vetoRules) {
        this.vetoRules = vetoRules;
    }

    public LoadBalancer withVetoRules(HostVetoRule... vetoRules) {
        setVetoRules(Arrays.asList(vetoRules));
        return this;
    }
}
