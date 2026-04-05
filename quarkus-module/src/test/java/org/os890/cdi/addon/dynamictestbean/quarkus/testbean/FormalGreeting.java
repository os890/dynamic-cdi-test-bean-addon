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

package org.os890.cdi.addon.dynamictestbean.quarkus.testbean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.os890.cdi.addon.dynamictestbean.usecase.Greeting;

/**
 * A second {@code @Alternative} for {@link Greeting} — different from
 * {@link CustomGreeting}. Used to test that two test classes can
 * activate different alternatives for the same type without conflict.
 */
@Alternative
@ApplicationScoped
public class FormalGreeting implements Greeting {

    @Override
    public String greet(String name) {
        return "Good day, " + name + ".";
    }
}
