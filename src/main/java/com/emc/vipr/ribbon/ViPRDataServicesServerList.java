/*
 * Copyright 2014 EMC Corporation. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.emc.vipr.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.Server;

import java.util.List;

public class ViPRDataServicesServerList extends AbstractServerList<Server> {
    protected static final int DEFAULT_TIMEOUT = 5000; // ms

    private List<Server> nodeList;
    private String user;
    private String secret;
    private int timeout = -1; // ms

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        String nodeStr = clientConfig.getPropertyAsString(SmartClientConfigKey.ViPRDataServicesInitialNodes, "");
        if (nodeStr.trim().length() == 0)
            throw new IllegalStateException("No servers configured in smartConfig or NIWS config");
        nodeList = SmartClientConfig.parseServerList(nodeStr);

        user = clientConfig.getPropertyAsString(SmartClientConfigKey.ViPRDataServicesUser, null);

        secret = clientConfig.getPropertyAsString(SmartClientConfigKey.ViPRDataServicesUserSecret, null);

        timeout = clientConfig.getPropertyAsInteger(SmartClientConfigKey.ViPRDataServicesTimeout, DEFAULT_TIMEOUT);
    }

    @Override
    public List<Server> getInitialListOfServers() {
        return pollForServers();
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        return pollForServers();
    }

    protected List<Server> pollForServers() {
        // TODO: poll for active node list (?)
        return nodeList;
    }
}
