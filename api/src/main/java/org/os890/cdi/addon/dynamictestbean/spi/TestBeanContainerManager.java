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

package org.os890.cdi.addon.dynamictestbean.spi;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * SPI for CDI container lifecycle management in tests.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}
 * and registered in
 * {@code META-INF/services/org.os890.cdi.addon.dynamictestbean.spi.TestBeanContainerManager}.</p>
 *
 * <p>Exactly one implementation must be on the classpath. The delegating
 * JUnit extension in the API module forwards all lifecycle callbacks to
 * the discovered implementation.</p>
 *
 * <p><strong>Thread safety:</strong> Implementations use static mutable
 * state and are not thread-safe. Parallel test execution across test
 * classes (e.g., Maven Surefire {@code <parallel>classes</parallel>})
 * is not supported.</p>
 *
 * <p><strong>Callback order:</strong>
 * {@code beforeAll} → ({@code beforeEach} → {@code postProcessTestInstance}
 * → test → {@code afterEach})* → {@code afterAll}.
 * Each callback is invoked exactly once per test class (beforeAll/afterAll)
 * or per test method (the others).</p>
 */
public interface TestBeanContainerManager {

    /**
     * Called once before all tests in the class.
     * Implementations should boot the CDI container here.
     *
     * @param context the JUnit extension context
     * @throws Exception if container startup fails
     */
    void beforeAll(ExtensionContext context) throws Exception;

    /**
     * Called before each test method.
     * Implementations should activate the request scope here.
     *
     * @param context the JUnit extension context
     * @throws Exception if scope activation fails
     */
    void beforeEach(ExtensionContext context) throws Exception;

    /**
     * Called after the test instance is created.
     * Implementations should inject {@code @Inject} fields here.
     *
     * @param testInstance the test instance to inject into
     * @param context the JUnit extension context
     * @throws Exception if injection fails
     */
    void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception;

    /**
     * Called after each test method.
     * Implementations should deactivate the request scope here.
     *
     * @param context the JUnit extension context
     * @throws Exception if scope deactivation fails
     */
    void afterEach(ExtensionContext context) throws Exception;

    /**
     * Called once after all tests in the class.
     * Implementations should shut down the CDI container here.
     *
     * @param context the JUnit extension context
     * @throws Exception if container shutdown fails
     */
    void afterAll(ExtensionContext context) throws Exception;
}
