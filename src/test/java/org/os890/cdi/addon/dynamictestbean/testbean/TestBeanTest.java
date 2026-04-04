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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;
import org.os890.cdi.addon.dynamictestbean.usecase.GreetingConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.NotificationSender;
import org.os890.cdi.addon.dynamictestbean.usecase.Greeting;

/**
 * Tests the {@link TestBean} annotation.
 *
 * <p>{@link CustomGreeting} is {@code @Alternative @ApplicationScoped}
 * (no {@code @Priority}). This test class activates it via
 * {@code @TestBean}, so it replaces the Mockito mock for
 * {@link Greeting}. Other unsatisfied types ({@link NotificationSender})
 * are still auto-mocked.</p>
 */
@EnableTestBeans
@TestBean(bean = CustomGreeting.class)
class TestBeanTest {

    private static SeContainer container;
    private static RequestContextController requestContext;

    @BeforeAll
    static void bootContainer() {
        container = SeContainerInitializer.newInstance().initialize();
        requestContext = container.select(RequestContextController.class).get();
        requestContext.activate();
    }

    @AfterAll
    static void shutdownContainer() {
        if (requestContext != null) {
            requestContext.deactivate();
        }
        if (container != null && container.isRunning()) {
            container.close();
        }
    }

    @Test
    @DisplayName("Replacement @Alternative satisfies the Greeting injection point")
    void replacementAlternativeIsInjected() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        Greeting greeting = consumer.getGreeting();

        assertNotNull(greeting);
        assertEquals("Hello, world!", greeting.greet("world"),
                "Should use CustomGreeting, not a Mockito mock");
    }

    @Test
    @DisplayName("Other unsatisfied types are still auto-mocked")
    void otherTypesStillMocked() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        NotificationSender sender = consumer.getNotificationSender();

        assertNotNull(sender);
        assertDoesNotThrow(() -> sender.send("test"));
    }

    @Test
    @DisplayName("Replacement returns real values, not Mockito defaults")
    void replacementReturnsRealValues() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        Greeting greeting = consumer.getGreeting();

        assertEquals("Hello, Alice!", greeting.greet("Alice"));
        assertEquals("Hello, Bob!", greeting.greet("Bob"));
    }
}
