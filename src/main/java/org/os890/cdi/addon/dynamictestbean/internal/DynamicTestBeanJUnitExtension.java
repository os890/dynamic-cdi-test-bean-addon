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

import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.InjectionTarget;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;

/**
 * Internal JUnit extension that manages the CDI SE container lifecycle
 * and injects CDI beans into the test instance.
 *
 * <p><strong>This is an internal class.</strong> Users should annotate
 * their test class with
 * {@link EnableTestBeans @EnableTestBeans}
 * which embeds this extension via {@code @ExtendWith}.</p>
 *
 * <p>When {@code manageContainer = true} (default):</p>
 * <ul>
 *   <li>{@code beforeAll} — sets active test class, starts the container</li>
 *   <li>{@code beforeEach} — activates the request scope</li>
 *   <li>{@code postProcessTestInstance} — injects {@code @Inject} fields</li>
 *   <li>{@code afterEach} — deactivates the request scope</li>
 *   <li>{@code afterAll} — shuts down the container, resets state</li>
 * </ul>
 */
public class DynamicTestBeanJUnitExtension
        implements BeforeAllCallback, AfterAllCallback,
                   BeforeEachCallback, AfterEachCallback,
                   TestInstancePostProcessor {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(DynamicTestBeanJUnitExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getTestClass().ifPresent(testClass -> {
            EnableTestBeans config = testClass.getAnnotation(EnableTestBeans.class);
            DynamicTestBeanContext.setActiveTestClass(testClass);
            if (config != null) {
                DynamicTestBeanContext.setAddTestClass(config.addTestClass());
                DynamicTestBeanContext.setLimitToTestBeans(config.limitToTestBeans());
                DynamicTestBeanContext.setManageContainer(config.manageContainer());
            }

            if (DynamicTestBeanContext.isManageContainer()) {
                SeContainer container = SeContainerInitializer.newInstance().initialize();
                context.getStore(NS).put("container", container);
            }
        });
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (!DynamicTestBeanContext.isManageContainer()) {
            return;
        }
        try {
            RequestContextController rc = CDI.current()
                    .select(RequestContextController.class).get();
            rc.activate();
            context.getStore(NS).put("requestContext", rc);
        } catch (IllegalStateException e) {
            // No container running
        }
    }

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

    @Override
    public void afterEach(ExtensionContext context) {
        RequestContextController rc = context.getStore(NS)
                .remove("requestContext", RequestContextController.class);
        if (rc != null) {
            rc.deactivate();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        SeContainer container = context.getStore(NS)
                .remove("container", SeContainer.class);
        if (container != null && container.isRunning()) {
            container.close();
        }
        DynamicTestBeanContext.reset();
    }
}
