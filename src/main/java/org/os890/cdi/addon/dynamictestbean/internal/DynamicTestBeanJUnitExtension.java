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

package org.os890.cdi.addon.dynamictestbean.internal;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Internal JUnit extension that sets the active test class before
 * container bootstrap, ensuring the CDI extension only processes
 * {@code @TestBean} annotations from the current test class and
 * vetoes unselected {@code @Alternative} beans.
 *
 * <p><strong>This is an internal class.</strong> Users should annotate
 * their test class with
 * {@link org.os890.cdi.addon.dynamictestbean.EnableTestBeans @EnableTestBeans}
 * which embeds this extension via {@code @ExtendWith}.</p>
 *
 * <p>Calls {@link DynamicTestBeanContext#setActiveTestClass} before
 * {@code @BeforeAll} and clears it after {@code @AfterAll}.</p>
 */
public class DynamicTestBeanJUnitExtension implements BeforeAllCallback, AfterAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getTestClass().ifPresent(DynamicTestBeanContext::setActiveTestClass);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        DynamicTestBeanContext.setActiveTestClass(null);
    }
}
