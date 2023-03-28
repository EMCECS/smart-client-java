/*
 * Copyright (c) 2021 Dell Inc., or its subsidiaries. All Rights Reserved.
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
package com.emc.rest.smart;

import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIUtils;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;

public class RewriteURITest {
    private static final String TEST_SCHEME = "https";
    private static final String TEST_HOST_1 = "server1";
    private static final String TEST_HOST_2 = "server2";
    private static final int TEST_PORT = 443;

    // this test is necessary because httpclient changed the behavior of their URIUtils.rewriteURI() method in v4.5.7.
    // this method is a primary dependency of the smart-client - see https://issues.apache.org/jira/browse/HTTPCLIENT-1968
    @Test
    public void testApacheRewriteURI() throws Exception {
        // test double slash (encoded)
        testApacheRewritePath("/%2Ffoo/%2Fbar");

        // test double-slash (not encoded)
        testApacheRewritePath("//foo//bar");

        // test exclamations (encoded)
        testApacheRewritePath("/foo%21bar%21baz");

        // test exclamations (not encoded)
        testApacheRewritePath("/foo!bar!baz");

        // test plus (encoded)
        testApacheRewritePath("/foo%2Bbar%2Bbaz");

        // test plus (not encoded)
        testApacheRewritePath("/foo+bar+baz");
    }

    private void testApacheRewritePath(String path) throws Exception {
        URI uri = new URI(TEST_SCHEME, null, TEST_HOST_1, TEST_PORT, path, null, null);
        // NOTE: flags were added in httpclient 4.5.8 to allow for no normalization (which matches behavior prior to 4.5.7)
        uri = URIUtils.rewriteURI(uri, new HttpHost(TEST_HOST_2, TEST_PORT, TEST_SCHEME), URIUtils.NO_FLAGS);
        Assert.assertEquals(path, uri.getPath());
    }
}
