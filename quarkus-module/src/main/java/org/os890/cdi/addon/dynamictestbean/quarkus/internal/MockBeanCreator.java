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
 */
public class MockBeanCreator implements BeanCreator<Object> {

    private static final Logger LOG = Logger.getLogger(MockBeanCreator.class.getName());

    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        Class<?> implementationClass = (Class<?>) context.getParams().get("implementationClass");
        LOG.info("[DynamicTestBean] Mock created for: " + implementationClass.getName());
        return Mockito.mock(implementationClass);
    }
}
