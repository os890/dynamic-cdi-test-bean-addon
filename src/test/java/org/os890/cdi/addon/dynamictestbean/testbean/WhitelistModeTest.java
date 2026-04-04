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
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.CDI;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;
import org.os890.cdi.addon.dynamictestbean.usecase.Greeting;
import org.os890.cdi.addon.dynamictestbean.usecase.GreetingConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.SelfContainedService;

/**
 * Tests {@code limitToTestBeans = true} — whitelist mode.
 * Only the explicitly declared {@link CustomGreeting} should be active.
 * All other application beans (like {@link SelfContainedService},
 * {@link GreetingConsumer}) are vetoed.
 */
@EnableTestBeans(limitToTestBeans = true)
@TestBean(bean = CustomGreeting.class)
class WhitelistModeTest {

    @Test
    @DisplayName("Whitelisted @TestBean alternative is available")
    void whitelistedBeanIsAvailable() {
        Set<Bean<?>> beans = CDI.current().getBeanManager().getBeans(Greeting.class);
        assertNotNull(beans);
        assertEquals(1, beans.size(), "Only CustomGreeting should satisfy Greeting");
    }

    @Test
    @DisplayName("Non-whitelisted beans are vetoed")
    void nonWhitelistedBeansAreVetoed() {
        Set<Bean<?>> greetingConsumerBeans =
                CDI.current().getBeanManager().getBeans(GreetingConsumer.class);
        assertTrue(greetingConsumerBeans.isEmpty(),
                "GreetingConsumer should be vetoed in whitelist mode");

        Set<Bean<?>> selfContainedBeans =
                CDI.current().getBeanManager().getBeans(SelfContainedService.class);
        assertTrue(selfContainedBeans.isEmpty(),
                "SelfContainedService should be vetoed in whitelist mode");
    }
}
