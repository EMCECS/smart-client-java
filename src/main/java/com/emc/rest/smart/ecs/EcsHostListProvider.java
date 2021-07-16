/*
 * Copyright (c) 2015-2016, EMC Corporation.
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
package com.emc.rest.smart.ecs;

import com.emc.rest.smart.Host;
import com.emc.rest.smart.HostListProvider;
import com.emc.rest.smart.LoadBalancer;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import org.apache.commons.codec.binary.Base64;
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
    private final Client client;
    private final LoadBalancer loadBalancer;
    private final String user;
    private final String secret;
    private String protocol = DEFAULT_PROTOCOL;
    private int port = DEFAULT_PORT;
    private List<Vdc> vdcs;

    public EcsHostListProvider(Client client, LoadBalancer loadBalancer, String user, String secret) {
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
        PingResponse response = client.resource(getRequestUri(host, "/?ping"))
                .header("x-emc-namespace", "x")
                .header("Connection", "close") // make sure maintenance calls are not kept alive
                .get(PingResponse.class);

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
        client.destroy();
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
        WebResource.Builder request = client.resource(uri).getRequestBuilder();

        // add date and auth headers
        request.header("Date", rfcDate);
        request.header("Authorization", "AWS " + user + ":" + signature);
        // make sure maintenance calls are not kept alive
        request.header("Connection", "close");

        // make REST call
        log.debug("retrieving VDC node list from {}", host.getName());
        List<String> dataNodes = request.get(ListDataNode.class).getDataNodes();

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

    public Client getClient() {
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
