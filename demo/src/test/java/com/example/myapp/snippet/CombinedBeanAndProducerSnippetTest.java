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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;

import com.example.myapp.service.AuditConsumer;
import com.example.myapp.service.GreetingConsumer;

/**
 * Verifies testbean-advanced.html snippet 2: combined bean + beanProducer.
 */
@EnableTestBeans
@TestBean(bean = CustomGreeting.class)
@TestBean(beanProducer = TestProducers.class)
class CombinedBeanAndProducerSnippetTest {

    @Inject
    GreetingConsumer greetingConsumer;

    @Inject
    AuditConsumer auditConsumer;

    @Test
    @DisplayName("docs: @TestBean(bean=...) + @TestBean(beanProducer=...) combined")
    void bothBeanAndProducerActive() {
        assertEquals("Hello, world!", greetingConsumer.getGreeting().greet("world"));
        assertNotNull(auditConsumer.getAuditService());
        assertDoesNotThrow(() -> auditConsumer.getAuditService().audit("test"));
    }
}
