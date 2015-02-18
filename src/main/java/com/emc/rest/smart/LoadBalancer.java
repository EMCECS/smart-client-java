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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LoadBalancer {
    private final Queue<Host> hosts = new ConcurrentLinkedQueue<Host>();

    public LoadBalancer(List<String> initialHosts) {

        // seed the host map
        for (String host : initialHosts) {
            hosts.add(new Host(host));
        }
    }

    /**
     * Returns the host with the lowest response index.
     */
    public Host getTopHost() {
        Host topHost = null;

        long lowestIndex = Long.MAX_VALUE;

        synchronized (hosts) {
            for (Host host : hosts) {

                // get response index for a host
                long hostIndex = host.getResponseIndex();

                // remember the host with the lowest index
                if (hostIndex < lowestIndex) {
                    topHost = host;
                    lowestIndex = hostIndex;
                }
            }

            // move the top host to the end of the host list as an extra tie-breaker
            hosts.remove(topHost);
            hosts.add(topHost);
        }

        return topHost;
    }

    /**
     * Returns stats for all active hosts in this load balancer
     */
    public HostStats[] getHostStats() {
        return hosts.toArray(new HostStats[hosts.size()]);
    }

    protected void updateHosts(List<String> updatedHosts) throws Exception {
        // don't modify the parameter
        List<String> hostList = new ArrayList<String>(updatedHosts);

        synchronized (hosts) {
            // remove hosts from stored list that are not present in updated list
            // remove hosts in updated list that are already present in stored list
            Iterator<Host> hostI = hosts.iterator();
            while (hostI.hasNext()) {
                Host host = hostI.next();
                boolean stillThere = false;
                Iterator<String> hostListI = hostList.iterator();
                while (hostListI.hasNext()) {
                    String hostFromUpdate = hostListI.next();
                    if (host.getName().equalsIgnoreCase(hostFromUpdate)) {

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
            for (String newHost : hostList) {
                hosts.add(new Host(newHost));
            }
        }
    }
}
