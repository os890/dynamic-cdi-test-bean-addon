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

import org.os890.cdi.addon.dynamictestbean.usecase.qualifier.Premium;
import org.os890.cdi.addon.dynamictestbean.usecase.qualifier.Reliable;
import org.os890.cdi.addon.dynamictestbean.usecase.qualifier.ServiceType;

/**
 * Bean that injects services using various CDI qualifier combinations:
 * <ul>
 *   <li>Single custom qualifier: {@code @Premium PaymentService}</li>
 *   <li>Multiple qualifiers: {@code @Premium @Reliable ShippingService}</li>
 *   <li>Qualifier with member: {@code @ServiceType("express") CacheService}</li>
 *   <li>Satisfied qualified: {@code @Premium HealthCheck}
 *       — satisfied by {@link StereotypedService} (stereotype-scoped, {@code @Premium})</li>
 * </ul>
 */
@ApplicationScoped
public class QualifierConsumer {

    @Inject
    @Premium
    private PaymentService premiumPayment;

    @Inject
    @Premium
    @Reliable
    private ShippingService reliableShipping;

    @Inject
    @ServiceType("express")
    private CacheService expressCache;

    @Inject
    @Premium
    private HealthCheck healthCheck;

    public PaymentService getPremiumPayment() {
        return premiumPayment;
    }

    public ShippingService getReliableShipping() {
        return reliableShipping;
    }

    public CacheService getExpressCache() {
        return expressCache;
    }

    public HealthCheck getHealthCheck() {
        return healthCheck;
    }
}
