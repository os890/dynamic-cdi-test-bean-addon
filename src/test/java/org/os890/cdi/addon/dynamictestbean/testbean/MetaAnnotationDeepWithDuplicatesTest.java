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

package org.os890.cdi.addon.dynamictestbean.testbean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;
import org.os890.cdi.addon.dynamictestbean.testbean.meta.FullTestSetup;
import org.os890.cdi.addon.dynamictestbean.usecase.AuditConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.GreetingConsumer;

/**
 * Tests a deeply nested meta-annotation ({@link FullTestSetup}) that:
 * <ul>
 *   <li>Includes {@code @StandardMocks} (level 2) which includes
 *       {@code @GreetingMocks} + {@code @NotificationMocks} (level 1)</li>
 *   <li>Adds {@code @TestBean(beanProducer = TestProducers.class)}</li>
 *   <li>Redundantly declares {@code CustomGreeting} (already in
 *       {@code @GreetingMocks}) — tests deduplication</li>
 * </ul>
 *
 * <p>Also adds a direct {@code @TestBean} on the test class that
 * duplicates {@code CustomGreeting} again — triple redundancy test.</p>
 */
@EnableTestBeans
@FullTestSetup
@TestBean(bean = CustomGreeting.class)
class MetaAnnotationDeepWithDuplicatesTest {

    @Inject
    GreetingConsumer greetingConsumer;

    @Inject
    AuditConsumer auditConsumer;

    private static SeContainer container;
    private static RequestContextController rc;

    @BeforeAll
    static void boot() {
        container = SeContainerInitializer.newInstance().initialize();
        rc = container.select(RequestContextController.class).get();
        rc.activate();
    }

    @AfterAll
    static void shutdown() {
        if (rc != null) {
            rc.deactivate();
        }
        if (container != null && container.isRunning()) {
            container.close();
        }
    }

    @Test
    @DisplayName("Deep nesting: CustomGreeting from @GreetingMocks (via @StandardMocks via @FullTestSetup)")
    void deeplyNestedGreetingWorks() {
        assertNotNull(greetingConsumer);
        assertEquals("Hello, world!", greetingConsumer.getGreeting().greet("world"));
    }

    @Test
    @DisplayName("Deep nesting: CustomNotificationSender from @NotificationMocks (via @StandardMocks)")
    void deeplyNestedNotificationWorks() {
        assertDoesNotThrow(() -> greetingConsumer.getNotificationSender().send("test"));
    }

    @Test
    @DisplayName("Deep nesting: TestProducers from @FullTestSetup provides AuditService")
    void deeplyNestedProducerWorks() {
        assertNotNull(auditConsumer);
        assertDoesNotThrow(() -> auditConsumer.getAuditService().audit("test"));
    }

    @Test
    @DisplayName("Triple-redundant CustomGreeting (meta + meta + direct) does not cause ambiguity")
    void duplicateDeclarationsDoNotConflict() {
        // If deduplication failed, CustomGreeting would be registered
        // multiple times and cause AmbiguousResolutionException
        assertEquals("Hello, world!", greetingConsumer.getGreeting().greet("world"));
    }
}
