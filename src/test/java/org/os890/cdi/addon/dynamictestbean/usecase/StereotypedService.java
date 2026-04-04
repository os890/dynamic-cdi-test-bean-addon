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

import org.os890.cdi.addon.dynamictestbean.usecase.qualifier.Monitored;
import org.os890.cdi.addon.dynamictestbean.usecase.qualifier.Premium;

/**
 * Bean whose scope ({@code @ApplicationScoped}) comes from the
 * {@link Monitored} stereotype, and whose qualifier ({@code @Premium})
 * is declared directly on the class.
 *
 * <p>Tests two things:</p>
 * <ul>
 *   <li>The stereotype-scoped bean is discovered and satisfies
 *       {@code @Inject @Premium HealthCheck} (via
 *       {@link MonitoredHealthCheck}).</li>
 *   <li>The bean's own unsatisfied dependency ({@link MetricsService})
 *       is correctly mocked by the extension.</li>
 * </ul>
 */
@Monitored
@Premium
public class StereotypedService implements HealthCheck {

    @Inject
    private MetricsService metrics;

    @Override
    public boolean isHealthy() {
        return true;
    }

    public MetricsService getMetrics() {
        return metrics;
    }
}
