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
package com.emc.rest.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StreamUtil {

    private static final Logger log = LoggerFactory.getLogger(StreamUtil.class);

    /**
     * Closes streams no matter what.
     */
    public static String readAsString(InputStream inputStream) {
        if (inputStream == null) return null;
        try {
            return new java.util.Scanner(inputStream, "UTF-8").useDelimiter("\\A").next();
        } catch (java.util.NoSuchElementException e) {
            return "";
        } finally {
            try {
                inputStream.close();
            } catch (Throwable t) {
                log.warn("could not close stream", t);
            }
        }
    }

    /**
     * Closes streams no matter what.
     */
    public static long copy(InputStream is, OutputStream os, long maxBytes) throws IOException {
        byte[] buffer = new byte[1024 * 64]; // 64k buffer
        long count = 0;
        int read, maxRead;

        try {
            while (count < maxBytes) {
                maxRead = (int) Math.min(buffer.length, maxBytes - count);
                if (-1 == (read = is.read(buffer, 0, maxRead))) break;
                os.write(buffer, 0, read);
                count += read;
            }
        } finally {
            try {
                is.close();
            } catch (Throwable t) {
                log.warn("could not close stream", t);
            }
            try {
                os.close();
            } catch (Throwable t) {
                log.warn("could not close stream", t);
            }
        }
        return count;
    }

    private StreamUtil() {
    }
}
