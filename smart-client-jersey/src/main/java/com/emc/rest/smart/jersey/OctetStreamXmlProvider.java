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
package com.emc.rest.smart.jersey;

import org.glassfish.jersey.jaxb.internal.XmlRootElementJaxbProvider;
import javax.inject.Provider;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Providers;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Produces("application/octet-stream")
@Consumes("application/octet-stream")
public class OctetStreamXmlProvider implements MessageBodyReader<Object> {
    private final MessageBodyReader<Object> delegate;

    public OctetStreamXmlProvider(@Context Provider<SAXParserFactory> spf, @Context Providers ps) {
        this.delegate = new XmlRootElementJaxbProvider.General(spf, ps);
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return (type.getAnnotation(XmlRootElement.class) != null || type.getAnnotation(XmlType.class) != null)
                && mediaType.equals(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        return delegate.readFrom(type, genericType, annotations, mediaType, httpHeaders, entityStream);
    }
}
