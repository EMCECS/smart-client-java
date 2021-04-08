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

import org.junit.Assert;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.io.StringWriter;

public class ListDataNodeTest {
    @Test
    public void testMarshalling() throws Exception {
        JAXBContext context = JAXBContext.newInstance(ListDataNode.class);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<ListDataNode xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" +
                "<DataNodes>10.241.48.51</DataNodes>" +
                "<DataNodes>10.241.48.52</DataNodes>" +
                "<DataNodes>10.241.48.53</DataNodes>" +
                "<DataNodes>10.241.48.54</DataNodes>" +
                "<VersionInfo>1.2.0.0.60152.d32b519</VersionInfo>" +
                "</ListDataNode>";

        ListDataNode listDataNode = new ListDataNode();
        listDataNode.getDataNodes().add("10.241.48.51");
        listDataNode.getDataNodes().add("10.241.48.52");
        listDataNode.getDataNodes().add("10.241.48.53");
        listDataNode.getDataNodes().add("10.241.48.54");
        listDataNode.setVersionInfo("1.2.0.0.60152.d32b519");

        // unmarshall and compare to object
        Unmarshaller unmarshaller = context.createUnmarshaller();
        ListDataNode unmarshalledObject = (ListDataNode) unmarshaller.unmarshal(new StringReader(xml));

        Assert.assertEquals(listDataNode.getDataNodes(), unmarshalledObject.getDataNodes());
        Assert.assertEquals(listDataNode.getVersionInfo(), unmarshalledObject.getVersionInfo());

        // marshall and compare XML
        Marshaller marshaller = context.createMarshaller();
        StringWriter writer = new StringWriter();
        marshaller.marshal(listDataNode, writer);

        Assert.assertEquals(xml, writer.toString());
    }
}
