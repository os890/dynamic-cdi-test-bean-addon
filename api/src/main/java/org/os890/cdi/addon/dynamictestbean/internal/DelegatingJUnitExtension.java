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

import java.util.ServiceLoader;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import org.os890.cdi.addon.dynamictestbean.spi.TestBeanContainerManager;

/**
 * Thin JUnit 5 extension that delegates all lifecycle callbacks to a
 * {@link TestBeanContainerManager} discovered via {@link ServiceLoader}.
 *
 * <p><strong>This is an internal class.</strong> Users should annotate
 * their test class with
 * {@link org.os890.cdi.addon.dynamictestbean.EnableTestBeans @EnableTestBeans}
 * which embeds this extension via {@code @ExtendWith}.</p>
 */
public class DelegatingJUnitExtension
        implements BeforeAllCallback, AfterAllCallback,
                   BeforeEachCallback, AfterEachCallback,
                   TestInstancePostProcessor {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(DelegatingJUnitExtension.class);

    private static final String KEY_DELEGATE = "delegate";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        TestBeanContainerManager delegate = resolveDelegate();
        context.getStore(NS).put(KEY_DELEGATE, delegate);
        delegate.beforeAll(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        TestBeanContainerManager delegate = getDelegate(context);
        if (delegate != null) {
            delegate.beforeEach(context);
        }
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        TestBeanContainerManager delegate = getDelegate(context);
        if (delegate != null) {
            delegate.postProcessTestInstance(testInstance, context);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        TestBeanContainerManager delegate = getDelegate(context);
        if (delegate != null) {
            delegate.afterEach(context);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        TestBeanContainerManager delegate = context.getStore(NS)
                .remove(KEY_DELEGATE, TestBeanContainerManager.class);
        if (delegate != null) {
            delegate.afterAll(context);
        }
    }

    private static TestBeanContainerManager resolveDelegate() {
        ServiceLoader<TestBeanContainerManager> loader =
                ServiceLoader.load(TestBeanContainerManager.class);

        TestBeanContainerManager found = null;
        try {
            for (TestBeanContainerManager manager : loader) {
                if (found != null) {
                    throw new IllegalStateException(
                            "Multiple TestBeanContainerManager implementations found on the classpath: "
                                    + found.getClass().getName() + " and "
                                    + manager.getClass().getName()
                                    + ". Only one implementation module (cdi or quarkus) "
                                    + "should be present.");
                }
                found = manager;
            }
        } catch (java.util.ServiceConfigurationError e) {
            throw new IllegalStateException(
                    "Failed to load TestBeanContainerManager implementation. "
                            + "Check that the implementation module (cdi or quarkus) "
                            + "and all its dependencies are on the classpath.", e);
        }
        if (found == null) {
            throw new IllegalStateException(
                    "No TestBeanContainerManager implementation found on the classpath. "
                            + "Add either dynamic-cdi-test-bean-addon-cdi or "
                            + "dynamic-cdi-test-bean-addon-quarkus as a test dependency.");
        }
        return found;
    }

    private static TestBeanContainerManager getDelegate(ExtensionContext context) {
        return context.getStore(NS).get(KEY_DELEGATE, TestBeanContainerManager.class);
    }
}
