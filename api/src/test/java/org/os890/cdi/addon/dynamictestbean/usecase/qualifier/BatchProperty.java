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

package org.os890.cdi.addon.dynamictestbean.usecase.qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

/**
 * CDI qualifier that mimics {@code jakarta.batch.api.BatchProperty}.
 *
 * <p>In production, a producer method with an
 * {@link jakarta.enterprise.inject.spi.InjectionPoint} parameter would
 * inspect the {@link #name()} attribute (or the target field name) and
 * produce the corresponding configuration value. Because the producer
 * is typically absent from the test classpath, the extension must
 * register a mock bean for the qualified JDK type.</p>
 *
 * <p>The {@code name} member is {@link Nonbinding} — all
 * {@code @BatchProperty} injection points resolve to the same bean,
 * regardless of the name value.</p>
 */
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER, TYPE})
public @interface BatchProperty {

    @Nonbinding
    String name() default "";
}
