package com.emc.util;

import java.io.*;
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
    public static Properties getProperties(String projectName) throws IOException {
        String propFile = projectName + ".properties";
        InputStream in = TestConfig.class.getClassLoader().getResourceAsStream(propFile);
        if (in == null) {
            // Check in home directory
            File homeProps = new File(System.getProperty("user.home") + File.separator + propFile);
            if (homeProps.exists()) {
                in = new FileInputStream(homeProps);
            } else {
                throw new FileNotFoundException(propFile);
            }
        }

        Properties props = new Properties();
        props.load(in);
        in.close();

        return props;
    }

    public static Properties getProperties() throws IOException {
        return getProperties(DEFAULT_PROJECT_NAME);
    }

    /**
     * Convenience if you're using the default properties file
     */
    public static String getPropertyNotEmpty(String key) throws IOException {
        return getPropertyNotEmpty(getProperties(), key);
    }

    /**
     * Utility method that gets a key from a Properties object and throws a
     * RuntimeException if the key does not exist or is not set.
     */
    public static String getPropertyNotEmpty(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new RuntimeException(String.format("The property %s is required", key));
        }
        return value;
    }
}
