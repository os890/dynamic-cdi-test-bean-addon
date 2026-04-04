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

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.testbean.meta.StandardMocks;
import org.os890.cdi.addon.dynamictestbean.usecase.GreetingConsumer;

/**
 * Tests a level 2 meta-annotation ({@link StandardMocks}) that composes
 * {@code @GreetingMocks} and {@code @NotificationMocks}.
 */
@EnableTestBeans
@StandardMocks
class MetaAnnotationNestedTest {

    @Inject
    GreetingConsumer consumer;

    @Test
    @DisplayName("Level 2: @StandardMocks activates CustomGreeting from @GreetingMocks")
    void nestedGreetingWorks() {
        assertNotNull(consumer);
        assertEquals("Hello, world!", consumer.getGreeting().greet("world"));
    }

    @Test
    @DisplayName("Level 2: @StandardMocks activates CustomNotificationSender from @NotificationMocks")
    void nestedNotificationWorks() {
        assertNotNull(consumer.getNotificationSender());
        assertDoesNotThrow(() -> consumer.getNotificationSender().send("test"));
    }
}
