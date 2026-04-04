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
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;
import org.os890.cdi.addon.dynamictestbean.usecase.Greeting;
import org.os890.cdi.addon.dynamictestbean.usecase.GreetingConsumer;

/**
 * Tests the inline {@code @Produces @TestBean} field pattern with
 * Mockito stubbing applied in the test method.
 */
@EnableTestBeans
class InlineProducerWithStubbingTest {

    @Produces
    @TestBean
    private static Greeting greeting = Mockito.mock(Greeting.class);

    @Inject
    GreetingConsumer consumer;

    @Test
    @DisplayName("Stubbed Mockito mock is injected as CDI bean")
    void stubbedMockIsInjected() {
        Mockito.when(greeting.greet("world")).thenReturn("Stubbed!");

        assertEquals("Stubbed!", consumer.getGreeting().greet("world"));
        assertNull(consumer.getGreeting().greet("other"));
    }

    @Test
    @DisplayName("Mockito verify works on the injected mock")
    void verifyWorksOnInjectedMock() {
        consumer.getGreeting().greet("test");

        Mockito.verify(greeting).greet("test");
    }
}
