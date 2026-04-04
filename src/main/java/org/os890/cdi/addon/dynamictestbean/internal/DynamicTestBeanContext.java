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
 * Holds the currently active test class and its configuration for the
 * CDI extension and JUnit extension.
 *
 * <p>Set automatically by {@link DynamicTestBeanJUnitExtension}.
 * Internal — not part of the public API.</p>
 */
public final class DynamicTestBeanContext {

    private static volatile Class<?> activeTestClass;
    private static volatile boolean addTestClass = true;
    private static volatile boolean limitToTestBeans;
    private static volatile boolean manageContainer = true;

    private DynamicTestBeanContext() {
    }

    /** Sets the active test class. */
    public static void setActiveTestClass(Class<?> testClass) {
        activeTestClass = testClass;
    }

    /** Returns the active test class, or {@code null} if none. */
    public static Class<?> getActiveTestClass() {
        return activeTestClass;
    }

    /** Sets whether the test class should be added as a CDI bean. */
    public static void setAddTestClass(boolean value) {
        addTestClass = value;
    }

    /** Returns whether the test class should be added as a CDI bean. */
    public static boolean isAddTestClass() {
        return addTestClass;
    }

    /** Sets whether to veto all beans except those declared via @TestBean. */
    public static void setLimitToTestBeans(boolean value) {
        limitToTestBeans = value;
    }

    /** Returns whether whitelist mode is active. */
    public static boolean isLimitToTestBeans() {
        return limitToTestBeans;
    }

    /** Sets whether the extension manages the CDI container lifecycle. */
    public static void setManageContainer(boolean value) {
        manageContainer = value;
    }

    /** Returns whether the extension manages the CDI container lifecycle. */
    public static boolean isManageContainer() {
        return manageContainer;
    }

    /** Resets all state. */
    public static void reset() {
        activeTestClass = null;
        addTestClass = true;
        limitToTestBeans = false;
        manageContainer = true;
    }
}
