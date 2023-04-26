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
import com.emc.rest.smart.HostListProvider;
import com.emc.rest.smart.LoadBalancer;
import org.apache.commons.codec.binary.Base64;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyInvocation;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class EcsHostListProvider implements HostListProvider {

    private static final Logger log = LoggerFactory.getLogger(EcsHostListProvider.class);

    public static final String DEFAULT_PROTOCOL = "https";
    public static final int DEFAULT_PORT = 9021;

    protected final SimpleDateFormat rfc822DateFormat;
    private final JerseyClient client;
    private final LoadBalancer loadBalancer;
    private final String user;
    private final String secret;
    private String protocol = DEFAULT_PROTOCOL;
    private int port = DEFAULT_PORT;
    private List<Vdc> vdcs;

    public EcsHostListProvider(JerseyClient client, LoadBalancer loadBalancer, String user, String secret) {
        this.client = client;
        this.loadBalancer = loadBalancer;
        this.user = user;
        this.secret = secret;
        rfc822DateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        rfc822DateFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));
    }

    public List<Host> getHostList() {
        if (vdcs == null || vdcs.isEmpty()) return getDataNodes(loadBalancer.getTopHost(null));

        List<Host> hostList = new ArrayList<>();

        for (Vdc vdc : vdcs) {
            if (vdc.getHosts().isEmpty()) log.warn("VDC " + vdc.getName() + " has no hosts!");

            boolean success = false;
            for (Host host : vdc) {
                if (!host.isHealthy()) { // the load balancer manages health checks
                    log.warn("not retrieving node list from " + host.getName() + " because it's unhealthy");
                    continue;
                }
                try {
                    updateVdcNodes(vdc, getDataNodes(host));
                    success = true;
                    break;
                } catch (Throwable t) {
                    log.warn("unable to retrieve node list from " + host.getName(), t);
                }
            }
            if (!success) log.warn("could not retrieve node list for VDC " + vdc.getName());

            hostList.addAll(vdc.getHosts());
        }

        return hostList;
    }

    @Override
    public void runHealthCheck(Host host) {
        // header is workaround for STORAGE-1833
        JerseyWebTarget webTarget = client.target(getRequestUri(host, "/?ping"));
        JerseyInvocation invocation = webTarget.request()
                .header("x-emc-namespace", "x")
                .header("Connection", "close") // make sure maintenance calls are not kept alive
                .buildGet();
        PingResponse response = invocation.invoke(PingResponse.class);

        if (host instanceof VdcHost) {
            PingItem.Status status = PingItem.Status.OFF;
            if (response != null && response.getPingItemMap() != null) {
                PingItem pingItem = response.getPingItemMap().get(PingItem.MAINTENANCE_MODE);
                if (pingItem != null) status = pingItem.getStatus();
            }
            ((VdcHost) host).setMaintenanceMode(status == PingItem.Status.ON);
        }
    }

    @Override
    public void destroy() {
        client.close();
    }

    protected List<Host> getDataNodes(Host host) {
        String path = "/?endpoint";
        URI uri = getRequestUri(host, path);

        // format date
        String rfcDate;
        synchronized (rfc822DateFormat) {
            rfcDate = rfc822DateFormat.format(new Date());
        }

        // generate signature
        String canonicalString = "GET\n\n\n" + rfcDate + "\n" + path;
        String signature;
        try {
            signature = getSignature(canonicalString, secret);
        } catch (Exception e) {
            throw new RuntimeException("could not generate signature", e);
        }

        // construct request
        JerseyWebTarget webTarget = client.target(uri);
        JerseyInvocation invocation = webTarget.request()
                .header("Date", rfcDate) // add date and auth headers
                .header("Authorization", "AWS " + user + ":" + signature)
                .header("Connection", "close") // make sure maintenance calls are not kept alive
                .buildGet();

        // make REST call
        log.debug("retrieving VDC node list from {}", host.getName());
        List<String> dataNodes = invocation.invoke(ListDataNode.class).getDataNodes();

        List<Host> hosts = new ArrayList<>();
        for (String node : dataNodes) {
            hosts.add(new Host(node));
        }
        return hosts;
    }

    protected URI getRequestUri(Host host, String path) {
        try {
            String portStr = (port > -1) ? ":" + port : "";
            return new URI(protocol + "://" + host.getName() + portStr + path);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    protected String getSignature(String canonicalString, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String signature = new String(Base64.encodeBase64(mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8))));
        log.debug("canonicalString:\n" + canonicalString);
        log.debug("signature:\n" + signature);
        return signature;
    }

    protected void updateVdcNodes(Vdc vdc, List<Host> nodeList) {
        if (nodeList == null || nodeList.isEmpty()) throw new RuntimeException("node list is empty");

        // make sure the hosts are associated with the VDC first
        List<VdcHost> vdcNodeList = new ArrayList<>();
        for (Host host : nodeList) {
            vdcNodeList.add(new VdcHost(vdc, host.getName()));
        }

        // we need to maintain references to existing hosts to preserve health status, which is managed by the load
        // balancer
        for (Iterator<VdcHost> vdcI = vdc.iterator(); vdcI.hasNext(); ) {
            VdcHost vdcHost = vdcI.next();
            boolean hostPresent = false;
            for (Iterator<VdcHost> nodeI = vdcNodeList.iterator(); nodeI.hasNext(); ) {
                VdcHost node = nodeI.next();

                // already aware of this node; remove from new node list
                if (vdcHost.equals(node)) {
                    hostPresent = true;
                    nodeI.remove();
                }
            }

            // host is not in the updated host list, so remove it from the VDC
            if (!hostPresent) {
                log.info("host " + vdcHost.getName() + " was not in the updated node list; removing from VDC " + vdc.getName());
                vdcI.remove();
            }
        }

        // add any remaining new hosts we weren't previously aware of
        for (VdcHost node : vdcNodeList) {
            log.info("adding host " + node.getName() + " to VDC " + vdc.getName());
            vdc.getHosts().add(new VdcHost(vdc, node.getName()));
        }
    }

    public JerseyClient getClient() {
        return client;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public String getUser() {
        return user;
    }

    public String getSecret() {
        return secret;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public List<Vdc> getVdcs() {
        return vdcs;
    }

    public void setVdcs(List<Vdc> vdcs) {
        this.vdcs = vdcs;
    }

    public EcsHostListProvider withVdcs(Vdc... vdcs) {
        setVdcs(Arrays.asList(vdcs));
        return this;
    }
}
