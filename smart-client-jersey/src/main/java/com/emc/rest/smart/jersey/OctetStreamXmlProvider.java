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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@Produces("application/octet-stream")
@Consumes("application/octet-stream")
public class OctetStreamXmlProvider implements MessageBodyReader<Object> {
    private final Map<Class<?>, JAXBContext> contexts = new ConcurrentHashMap<>();

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return (type.getAnnotation(XmlRootElement.class) != null || type.getAnnotation(XmlType.class) != null)
                && mediaType.equals(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, 
                          MultivaluedMap<String, String> httpHeaders, InputStream entityStream) 
            throws IOException, WebApplicationException {
        try {
            JAXBContext context = contexts.computeIfAbsent(type, t -> {
                try {
                    return JAXBContext.newInstance(t);
                } catch (JAXBException e) {
                    throw new RuntimeException("Failed to create JAXB context for " + t, e);
                }
            });
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return unmarshaller.unmarshal(entityStream);
        } catch (JAXBException e) {
            throw new WebApplicationException("Failed to unmarshal XML", e);
        }
    }
}
