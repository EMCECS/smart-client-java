/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.rest.smart;

import com.sun.jersey.core.impl.provider.entity.XMLRootElementProvider;
import com.sun.jersey.spi.inject.Injectable;

import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;
import javax.xml.parsers.SAXParserFactory;

public class MeasuredJaxbWriter extends MeasuredMessageBodyWriter<Object> {
    public MeasuredJaxbWriter(MessageBodyWriter<Object> wrapped) {
        super(wrapped);
    }

    @Produces("application/xml")
    public static final class App extends MeasuredJaxbWriter {
        public App(@Context Injectable<SAXParserFactory> spf, @Context Providers ps) {
            super(new XMLRootElementProvider.App(spf, ps));
        }
    }

    @Produces("text/xml")
    public static final class Text extends MeasuredJaxbWriter {
        public Text(@Context Injectable<SAXParserFactory> spf, @Context Providers ps) {
            super(new XMLRootElementProvider.Text(spf, ps));
        }
    }

    @Produces("*/*")
    public static final class General extends MeasuredJaxbWriter {
        public General(@Context Injectable<SAXParserFactory> spf, @Context Providers ps) {
            super(new XMLRootElementProvider.General(spf, ps));
        }
    }
}
