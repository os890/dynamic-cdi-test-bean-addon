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

package com.example.myapp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;

import com.example.myapp.service.UserRepository;
import com.example.myapp.service.UserService;

/**
 * Demo test using the addon with a custom application package
 * (com.example.myapp) — NOT the addon's own package.
 * Proves the addon works for arbitrary applications.
 */
@EnableTestBeans
class UserServiceTest {

    @Inject
    UserService userService;

    @Test
    @DisplayName("UserService is injected with auto-mocked dependencies")
    void userServiceIsInjected() {
        assertNotNull(userService, "UserService should be injected");
        assertNotNull(userService.getUserRepository(), "UserRepository should be auto-mocked");
        assertNotNull(userService.getEmailService(), "EmailService should be auto-mocked");
    }

    @Test
    @DisplayName("Mocked UserRepository returns null (Mockito default)")
    void mockedRepositoryReturnsNull() {
        assertNull(userService.getUserRepository().findNameById(999L));
    }

    @Test
    @DisplayName("Mocked EmailService does not throw")
    void mockedEmailServiceDoesNotThrow() {
        assertDoesNotThrow(() -> userService.getEmailService().sendWelcome("test@example.com"));
    }

    @Test
    @DisplayName("Business logic works with mocked dependencies")
    void businessLogicWorksWithMocks() {
        // Without stubbing, findNameById returns null → "Hello, stranger!"
        assertEquals("Hello, stranger!", userService.greetUser(42L));
    }

    @Test
    @DisplayName("Mockito stubbing works through CDI proxy")
    void stubbingWorksThroughProxy() {
        UserRepository repo = userService.getUserRepository();
        Mockito.when(repo.findNameById(1L)).thenReturn("Alice");
        assertEquals("Hello, Alice!", userService.greetUser(1L));
    }
}
