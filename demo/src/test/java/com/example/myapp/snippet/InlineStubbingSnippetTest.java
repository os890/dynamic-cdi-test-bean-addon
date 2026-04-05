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

package com.example.myapp.snippet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;

import com.example.myapp.service.Greeting;
import com.example.myapp.service.GreetingConsumer;

/**
 * Verifies testbean.html snippet 4: inline field with in-method stubbing.
 * Matches the doc example where Mockito.when() is called inside the test.
 */
@EnableTestBeans
class InlineStubbingSnippetTest {

    @TestBean
    private static Greeting greeting = Mockito.mock(Greeting.class);

    @Inject
    GreetingConsumer consumer;

    @Test
    @DisplayName("docs: inline @TestBean with in-method stubbing")
    void stubbedMockIsInjected() {
        Mockito.when(greeting.greet("world")).thenReturn("Stubbed!");

        assertEquals("Stubbed!", consumer.getGreeting().greet("world"));
        assertNull(consumer.getGreeting().greet("other"));
    }
}
