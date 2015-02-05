/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.rest.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class StreamUtil {
    public static String readAsString(InputStream inputStream) throws IOException {
        if (inputStream == null) return null;
        try (InputStream autoClose = inputStream) {
            return new java.util.Scanner(autoClose, "UTF-8").useDelimiter("\\A").next();
        } catch (java.util.NoSuchElementException e) {
            return "";
        }
    }

    public static long copy(InputStream is, OutputStream os, long maxBytes) throws IOException {
        byte[] buffer = new byte[1024 * 64]; // 64k buffer
        long count = 0;
        int read, maxRead;

        try (InputStream autoClose = is) {
            while (count < maxBytes) {
                maxRead = (int) Math.min((long) buffer.length, maxBytes - count);
                if (-1 == (read = autoClose.read(buffer, 0, maxRead))) break;
                os.write(buffer, 0, read);
                count += read;
            }
        }
        return count;
    }

    private StreamUtil() {
    }
}
