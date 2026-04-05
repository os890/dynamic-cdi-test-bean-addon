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

/**
 * Public API of the Dynamic CDI Test Bean Addon.
 *
 * <p>This package contains the annotations that consumers use:</p>
 * <ul>
 *   <li>{@link org.os890.cdi.addon.dynamictestbean.EnableTestBeans} —
 *       enables CDI integration for the test class (delegates to the
 *       implementation module on the classpath via SPI)</li>
 *   <li>{@link org.os890.cdi.addon.dynamictestbean.TestBean} —
 *       declares an {@code @Alternative} bean or producer to activate,
 *       or marks a static field as an inline producer</li>
 *   <li>{@link org.os890.cdi.addon.dynamictestbean.TestBeans} —
 *       container for repeatable {@code @TestBean}</li>
 * </ul>
 *
 * @see org.os890.cdi.addon.dynamictestbean.spi.TestBeanContainerManager
 */
package org.os890.cdi.addon.dynamictestbean;
