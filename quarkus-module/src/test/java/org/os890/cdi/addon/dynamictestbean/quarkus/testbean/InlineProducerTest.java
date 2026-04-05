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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * Tests the inline {@code @Produces @TestBean} field pattern.
 * The static field provides a Mockito mock with stubbing as the
 * CDI bean for {@link Greeting}.
 */
@EnableTestBeans
class InlineProducerTest {

    @Produces
    @TestBean
    private static Greeting greetingMock = createGreetingMock();

    @Inject
    GreetingConsumer greetingConsumer;

    private static Greeting createGreetingMock() {
        Greeting mock = Mockito.mock(Greeting.class);
        Mockito.when(mock.greet("world")).thenReturn("Inline mock says hi!");
        return mock;
    }

    @Test
    @DisplayName("Inline @TestBean field provides the mock as CDI bean")
    void inlineFieldProvidesBean() {
        assertNotNull(greetingConsumer);
        Greeting greeting = greetingConsumer.getGreeting();
        assertNotNull(greeting);
        assertEquals("Inline mock says hi!", greeting.greet("world"));
    }
}
