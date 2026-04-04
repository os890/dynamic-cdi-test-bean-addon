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
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Stereotype;

/**
 * CDI stereotype that defines {@link ApplicationScoped} scope.
 *
 * <p>Per the CDI spec, stereotypes may define a default scope but
 * <strong>must not</strong> declare qualifier annotations (other than
 * {@code @Named}). Qualifiers must be applied separately on the bean
 * class.</p>
 *
 * <p>Used in combination with {@link Premium} on a bean class to test
 * that the extension correctly handles beans whose scope comes from
 * a stereotype and whose qualifier is declared directly.</p>
 */
@Stereotype
@ApplicationScoped
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD})
public @interface Monitored {
}
