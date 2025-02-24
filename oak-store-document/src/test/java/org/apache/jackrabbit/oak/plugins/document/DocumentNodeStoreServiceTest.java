/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.document;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.jackrabbit.guava.common.collect.Maps;
import com.mongodb.MongoClient;

import org.apache.commons.io.FilenameUtils;
import org.apache.jackrabbit.oak.commons.PerfLogger;
import org.apache.jackrabbit.oak.plugins.document.mongo.MongoDocumentStore;
import org.apache.jackrabbit.oak.plugins.document.mongo.MongoDocumentStoreTestHelper;
import org.apache.jackrabbit.oak.plugins.document.spi.JournalPropertyService;
import org.apache.jackrabbit.oak.plugins.document.spi.lease.LeaseFailureHandler;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.apache.jackrabbit.oak.stats.StatisticsProvider;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.apache.jackrabbit.oak.plugins.document.Configuration.PID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;

public class DocumentNodeStoreServiceTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    @Rule
    public final TemporaryFolder target = new TemporaryFolder(new File("target"));

    private final DocumentNodeStoreService service = new DocumentNodeStoreService();

    private final DocumentNodeStoreService.Preset preset = new DocumentNodeStoreService.Preset();

    private String repoHome;

    @Before
    public void setUp() throws  Exception {
        assumeTrue(MongoUtils.isAvailable());
        context.registerService(StatisticsProvider.class, StatisticsProvider.NOOP);
        context.registerInjectActivateService(preset);
        MockOsgi.injectServices(service, context.bundleContext());
        repoHome = target.newFolder().getAbsolutePath();
    }

    @After
    public void tearDown() throws Exception {
        MockOsgi.deactivate(service, context.bundleContext());
        MongoUtils.dropCollections(MongoUtils.DB);
        ClusterNodeInfo.resetRecoveryDelayMillisToDefault();
    }

    @Test
    public void persistentCache() {
        String persistentCache = FilenameUtils.concat(repoHome, "cache");
        assertPersistentCachePath(persistentCache, persistentCache, "");
    }

    @Test
    public void journalCache() {
        String journalCache = FilenameUtils.concat(repoHome, "diff-cache");
        assertJournalCachePath(journalCache, journalCache, "");
    }

    @Test
    public void persistentCacheWithRepositoryHome() {
        assertPersistentCachePath(FilenameUtils.concat(repoHome, "cache"),
                "cache", repoHome);
    }

    @Test
    public void journalCacheWithRepositoryHome() {
        assertJournalCachePath(FilenameUtils.concat(repoHome, "diff-cache"),
                "diff-cache", repoHome);
    }

    @Test
    public void defaultPersistentCacheWithRepositoryHome() {
        String persistentCache = FilenameUtils.concat(repoHome, "cache");
        assertPersistentCachePath(persistentCache, "", repoHome);
    }

    @Test
    public void defaultJournalCacheWithRepositoryHome() {
        String journalCache = FilenameUtils.concat(repoHome, "diff-cache");
        assertJournalCachePath(journalCache, "", repoHome);
    }

    @Test
    public void disablePersistentCacheWithRepositoryHome() {
        String persistentCache = FilenameUtils.concat(repoHome, "cache");
        assertNoPersistentCachePath(persistentCache, "-", repoHome);

    }

    @Test
    public void disableJournalCacheWithRepositoryHome() {
        String journalCache = FilenameUtils.concat(repoHome, "diff-cache");
        assertNoJournalCachePath(journalCache, "-", repoHome);
    }

    @Test
    public void journalPropertyTracker() throws Exception {
        MockOsgi.setConfigForPid(context.bundleContext(), PID, newConfig(repoHome));
        MockOsgi.activate(service, context.bundleContext());
        DocumentNodeStore store = context.getService(DocumentNodeStore.class);
        assertEquals(0, store.getJournalPropertyHandlerFactory().getServiceCount());

        context.registerService(JournalPropertyService.class, mock(JournalPropertyService.class));
        assertEquals(1, store.getJournalPropertyHandlerFactory().getServiceCount());
    }

    @Test
    public void setUpdateLimit() throws Exception {
        Map<String, Object> config = newConfig(repoHome);
        config.put(DocumentNodeStoreServiceConfiguration.PROP_UPDATE_LIMIT, 17);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());
        DocumentNodeStore store = context.getService(DocumentNodeStore.class);
        assertEquals(17, store.getUpdateLimit());
    }

    @Test
    public void keepAlive() throws Exception {
        Map<String, Object> config = newConfig(repoHome);
        config.put(DocumentNodeStoreServiceConfiguration.PROP_SO_KEEP_ALIVE, true);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());
        DocumentNodeStore store = context.getService(DocumentNodeStore.class);
        MongoDocumentStore mds = getMongoDocumentStore(store);
        MongoClient client = MongoDocumentStoreTestHelper.getClient(mds);
        assertTrue(client.getMongoClientOptions().isSocketKeepAlive());
    }

    @Test
    public void continuousRGCDefault() throws Exception {
        Map<String, Object> config = newConfig(repoHome);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());
        boolean jobScheduled = false;
        for (Runnable r : context.getServices(Runnable.class, "(scheduler.expression=\\*/5 \\* \\* \\* \\* ?)")) {
            jobScheduled |= r.getClass().equals(DocumentNodeStoreService.RevisionGCJob.class);
        }
        assertTrue(jobScheduled);
    }

    @Test
    public void continuousRGCJobAsSupplier() throws Exception {
        Map<String, Object> config = newConfig(repoHome);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());
        Runnable rgcJob = null;
        for (Runnable r : context.getServices(Runnable.class, null)) {
            if (r.getClass().equals(DocumentNodeStoreService.RevisionGCJob.class)) {
                rgcJob = r;
            }
        }
        assertNotNull(rgcJob);
        assertTrue(rgcJob instanceof Supplier);
        assertNotNull(((Supplier<String>) rgcJob).get());
    }

    @Test
    public void persistentCacheExclude() throws Exception{
        Map<String, Object> config = newConfig(repoHome);
        config.put("persistentCacheIncludes", new String[] {"/a/b", "/c/d ", null});
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());

        DocumentNodeStore dns = context.getService(DocumentNodeStore.class);
        assertTrue(dns.getNodeCachePredicate().test(Path.fromString("/a/b/c")));
        assertTrue(dns.getNodeCachePredicate().test(Path.fromString("/c/d/e")));

        assertFalse(dns.getNodeCachePredicate().test(Path.fromString("/x")));
    }

    @Test
    public void preset() throws Exception {
        MockOsgi.setConfigForPid(context.bundleContext(),
                Configuration.PRESET_PID,
                DocumentNodeStoreServiceConfiguration.PROP_SO_KEEP_ALIVE, true);
        MockOsgi.activate(preset, context.bundleContext());

        MockOsgi.setConfigForPid(context.bundleContext(), PID, newConfig(repoHome));
        MockOsgi.activate(service, context.bundleContext());

        DocumentNodeStore store = context.getService(DocumentNodeStore.class);
        MongoDocumentStore mds = getMongoDocumentStore(store);
        assertNotNull(mds);
        MongoClient client = MongoDocumentStoreTestHelper.getClient(mds);
        assertTrue(client.getMongoClientOptions().isSocketKeepAlive());
    }

    @Test
    public void presetOverride() throws Exception {
        MockOsgi.setConfigForPid(context.bundleContext(),
                Configuration.PRESET_PID,
                DocumentNodeStoreServiceConfiguration.PROP_SO_KEEP_ALIVE, true);
        MockOsgi.activate(preset, context.bundleContext());

        Map<String, Object> config = newConfig(repoHome);
        config.put(DocumentNodeStoreServiceConfiguration.PROP_SO_KEEP_ALIVE, false);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);

        MockOsgi.activate(service, context.bundleContext());

        DocumentNodeStore store = context.getService(DocumentNodeStore.class);
        MongoDocumentStore mds = getMongoDocumentStore(store);
        MongoClient client = MongoDocumentStoreTestHelper.getClient(mds);
        assertFalse(client.getMongoClientOptions().isSocketKeepAlive());
    }

    @Test
    public void strictLeaseCheckMode() {
        Map<String, Object> config = newConfig(repoHome);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());

        DocumentNodeStore dns = context.getService(DocumentNodeStore.class);
        // strict is the default
        assertEquals(LeaseCheckMode.STRICT, dns.getClusterInfo().getLeaseCheckMode());
    }

    @Test
    public void lenientLeaseCheckMode() {
        Map<String, Object> config = newConfig(repoHome);
        config.put("leaseCheckMode", LeaseCheckMode.LENIENT.name());
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());

        DocumentNodeStore dns = context.getService(DocumentNodeStore.class);
        assertEquals(LeaseCheckMode.LENIENT, dns.getClusterInfo().getLeaseCheckMode());
    }

    @Test
    public void defaultLeaseFailureHandlerCheck() {
        Map<String, Object> config = newConfig(repoHome);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());
        DocumentNodeStore dns = context.getService(DocumentNodeStore.class);
        assertNotNull(dns);
        ClusterNodeInfo clusterInfo = dns.getClusterInfo();
        LeaseFailureHandler leaseFailureHandler = clusterInfo.getLeaseFailureHandler();
        assertNotNull(leaseFailureHandler);
        try {
            leaseFailureHandler.handleLeaseFailure();
            fail("default leaseFailureHandler should call bundle.stop(), which is not supported");
        } catch (UnsupportedOperationException u) {
            // the default LeaseFailureHandler should fail, as it calls bundle.stop()
            // and that is not supported (throws UnsupportedOperationExceptino)
        }
    }

    @Test
    public void customLeaseFailureHandlerCheck() {
        final AtomicInteger counter = new AtomicInteger(0);
        LeaseFailureHandler customLeaseFailureHandler = new LeaseFailureHandler() {
            @Override
            public void handleLeaseFailure() {
                counter.incrementAndGet();
            }
        };
        context.registerService(LeaseFailureHandler.class, customLeaseFailureHandler);
        Map<String, Object> config = newConfig(repoHome);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());
        DocumentNodeStore dns = context.getService(DocumentNodeStore.class);
        assertNotNull(dns);
        ClusterNodeInfo clusterInfo = dns.getClusterInfo();
        LeaseFailureHandler leaseFailureHandler = clusterInfo.getLeaseFailureHandler();
        assertNotNull(leaseFailureHandler);
        assertEquals(0, counter.get());
        for(int i = 0; i < 10; i++) {
            // now the custom LeaseFailureHandler should be used,
            // which just increments a counter.
            // but more importantly: it should no longer fail,
            // as does the default LeaseFailureHandler
            leaseFailureHandler.handleLeaseFailure();
            assertEquals(i + 1, counter.get());
        }
    }

    @Test
    public void revisionGcDelayFactorCheckMode() {
        Map<String, Object> config = newConfig(repoHome);
        double delayFactor = 0.25;
        config.put("versionGCDelayFactor", delayFactor);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());
        Runnable rgcJob = null;
        for (Runnable r : context.getServices(Runnable.class, null)) {
            if (r.getClass().equals(DocumentNodeStoreService.RevisionGCJob.class)) {
                rgcJob = r;
            }
        }
        assertNotNull(rgcJob);
        rgcJob.run(); //Need to trigger run method explicitly as delay-factor is set in this method
        DocumentNodeStore dns = context.getService(DocumentNodeStore.class);
        assertEquals(delayFactor, dns.getVersionGarbageCollector().getOptions().delayFactor, 0.001);
    }

    @Test
    public void suspendTimeoutMillis() {
        Map<String, Object> config = newConfig(repoHome);
        long suspendTimeoutMillis = 5000;
        config.put("suspendTimeoutMillis", suspendTimeoutMillis);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());

        DocumentNodeStore dns = context.getService(DocumentNodeStore.class);
        assertEquals(suspendTimeoutMillis, dns.commitQueue.getSuspendTimeoutMillis());
    }

    @Test
    public void recoveryDelayMillis0() {
        doRecoveryDelayMillis(0);
    }

    @Test
    public void recoveryDelayMillisNegative() {
        doRecoveryDelayMillis(-1);
    }

    @Test
    public void recoveryDelayMillisMinute() {
        doRecoveryDelayMillis(60000);
    }

    private void doRecoveryDelayMillis(long recoveryDelayMillis) {
        Map<String, Object> config = newConfig(repoHome);
        config.put("recoveryDelayMillis", recoveryDelayMillis);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());

        DocumentNodeStore dns = context.getService(DocumentNodeStore.class);
        assertEquals(recoveryDelayMillis, ClusterNodeInfo.getRecoveryDelayMillis());
    }

    @Test
    public void testPerfLoggerInfoMillis() {
        Map<String, Object> config = newConfig(repoHome);
        config.put("perfLoggerInfoMillis", 100);
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());

        DocumentNodeStore dns = context.getService(DocumentNodeStore.class);
        try {
            Field perfLoggerField = dns.getClass().getDeclaredField("PERFLOG");
            perfLoggerField.setAccessible(true);
            PerfLogger perfLogger = (PerfLogger) perfLoggerField.get(dns);
            Field infoLogMillisField = perfLogger.getClass().getDeclaredField("infoLogMillis");
            infoLogMillisField.setAccessible(true);
            long infoLogMillis = infoLogMillisField.getLong(perfLogger);
            assertEquals(100, infoLogMillis);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to access infoLogMillis field: " + e.getMessage());
        }
    }

    @NotNull
    private static MongoDocumentStore getMongoDocumentStore(DocumentNodeStore s) {
        try {
            Field f = s.getClass().getDeclaredField("nonLeaseCheckingStore");
            f.setAccessible(true);
            return (MongoDocumentStore) f.get(s);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        throw new IllegalStateException();
    }

    private void assertPersistentCachePath(String expectedPath,
                                           String persistentCache,
                                           String repoHome) {
        Map<String, Object> config = newConfig(repoHome);
        config.put("persistentCache", persistentCache);
        config.put("journalCache", "-");
        assertCachePath(expectedPath, config);
    }

    private void assertJournalCachePath(String expectedPath,
                                        String journalCache,
                                        String repoHome) {
        Map<String, Object> config = newConfig(repoHome);
        config.put("journalCache", journalCache);
        config.put("persistentCache", "-");
        assertCachePath(expectedPath, config);
    }

    private void assertCachePath(String expectedPath,
                                 Map<String, Object> config) {
        assertFalse(new File(expectedPath).exists());

        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());

        assertNotNull(context.getService(NodeStore.class));
        // must exist after service was activated
        assertTrue(new File(expectedPath).exists());
    }

    private void assertNoPersistentCachePath(String unexpectedPath,
                                             String persistentCache,
                                             String repoHome) {
        Map<String, Object> config = newConfig(repoHome);
        config.put("persistentCache", persistentCache);
        assertNoCachePath(unexpectedPath, config);
    }

    private void assertNoJournalCachePath(String unexpectedPath,
                                          String journalCache,
                                          String repoHome) {
        Map<String, Object> config = newConfig(repoHome);
        config.put("journalCache", journalCache);
        assertNoCachePath(unexpectedPath, config);
    }

    private void assertNoCachePath(String unexpectedPath,
                                   Map<String, Object> config) {
        assertFalse(new File(unexpectedPath).exists());
        MockOsgi.setConfigForPid(context.bundleContext(), PID, config);
        MockOsgi.activate(service, context.bundleContext());

        assertNotNull(context.getService(NodeStore.class));
        // must not exist after service was activated
        assertFalse(new File(unexpectedPath).exists());
        // also assert there is no dash directory
        // the dash character is used to disable a persistent cache
        assertFalse(new File(repoHome, "-").exists());
    }

    private Map<String, Object> newConfig(String repoHome) {
        Map<String, Object> config = Maps.newHashMap();
        config.put("repository.home", repoHome);
        config.put("db", MongoUtils.DB);
        config.put("mongouri", MongoUtils.URL);
        return config;
    }
}
