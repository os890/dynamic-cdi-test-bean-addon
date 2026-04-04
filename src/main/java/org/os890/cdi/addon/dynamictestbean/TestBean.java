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

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Declares an {@code @Alternative} CDI bean to activate for the current
 * test. Use exactly one of {@link #bean()} or {@link #beanProducer()}.
 *
 * <p>Must be used together with {@link EnableTestBeans} on the test class.</p>
 *
 * <h2>Example: direct replacement</h2>
 * <pre>{@code
 * @EnableTestBeans
 * @TestBean(bean = CustomGreeting.class)
 * class MyTest { ... }
 * }</pre>
 *
 * <h2>Example: producer-based replacement</h2>
 * <pre>{@code
 * @EnableTestBeans
 * @TestBean(beanProducer = TestProducers.class)
 * class MyTest { ... }
 * }</pre>
 *
 * @see EnableTestBeans
 * @see TestBeans
 */
@Retention(RUNTIME)
@Target(TYPE)
@Repeatable(TestBeans.class)
public @interface TestBean {

    /**
     * The {@code @Alternative} bean class to enable as a direct replacement.
     * Mutually exclusive with {@link #beanProducer()}.
     *
     * @return the alternative class, or {@code void.class} if not used
     */
    Class<?> bean() default void.class;

    /**
     * The {@code @Alternative} producer bean class to enable. The class
     * should contain {@code @Produces} methods or fields.
     * Mutually exclusive with {@link #bean()}.
     *
     * @return the alternative producer class, or {@code void.class} if not used
     */
    Class<?> beanProducer() default void.class;
}
