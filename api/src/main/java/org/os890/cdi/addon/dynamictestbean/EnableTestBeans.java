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

package org.os890.cdi.addon.dynamictestbean;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;
import org.os890.cdi.addon.dynamictestbean.internal.DelegatingJUnitExtension;

/**
 * Enables CDI integration for the test class.
 *
 * <p>The actual container management is provided by the implementation
 * module on the classpath (CDI SE or Quarkus/ArC), discovered via
 * {@link java.util.ServiceLoader}.</p>
 *
 * <p><strong>Quarkus compatibility:</strong> If the test class is also
 * annotated with {@code @QuarkusTest} or {@code @QuarkusComponentTest},
 * container lifecycle management is skipped automatically &mdash; the
 * Quarkus test framework handles the container in that case.</p>
 *
 * <p>By default, the test class is registered as a {@code @Singleton}
 * CDI bean, and {@code @Inject} fields on the test instance are
 * injected after container bootstrap. Use {@link #addTestClass()} to
 * disable this if needed.</p>
 *
 * <p>When combined with {@link TestBean} annotations, the extension
 * also activates the referenced {@code @Alternative} beans and vetoes
 * unselected alternatives.</p>
 *
 * <h2>Example: basic test</h2>
 * <pre>{@code
 * @EnableTestBeans
 * class MyTest {
 *
 *     @Inject
 *     MyService service;  // injected by CDI
 *
 *     @Test
 *     void testService() {
 *         assertNotNull(service);
 *     }
 * }
 * }</pre>
 *
 * <h2>Example: with custom alternatives</h2>
 * <pre>{@code
 * @EnableTestBeans
 * @TestBean(bean = CustomGreeting.class)
 * class MyTest {
 *
 *     @Inject
 *     Greeting greeting;  // CustomGreeting, not a mock
 * }
 * }</pre>
 *
 * <h2>Example: whitelist mode</h2>
 * <pre>{@code
 * @EnableTestBeans(limitToTestBeans = true)
 * @TestBean(bean = CustomGreeting.class)
 * class MyTest { ... }
 * }</pre>
 *
 * @see TestBean
 * @see org.os890.cdi.addon.dynamictestbean.spi.TestBeanContainerManager
 */
@ExtendWith(DelegatingJUnitExtension.class)
@Retention(RUNTIME)
@Target(TYPE)
public @interface EnableTestBeans {

    /**
     * Whether to register the test class as a {@code @Singleton} CDI
     * bean and inject {@code @Inject} fields on the test instance.
     * Defaults to {@code true}.
     *
     * @return {@code true} to enable test class injection
     */
    boolean addTestClass() default true;

    /**
     * When {@code true}, all discovered beans are vetoed except those
     * explicitly declared via {@link TestBean}. No auto-mocking occurs.
     * Only the whitelisted beans and the test class itself survive.
     * Defaults to {@code false}.
     *
     * @return {@code true} to enable whitelist mode
     */
    boolean limitToTestBeans() default false;

    /**
     * When {@code true}, the extension manages the CDI container
     * lifecycle automatically. Defaults to {@code true}.
     * Set to {@code false} to manage the container manually.
     *
     * <p>Note: the Quarkus/ArC implementation always manages the
     * container — this attribute is ignored.</p>
     *
     * @return {@code true} to enable automatic container management
     */
    boolean manageContainer() default true;
}
