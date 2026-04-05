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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;
import org.os890.cdi.addon.dynamictestbean.usecase.Greeting;
import org.os890.cdi.addon.dynamictestbean.usecase.GreetingConsumer;

/**
 * Tests that @TestBean on a static field works WITHOUT @Produces.
 * The extension registers the field value as a synthetic bean directly.
 */
@EnableTestBeans
class InlineProducerWithoutProducesTest {

    @TestBean
    private static Greeting greetingMock = createGreetingMock();

    @Inject
    GreetingConsumer greetingConsumer;

    private static Greeting createGreetingMock() {
        Greeting mock = Mockito.mock(Greeting.class);
        Mockito.when(mock.greet("world")).thenReturn("No @Produces needed!");
        return mock;
    }

    @Test
    @DisplayName("@TestBean field without @Produces still provides the bean")
    void inlineFieldWithoutProducesWorks() {
        assertNotNull(greetingConsumer);
        Greeting greeting = greetingConsumer.getGreeting();
        assertNotNull(greeting);
        assertEquals("No @Produces needed!", greeting.greet("world"));
    }
}
