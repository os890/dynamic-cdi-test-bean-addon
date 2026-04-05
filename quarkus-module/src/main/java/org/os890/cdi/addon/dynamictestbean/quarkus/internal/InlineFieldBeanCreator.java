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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.logging.Logger;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

/**
 * {@link BeanCreator} that reads a static field value from a test class.
 * The declaring class name and field name are passed as parameters.
 */
public class InlineFieldBeanCreator implements BeanCreator<Object> {

    private static final Logger LOG = Logger.getLogger(InlineFieldBeanCreator.class.getName());

    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        String declaringClass = (String) context.getParams().get("declaringClass");
        String fieldName = (String) context.getParams().get("fieldName");
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = InlineFieldBeanCreator.class.getClassLoader();
            }
            Class<?> clazz = cl.loadClass(declaringClass);
            Field field = clazz.getDeclaredField(fieldName);
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalArgumentException(
                        "@TestBean field must be static: " + declaringClass + "." + fieldName);
            }
            field.setAccessible(true);
            Object value = field.get(null);
            LOG.info("[DynamicTestBean] Inline field value resolved: "
                    + clazz.getSimpleName() + "." + fieldName);
            return value;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read @TestBean field: "
                    + declaringClass + "." + fieldName, e);
        }
    }
}
