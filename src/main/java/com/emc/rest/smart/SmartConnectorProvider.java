package com.emc.rest.smart;

import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.client.spi.ConnectorProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Configuration;

public class SmartConnectorProvider implements ConnectorProvider {
    private ConnectorProvider delegated;
    private LoadBalancer loadBalancer;

    public SmartConnectorProvider(ConnectorProvider delegated, SmartConfig smartConfig) {
        this.loadBalancer = smartConfig.getLoadBalancer();
        this.delegated = delegated;
    }

    @Override
    public Connector getConnector(Client client, Configuration runtimeConfig) {
        return new SmartConnector(delegated.getConnector(client, runtimeConfig), loadBalancer);
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }
}
