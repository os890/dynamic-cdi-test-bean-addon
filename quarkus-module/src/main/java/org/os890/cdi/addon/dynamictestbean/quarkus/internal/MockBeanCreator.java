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

package org.os890.cdi.addon.dynamictestbean.quarkus.internal;

import java.util.logging.Logger;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

import org.mockito.Mockito;

/**
 * {@link BeanCreator} that creates a Mockito mock for the implementation class
 * passed as a parameter.
 *
 * <p>The class can be provided either as a {@code ClassInfo} via the
 * {@code "implementationClass"} param (resolved by ArC to a {@code Class<?>}
 * at runtime) or as a class name string via {@code "implementationClassName"}
 * for JDK types that are not in the Jandex index.</p>
 */
public class MockBeanCreator implements BeanCreator<Object> {

    private static final Logger LOG = Logger.getLogger(MockBeanCreator.class.getName());

    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        Class<?> implementationClass = resolveClass(context);
        LOG.info("[DynamicTestBean] Mock created for: " + implementationClass.getName());
        try {
            return Mockito.mock(implementationClass);
        } catch (Exception e) {
            // JDK type that Mockito cannot subclass — return null,
            // consistent with Mockito's default answer for reference types
            return null;
        }
    }

    private static Class<?> resolveClass(SyntheticCreationalContext<Object> context) {
        Object implClass = context.getParams().get("implementationClass");
        if (implClass instanceof Class<?> cls) {
            return cls;
        }
        // Fallback: class name string for JDK types not in Jandex index
        String className = (String) context.getParams().get("implementationClassName");
        if (className != null) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot load mock target class: " + className, e);
            }
        }
        throw new IllegalStateException(
                "[DynamicTestBean] No implementationClass or implementationClassName param found");
    }
}
