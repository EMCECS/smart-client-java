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
package com.emc.util;

import jdk.vm.ci.meta.Assumptions;
import org.junit.Assume;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility functions to configure tests through a properties file located on either
 * the classpath or in the user's home directory.
 */
public class TestConfig {
    public static final String DEFAULT_PROJECT_NAME = "test"; // => $HOME/test.properties

    /**
     * Locates and loads the properties file for the test configuration.  This file can
     * reside in one of two places: somewhere in the CLASSPATH or in the user's home
     * directory.
     *
     * @return the contents of the properties file as a {@link java.util.Properties} object.
     * @throws java.io.FileNotFoundException if the file was not found
     * @throws java.io.IOException           if there was an error reading the file.
     */
    public static Properties getProperties(String projectName, boolean failIfMissing) throws IOException {
        String propFile = projectName + ".properties";
        InputStream in = TestConfig.class.getClassLoader().getResourceAsStream(propFile);
        if (in == null) {
            // Check in home directory
            File homeProps = new File(System.getProperty("user.home") + File.separator + propFile);
            if (homeProps.exists()) {
                in = new FileInputStream(homeProps);
            }
        }

        if (in == null) {
            Assume.assumeFalse(projectName + ".properties missing (look in src/test/resources for template)", failIfMissing);
            return null;
        }

        Properties props = new Properties();
        props.load(in);
        in.close();

        return props;
    }

    @SuppressWarnings("unused")
    public static Properties getProperties() throws IOException {
        return getProperties(DEFAULT_PROJECT_NAME, true);
    }

    /**
     * Convenience if you're using the default properties file
     */
    @SuppressWarnings("unused")
    public static String getPropertyNotEmpty(String key) throws IOException {
        return getPropertyNotEmpty(getProperties(), key);
    }

    /**
     * Utility method that gets a key from a Properties object and throws a
     * RuntimeException if the key does not exist or is not set.
     */
    public static String getPropertyNotEmpty(Properties p, String key) {
        String value = p.getProperty(key);
        Assume.assumeTrue(String.format("The property %s is required", key), value != null && !value.isEmpty());
        return value;
    }
}
