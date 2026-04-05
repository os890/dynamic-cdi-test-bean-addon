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

package org.os890.cdi.addon.dynamictestbean.quarkus.testbean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;
import org.os890.cdi.addon.dynamictestbean.usecase.GreetingConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.NotificationSender;

/**
 * Tests multiple {@link TestBean} annotations on a single test class.
 *
 * <p>Both {@link CustomGreeting} and {@link CustomNotificationSender} are
 * {@code @Alternative} beans activated together. Both injection points
 * in {@link GreetingConsumer} are satisfied by the custom replacements
 * instead of Mockito mocks.</p>
 */
@EnableTestBeans
@TestBean(bean = CustomGreeting.class)
@TestBean(bean = CustomNotificationSender.class)
class MultiTestBeanTest {

    @Test
    @DisplayName("Both @Alternative beans are activated simultaneously")
    void bothAlternativesAreActive() {
        GreetingConsumer consumer = CDI.current().select(GreetingConsumer.class).get();

        assertNotNull(consumer.getGreeting());
        assertNotNull(consumer.getNotificationSender());
    }

    @Test
    @DisplayName("CustomGreeting replacement returns real values")
    void greetingReplacementReturnsRealValues() {
        GreetingConsumer consumer = CDI.current().select(GreetingConsumer.class).get();

        assertEquals("Hello, world!", consumer.getGreeting().greet("world"));
    }

    @Test
    @DisplayName("CustomNotificationSender replacement records messages")
    void notificationReplacementRecordsMessages() {
        GreetingConsumer consumer = CDI.current().select(GreetingConsumer.class).get();
        NotificationSender sender = consumer.getNotificationSender();

        // Real NotificationSender throws — custom replacement records
        assertDoesNotThrow(() -> sender.send("test-message"));
    }
}
