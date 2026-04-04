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
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;
import org.os890.cdi.addon.dynamictestbean.usecase.GreetingConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.StatusConsumer;

/**
 * Tests that the veto logic correctly handles alternatives:
 * <ul>
 *   <li>{@code CustomGreeting} is selected via {@code @TestBean} —
 *       survives, replaces the mock for {@code Greeting}.</li>
 *   <li>{@code PriorityStatusService} has {@code @Priority(100)} and
 *       overlaps with nothing in the {@code @TestBean} set — survives.</li>
 *   <li>{@code FormalGreeting} is an unselected alternative whose type
 *       ({@code Greeting}) clashes with {@code CustomGreeting} — vetoed.</li>
 * </ul>
 */
@EnableTestBeans
@TestBean(bean = CustomGreeting.class)
class AlternativeVetoTest {

    @Inject
    GreetingConsumer greetingConsumer;

    @Inject
    StatusConsumer statusConsumer;

    @Test
    @DisplayName("Selected @TestBean alternative wins")
    void selectedAlternativeWins() {
        assertEquals("Hello, world!", greetingConsumer.getGreeting().greet("world"));
    }

    @Test
    @DisplayName("@Alternative @Priority for a non-overlapping type is NOT vetoed")
    void priorityAlternativeForDifferentTypeNotVetoed() {
        assertNotNull(statusConsumer.getStatusService());
        assertEquals("OK", statusConsumer.getStatusService().getStatus());
    }
}
