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

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.InjectionTarget;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;

/**
 * Internal JUnit extension that sets the active test class before
 * container bootstrap and injects CDI beans into the test instance.
 *
 * <p><strong>This is an internal class.</strong> Users should annotate
 * their test class with
 * {@link EnableTestBeans @EnableTestBeans}
 * which embeds this extension via {@code @ExtendWith}.</p>
 */
public class DynamicTestBeanJUnitExtension
        implements BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor {

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getTestClass().ifPresent(testClass -> {
            DynamicTestBeanContext.setActiveTestClass(testClass);
            EnableTestBeans config = testClass.getAnnotation(EnableTestBeans.class);
            if (config != null) {
                DynamicTestBeanContext.setAddTestClass(config.addTestClass());
                DynamicTestBeanContext.setLimitToTestBeans(config.limitToTestBeans());
            }
        });
    }

    @Override
    public void afterAll(ExtensionContext context) {
        DynamicTestBeanContext.reset();
    }

    /**
     * Injects CDI beans into {@code @Inject} fields on the test instance.
     * Only runs if {@code addTestClass = true} (default) and a CDI
     * container is running.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        if (!DynamicTestBeanContext.isAddTestClass()) {
            return;
        }

        try {
            BeanManager bm = CDI.current().getBeanManager();
            Class<Object> testClass = (Class<Object>) testInstance.getClass();
            AnnotatedType<Object> annotatedType = bm.createAnnotatedType(testClass);
            InjectionTarget<Object> injectionTarget =
                    bm.getInjectionTargetFactory(annotatedType).createInjectionTarget(null);
            CreationalContext<Object> ctx = bm.createCreationalContext(null);
            injectionTarget.inject(testInstance, ctx);
        } catch (IllegalStateException e) {
            // No CDI container running — skip injection
        }
    }
}
