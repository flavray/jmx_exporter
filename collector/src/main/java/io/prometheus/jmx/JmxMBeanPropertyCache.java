package io.prometheus.jmx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
 * This object stores a mapping of mBean objectNames to mBean key property lists. The main purpose of it is to reduce
 * the frequency with which we invoke PROPERTY_PATTERN when discovering mBeans.
 */
class JmxMBeanPropertyCache {
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
            "([^,=:\\*\\?]+)" + // Name - non-empty, anything but comma, equals, colon, star, or question mark
                    "=" +  // Equals
                    "(" + // Either
                    "\"" + // Quoted
                    "(?:" + // A possibly empty sequence of
                    "[^\\\\\"]*" + // Greedily match anything but backslash or quote
                    "(?:\\\\.)?" + // Greedily see if we can match an escaped sequence
                    ")*" +
                    "\"" +
                    "|" + // Or
                    "[^,=:\"]*" + // Unquoted - can be empty, anything but comma, equals, colon, or quote
                    ")");

    // Implement a version of ObjectName.getKeyPropertyList that returns the
    // properties in the ordered they were added (the ObjectName stores them
    // in the order they were added).
    private final Map<ObjectName, LinkedHashMap<String, String>> keyPropertiesPerBean;

    // Cache mbean attribute info to avoid repetitive calls to the mbean server
    private final Map<ObjectName, MBeanAttributeInfo[]> attributeInfoPerBean;

    // Whether to use the attributeInfoPerBean cache.
    // Bean information is usually immutable ([1]) and can be cached. However, applications are able to change
    // this information during the lifetime of the process, in which case caching is not recommended.
    //
    // [1] https://docs.oracle.com/javase/8/docs/api/javax/management/MBeanInfo.html
    private boolean cacheAttributeInfo = false;

    public JmxMBeanPropertyCache() {
        this(false);
    }

    public JmxMBeanPropertyCache(boolean cacheAttributeInfo) {
        this.keyPropertiesPerBean = new ConcurrentHashMap<ObjectName, LinkedHashMap<String, String>>();
        this.attributeInfoPerBean = new ConcurrentHashMap<ObjectName, MBeanAttributeInfo[]>();
        this.cacheAttributeInfo = cacheAttributeInfo;
    }

    Map<ObjectName, LinkedHashMap<String, String>> getKeyPropertiesPerBean() {
        return keyPropertiesPerBean;
    }

    public LinkedHashMap<String, String> getKeyPropertyList(ObjectName mbeanName) {
        LinkedHashMap<String, String> keyProperties = keyPropertiesPerBean.get(mbeanName);
        if (keyProperties == null) {
            keyProperties = new LinkedHashMap<String, String>();
            String properties = mbeanName.getKeyPropertyListString();
            Matcher match = PROPERTY_PATTERN.matcher(properties);
            while (match.lookingAt()) {
                keyProperties.put(match.group(1), match.group(2));
                properties = properties.substring(match.end());
                if (properties.startsWith(",")) {
                    properties = properties.substring(1);
                }
                match.reset(properties);
            }
            keyPropertiesPerBean.put(mbeanName, keyProperties);
        }
        return keyProperties;
    }

    public MBeanAttributeInfo[] getAttributes(ObjectName mbeanName, MBeanServerConnection beanConn) throws Exception {
        if (!cacheAttributeInfo) {
            return beanConn.getMBeanInfo(mbeanName).getAttributes();
        }

        MBeanAttributeInfo[] info = attributeInfoPerBean.get(mbeanName);
        if (info == null) {
            info = beanConn.getMBeanInfo(mbeanName).getAttributes();
            attributeInfoPerBean.put(mbeanName, info);
        }
        return info;
    }

    public void onlyKeepMBeans(Set<ObjectName> latestBeans) {
        for (ObjectName prevName : keyPropertiesPerBean.keySet()) {
            if (!latestBeans.contains(prevName)) {
                keyPropertiesPerBean.remove(prevName);
            }
        }

        for (ObjectName prevName : attributeInfoPerBean.keySet()) {
            if (!latestBeans.contains(prevName)) {
                attributeInfoPerBean.remove(prevName);
            }
        }
    }

    public void setCacheAttributeInfo(boolean cacheAttributeInfo) {
        // If the cache is being disabled, clear it to avoid stale entries in case it is enabled again
        if (this.cacheAttributeInfo && !cacheAttributeInfo) {
            attributeInfoPerBean.clear();
        }

        this.cacheAttributeInfo = cacheAttributeInfo;
    }
}
