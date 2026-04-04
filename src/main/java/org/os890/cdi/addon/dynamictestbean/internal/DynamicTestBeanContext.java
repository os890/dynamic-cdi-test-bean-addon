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

/**
 * Holds the currently active test class for the
 * {@link org.os890.cdi.addon.dynamictestbean.internal.DynamicTestBeanExtension}.
 *
 * <p>When set, the CDI extension only processes {@link TestBean}
 * annotations from this specific class, preventing conflicts between
 * test classes that activate different {@code @Alternative} beans for
 * the same type.</p>
 *
 * <p>This is set automatically by {@link DynamicTestBeanJUnitExtension}.
 * Manual usage is not required.</p>
 */
public final class DynamicTestBeanContext {

    private static volatile Class<?> activeTestClass;

    private DynamicTestBeanContext() {
    }

    /**
     * Sets the active test class. Only {@link TestBean} annotations
     * on this class will be processed by the CDI extension.
     *
     * @param testClass the active test class, or {@code null} to clear
     */
    public static void setActiveTestClass(Class<?> testClass) {
        activeTestClass = testClass;
    }

    /**
     * Returns the currently active test class, or {@code null} if none
     * is set (in which case all {@link TestBean} annotations are processed).
     *
     * @return the active test class
     */
    public static Class<?> getActiveTestClass() {
        return activeTestClass;
    }
}
