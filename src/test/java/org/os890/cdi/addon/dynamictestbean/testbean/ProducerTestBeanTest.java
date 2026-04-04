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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;
import org.os890.cdi.addon.dynamictestbean.usecase.AuditConsumer;

/**
 * Tests {@code @TestBean(beanProducer = ...)} with a producer bean.
 *
 * <p>{@link TestProducers} is an {@code @Alternative @ApplicationScoped}
 * bean with a {@code @Produces} method for
 * {@link org.os890.cdi.addon.dynamictestbean.usecase.AuditService}.
 * This test activates it via {@code @TestBean}. Other unselected
 * {@code @Alternative} beans are vetoed by the extension.</p>
 */
@EnableTestBeans
@TestBean(beanProducer = TestProducers.class)
class ProducerTestBeanTest {

    @Test
    @DisplayName("Producer method provides custom AuditService")
    void producerProvidesCustomAuditService() {
        AuditConsumer consumer = CDI.current().select(AuditConsumer.class).get();

        assertNotNull(consumer.getAuditService());
        assertDoesNotThrow(() -> consumer.getAuditService().audit("login"));
    }

    @Test
    @DisplayName("Produced bean is functional, not a Mockito mock")
    void producedBeanIsFunctional() {
        AuditConsumer consumer = CDI.current().select(AuditConsumer.class).get();

        assertDoesNotThrow(() -> consumer.getAuditService().audit("first"));
        assertDoesNotThrow(() -> consumer.getAuditService().audit("second"));
    }
}
