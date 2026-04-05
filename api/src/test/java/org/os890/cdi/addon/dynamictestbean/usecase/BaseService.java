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

import jakarta.inject.Inject;

/**
 * Abstract generic service that injects a {@link BaseDao BaseDao&lt;E&gt;}.
 * When {@code E} is bound by a concrete subclass, the injection point
 * becomes e.g. {@code BaseDao<Order>} — a parameterized type that
 * the extension must mock if no real DAO bean exists.
 *
 * @param <E> the entity type
 */
public abstract class BaseService<E extends BaseEntity> {

    @Inject
    private BaseDao<E> dao;

    public BaseDao<E> getDao() {
        return dao;
    }
}
