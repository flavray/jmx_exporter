package io.prometheus.jmx;

import org.junit.Test;

import java.io.ObjectInputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.loading.ClassLoaderRepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JmxMBeanPropertyCacheTest {

    @Test
    public void testSingleObjectName() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        LinkedHashMap<String, String> parameterList = testCache.getKeyPropertyList(new ObjectName("com.organisation:name=value"));
        assertSameElementsAndOrder(parameterList, "name", "value");
    }

    @Test
    public void testSimpleObjectName() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        LinkedHashMap<String, String> parameterList = testCache.getKeyPropertyList(new ObjectName("com.organisation:name=value,name2=value2"));
        assertSameElementsAndOrder(parameterList, "name", "value", "name2", "value2");
    }

    @Test
    public void testQuotedObjectName() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        LinkedHashMap<String, String> parameterList = testCache.getKeyPropertyList(new ObjectName("com.organisation:name=value,name2=\"value2\""));
        assertSameElementsAndOrder(parameterList, "name", "value", "name2", "\"value2\"");
    }

    @Test
    public void testQuotedObjectNameWithComma() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        LinkedHashMap<String, String> parameterList = testCache.getKeyPropertyList(new ObjectName("com.organisation:name=\"value,more\",name2=value2"));
        assertSameElementsAndOrder(parameterList, "name", "\"value,more\"", "name2", "value2");
    }

    @Test
    public void testQuotedObjectNameWithEquals() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        LinkedHashMap<String, String> parameterList = testCache.getKeyPropertyList(new ObjectName("com.organisation:name=\"value=more\",name2=value2"));
        assertSameElementsAndOrder(parameterList, "name", "\"value=more\"", "name2", "value2");
    }

    @Test
    public void testQuotedObjectNameWithQuote() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        LinkedHashMap<String, String> parameterList = testCache.getKeyPropertyList(new ObjectName("com.organisation:name=\"value\\\"more\",name2=value2"));
        assertSameElementsAndOrder(parameterList, "name", "\"value\\\"more\"", "name2", "value2");
    }

    @Test
    public void testQuotedObjectNameWithBackslash() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        LinkedHashMap<String, String> parameterList = testCache.getKeyPropertyList(new ObjectName("com.organisation:name=\"value\\\\more\",name2=value2"));
        assertSameElementsAndOrder(parameterList, "name", "\"value\\\\more\"", "name2", "value2");
    }

    @Test
    public void testQuotedObjectNameWithMultipleQuoted() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        LinkedHashMap<String, String> parameterList = testCache.getKeyPropertyList(new ObjectName("com.organisation:name=\"value\\\\\\?\\*\\n\\\",:=more\",name2= value2 "));
        assertSameElementsAndOrder(parameterList, "name", "\"value\\\\\\?\\*\\n\\\",:=more\"", "name2", " value2 ");
    }

    @Test
    public void testIssue52() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        LinkedHashMap<String, String> parameterList = testCache.getKeyPropertyList(
                new ObjectName("org.apache.camel:context=ourinternalname,type=endpoints,name=\"seda://endpointName\\?concurrentConsumers=8&size=50000\""));
        assertSameElementsAndOrder(parameterList,
                "context", "ourinternalname",
                "type", "endpoints",
                "name", "\"seda://endpointName\\?concurrentConsumers=8&size=50000\"");
    }

    @Test
    public void testIdempotentGet() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        ObjectName testObjectName = new ObjectName("com.organisation:name=value");
        LinkedHashMap<String, String> parameterListFirst = testCache.getKeyPropertyList(testObjectName);
        LinkedHashMap<String, String> parameterListSecond = testCache.getKeyPropertyList(testObjectName);
        assertEquals(parameterListFirst, parameterListSecond);
    }

    @Test
    public void testGetAfterDeleteOneObject() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        ObjectName testObjectName = new ObjectName("com.organisation:name=value");
        LinkedHashMap<String, String> parameterListFirst = testCache.getKeyPropertyList(testObjectName);
        assertNotNull(parameterListFirst);
        testCache.onlyKeepMBeans(Collections.<ObjectName>emptySet());
        assertEquals(Collections.<ObjectName, LinkedHashMap<String,String>>emptyMap(), testCache.getKeyPropertiesPerBean());
        LinkedHashMap<String, String> parameterListSecond = testCache.getKeyPropertyList(testObjectName);
        assertNotNull(parameterListSecond);
    }

    @Test
    public void testRemoveOneOfMultipleObjects() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        ObjectName mBean1 = new ObjectName("com.organisation:name=value1");
        ObjectName mBean2 = new ObjectName("com.organisation:name=value2");
        ObjectName mBean3 = new ObjectName("com.organisation:name=value3");
        testCache.getKeyPropertyList(mBean1);
        testCache.getKeyPropertyList(mBean2);
        testCache.getKeyPropertyList(mBean3);
        Set<ObjectName> keepSet = new HashSet<ObjectName>();
        keepSet.add(mBean2);
        keepSet.add(mBean3);
        testCache.onlyKeepMBeans(keepSet);
        assertEquals(2, testCache.getKeyPropertiesPerBean().size());
        assertTrue(testCache.getKeyPropertiesPerBean().keySet().contains(mBean2));
        assertTrue(testCache.getKeyPropertiesPerBean().keySet().contains(mBean3));
    }

    @Test
    public void testRemoveEmptyIdempotent() throws Throwable {
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        testCache.onlyKeepMBeans(Collections.<ObjectName>emptySet());
        testCache.onlyKeepMBeans(Collections.<ObjectName>emptySet());
        assertEquals(testCache.getKeyPropertiesPerBean().size(), 0);
    }

    @Test
    public void testCacheMBeanAttributeInfoUncached() throws Throwable {
        FakeMBeanServer beanConn = new FakeMBeanServer();
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        ObjectName testObjectName = new ObjectName("com.organisation:name=value");
        testCache.getAttributes(testObjectName, beanConn);
        testCache.getAttributes(testObjectName, beanConn);
        assertEquals(2L, beanConn.getMBeanInfoCallCount.get(testObjectName).longValue());
    }


    @Test
    public void testCacheMBeanAttributeInfoCached() throws Throwable {
        FakeMBeanServer beanConn = new FakeMBeanServer();
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        testCache.setCacheAttributeInfo(true);
        ObjectName testObjectName = new ObjectName("com.organisation:name=value");
        testCache.getAttributes(testObjectName, beanConn);
        testCache.getAttributes(testObjectName, beanConn);
        assertEquals(1L, beanConn.getMBeanInfoCallCount.get(testObjectName).longValue());
    }


    @Test
    public void testCacheMBeanAttributeInfoClear() throws Throwable {
        FakeMBeanServer beanConn = new FakeMBeanServer();
        JmxMBeanPropertyCache testCache = new JmxMBeanPropertyCache();
        testCache.setCacheAttributeInfo(true);
        ObjectName testObjectName = new ObjectName("com.organisation:name=value");
        testCache.getAttributes(testObjectName, beanConn);

        testCache.setCacheAttributeInfo(false);
        testCache.getAttributes(testObjectName, beanConn);
        assertEquals(2L, beanConn.getMBeanInfoCallCount.get(testObjectName).longValue());

        // Re-enable cache - ensure the bean connection is queried on the next call and the next only
        testCache.setCacheAttributeInfo(true);
        testCache.getAttributes(testObjectName, beanConn);
        assertEquals(3L, beanConn.getMBeanInfoCallCount.get(testObjectName).longValue());
        testCache.getAttributes(testObjectName, beanConn);
        assertEquals(3L, beanConn.getMBeanInfoCallCount.get(testObjectName).longValue());
    }

    private void assertSameElementsAndOrder(LinkedHashMap<?, ?> actual, Object... expected) {
        assert expected.length % 2 == 0;
        List<Map.Entry<?,?>> actualList = new ArrayList<Map.Entry<?, ?>>(actual.entrySet());
        List<Map.Entry<?,?>> expectedList = new ArrayList<Map.Entry<?,?>>();
        for (int i = 0; i < expected.length / 2; i++) {
            expectedList.add(new AbstractMap.SimpleImmutableEntry<Object, Object>(expected[i * 2], expected[i * 2 + 1]));
        }
        assertEquals(expectedList, actualList);
    }

    private static class FakeMBeanServer implements MBeanServer {

        Map<ObjectName, Long> getMBeanInfoCallCount = new HashMap<ObjectName, Long>();

        @Override
        public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IntrospectionException, ReflectionException {
            Long callCount = getMBeanInfoCallCount.get(name);
            if (callCount == null) {
                callCount = 0L;
            }
            getMBeanInfoCallCount.put(name, callCount + 1);

            return new MBeanInfo(null, null, new MBeanAttributeInfo[0], new MBeanConstructorInfo[0], new MBeanOperationInfo[0], new MBeanNotificationInfo[0]);
        }

        @Override
        public ObjectInstance createMBean(String className, ObjectName name) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException {
            return null;
        }

        @Override
        public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException {
            return null;
        }

        @Override
        public ObjectInstance createMBean(String className, ObjectName name, Object[] params, String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException {
            return null;
        }

        @Override
        public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName, Object[] params, String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException {
            return null;
        }

        @Override
        public ObjectInstance registerMBean(Object object, ObjectName name) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
            return null;
        }

        @Override
        public void unregisterMBean(ObjectName name) throws InstanceNotFoundException, MBeanRegistrationException {

        }

        @Override
        public ObjectInstance getObjectInstance(ObjectName name) throws InstanceNotFoundException {
            return null;
        }

        @Override
        public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
            return null;
        }

        @Override
        public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
            return null;
        }

        @Override
        public boolean isRegistered(ObjectName name) {
            return false;
        }

        @Override
        public Integer getMBeanCount() {
            return null;
        }

        @Override
        public Object getAttribute(ObjectName name, String attribute) throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
            return null;
        }

        @Override
        public AttributeList getAttributes(ObjectName name, String[] attributes) throws InstanceNotFoundException, ReflectionException {
            return null;
        }

        @Override
        public void setAttribute(ObjectName name, Attribute attribute) throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {

        }

        @Override
        public AttributeList setAttributes(ObjectName name, AttributeList attributes) throws InstanceNotFoundException, ReflectionException {
            return null;
        }

        @Override
        public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature) throws InstanceNotFoundException, MBeanException, ReflectionException {
            return null;
        }

        @Override
        public String getDefaultDomain() {
            return null;
        }

        @Override
        public String[] getDomains() {
            return new String[0];
        }

        @Override
        public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException {

        }

        @Override
        public void addNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException {

        }

        @Override
        public void removeNotificationListener(ObjectName name, ObjectName listener) throws InstanceNotFoundException, ListenerNotFoundException {

        }

        @Override
        public void removeNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException, ListenerNotFoundException {

        }

        @Override
        public void removeNotificationListener(ObjectName name, NotificationListener listener) throws InstanceNotFoundException, ListenerNotFoundException {

        }

        @Override
        public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException, ListenerNotFoundException {

        }

        @Override
        public boolean isInstanceOf(ObjectName name, String className) throws InstanceNotFoundException {
            return false;
        }

        @Override
        public Object instantiate(String className) throws ReflectionException, MBeanException {
            return null;
        }

        @Override
        public Object instantiate(String className, ObjectName loaderName) throws ReflectionException, MBeanException, InstanceNotFoundException {
            return null;
        }

        @Override
        public Object instantiate(String className, Object[] params, String[] signature) throws ReflectionException, MBeanException {
            return null;
        }

        @Override
        public Object instantiate(String className, ObjectName loaderName, Object[] params, String[] signature) throws ReflectionException, MBeanException, InstanceNotFoundException {
            return null;
        }

        @Override
        public ObjectInputStream deserialize(ObjectName name, byte[] data) throws InstanceNotFoundException, OperationsException {
            return null;
        }

        @Override
        public ObjectInputStream deserialize(String className, byte[] data) throws OperationsException, ReflectionException {
            return null;
        }

        @Override
        public ObjectInputStream deserialize(String className, ObjectName loaderName, byte[] data) throws InstanceNotFoundException, OperationsException, ReflectionException {
            return null;
        }

        @Override
        public ClassLoader getClassLoaderFor(ObjectName mbeanName) throws InstanceNotFoundException {
            return null;
        }

        @Override
        public ClassLoader getClassLoader(ObjectName loaderName) throws InstanceNotFoundException {
            return null;
        }

        @Override
        public ClassLoaderRepository getClassLoaderRepository() {
            return null;
        }
    }
}
