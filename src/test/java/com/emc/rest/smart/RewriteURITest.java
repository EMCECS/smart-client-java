/*
 * Copyright (c) 2015-2021, EMC Corporation.
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
