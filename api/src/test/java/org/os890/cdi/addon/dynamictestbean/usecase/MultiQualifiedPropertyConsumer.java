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

import org.os890.cdi.addon.dynamictestbean.usecase.qualifier.BatchProperty;
import org.os890.cdi.addon.dynamictestbean.usecase.qualifier.ConfigProperty;

/**
 * Bean that injects the same JDK type ({@code String}) via two
 * <em>different</em> qualifiers that each have only
 * {@link jakarta.enterprise.util.Nonbinding @Nonbinding} members.
 *
 * <p>In production, each qualifier would have its own
 * {@code InjectionPoint}-aware producer method. When both producers
 * are absent from the test classpath, the extension must register
 * <em>separate</em> mock beans — one per qualifier type — so that
 * both injection points are satisfied.</p>
 *
 * <p>Multiple injection points per qualifier (with different
 * {@code @Nonbinding} values) must also be handled correctly:
 * they should share the same mock bean since only the qualifier
 * <em>type</em> (not its non-binding member values) is relevant
 * for bean resolution.</p>
 */
@ApplicationScoped
public class MultiQualifiedPropertyConsumer {

    // --- @BatchProperty ---

    @Inject
    @BatchProperty(name = "batch.jobName")
    private String batchJobName;

    @Inject
    @BatchProperty(name = "batch.stepId")
    private String batchStepId;

    // --- @ConfigProperty ---

    @Inject
    @ConfigProperty(name = "app.timeout", defaultValue = "30")
    private String configTimeout;

    @Inject
    @ConfigProperty(name = "app.retries", defaultValue = "3")
    private String configRetries;

    public String getBatchJobName() {
        return batchJobName;
    }

    public String getBatchStepId() {
        return batchStepId;
    }

    public String getConfigTimeout() {
        return configTimeout;
    }

    public String getConfigRetries() {
        return configRetries;
    }
}
