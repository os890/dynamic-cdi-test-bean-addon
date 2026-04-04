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

import static org.junit.jupiter.api.Assertions.assertEquals;

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

/**
 * Isolation test: activates {@link FormalGreeting} for {@link Greeting}.
 * Runs alongside {@link CustomGreetingIsolationTest} which activates
 * {@link CustomGreeting} for the same type — proving that the JUnit
 * extension correctly scopes alternatives per test class.
 */
@EnableTestBeans
@TestBean(bean = FormalGreeting.class)
class FormalGreetingIsolationTest {

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
    @DisplayName("FormalGreeting is active — returns 'Good day, ...'")
    void formalGreetingIsActive() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        assertEquals("Good day, world.", consumer.getGreeting().greet("world"));
    }
}
