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
