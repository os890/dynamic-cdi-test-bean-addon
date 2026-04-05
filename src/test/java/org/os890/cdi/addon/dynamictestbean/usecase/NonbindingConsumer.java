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

import org.os890.cdi.addon.dynamictestbean.usecase.qualifier.Traced;

/**
 * Bean that injects {@link TracingService} twice with different
 * {@code @Traced(description=...)} values. Since {@code description}
 * is {@code @Nonbinding}, both injection points should resolve to the
 * same mock bean.
 */
@ApplicationScoped
public class NonbindingConsumer {

    @Inject
    @Traced(description = "order-processing")
    private TracingService orderTracing;

    @Inject
    @Traced(description = "payment-processing")
    private TracingService paymentTracing;

    public TracingService getOrderTracing() {
        return orderTracing;
    }

    public TracingService getPaymentTracing() {
        return paymentTracing;
    }
}
