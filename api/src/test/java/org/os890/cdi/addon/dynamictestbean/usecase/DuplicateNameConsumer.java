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

package org.os890.cdi.addon.dynamictestbean.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Bean that injects two interfaces with the same simple name
 * ({@code DuplicateNameService}) from different packages. Tests that
 * the extension assigns unique mock bean names so both injection
 * points are satisfied without collisions.
 */
@ApplicationScoped
public class DuplicateNameConsumer {

    @Inject
    private org.os890.cdi.addon.dynamictestbean.usecase.pkga.DuplicateNameService serviceA;

    @Inject
    private org.os890.cdi.addon.dynamictestbean.usecase.pkgb.DuplicateNameService serviceB;

    public org.os890.cdi.addon.dynamictestbean.usecase.pkga.DuplicateNameService getServiceA() {
        return serviceA;
    }

    public org.os890.cdi.addon.dynamictestbean.usecase.pkgb.DuplicateNameService getServiceB() {
        return serviceB;
    }
}
