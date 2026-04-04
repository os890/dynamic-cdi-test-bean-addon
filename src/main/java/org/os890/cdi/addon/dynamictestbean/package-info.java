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
 *       enables test-class-scoped alternative activation (goes on the test class)</li>
 *   <li>{@link org.os890.cdi.addon.dynamictestbean.TestBean} —
 *       declares an {@code @Alternative} bean or producer to activate</li>
 *   <li>{@link org.os890.cdi.addon.dynamictestbean.TestBeans} —
 *       container for repeatable {@code @TestBean}</li>
 * </ul>
 *
 * <p>The extension itself activates automatically via SPI — no direct
 * interaction with internal classes is needed.</p>
 *
 * @see org.os890.cdi.addon.dynamictestbean.internal
 */
package org.os890.cdi.addon.dynamictestbean;
