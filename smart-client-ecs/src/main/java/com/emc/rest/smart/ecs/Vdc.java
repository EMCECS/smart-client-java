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
package com.emc.rest.smart.ecs;

import com.emc.rest.smart.Host;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Vdc implements Iterable<VdcHost> {
    private String name;
    private final List<VdcHost> hosts;

    public Vdc(String... hostNames) {
        this.name = hostNames[0];
        hosts = new ArrayList<>();
        for (String hostName : hostNames) {
            hosts.add(new VdcHost(this, hostName));
        }
    }

    public Vdc(List<? extends Host> hosts) {
        this(hosts.get(0).getName(), hosts);
    }

    public Vdc(String name, List<? extends Host> hosts) {
        this.name = name;
        this.hosts = createVdcHosts(hosts);
    }

    @Override
    public Iterator<VdcHost> iterator() {
        return hosts.iterator();
    }

    public boolean isHealthy() {
        for (Host host : hosts) {
            if (!host.isHealthy()) return false;
        }
        return true;
    }

    protected List<VdcHost> createVdcHosts(List<? extends Host> hosts) {
        List<VdcHost> vdcHosts = new ArrayList<>();
        for (Host host : hosts) {
            vdcHosts.add(new VdcHost(this, host.getName()));
        }
        return vdcHosts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vdc)) return false;

        Vdc vdc = (Vdc) o;

        return getName().equals(vdc.getName());

    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        String hostsString = "";
        if (hosts != null) {
            for (Host host : hosts) {
                if (hostsString.length() > 0) hostsString += ",";
                hostsString += host.getName();
            }
        }
        return name + '(' + hostsString + ')';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<VdcHost> getHosts() {
        return hosts;
    }

    public Vdc withName(String name) {
        setName(name);
        return this;
    }
}
