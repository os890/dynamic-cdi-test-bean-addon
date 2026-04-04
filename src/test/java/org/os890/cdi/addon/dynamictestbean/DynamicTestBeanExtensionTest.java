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

package org.os890.cdi.addon.dynamictestbean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.BeanManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.os890.cdi.addon.dynamictestbean.internal.DynamicTestBeanExtension;
import org.os890.cdi.addon.dynamictestbean.usecase.AuditService;
import org.os890.cdi.addon.dynamictestbean.usecase.ConstructorInjectedBean;
import org.os890.cdi.addon.dynamictestbean.usecase.EventPayload;
import org.os890.cdi.addon.dynamictestbean.usecase.BaseDao;
import org.os890.cdi.addon.dynamictestbean.usecase.CacheService;
import org.os890.cdi.addon.dynamictestbean.usecase.Greeting;
import org.os890.cdi.addon.dynamictestbean.usecase.GreetingConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.HealthCheck;
import org.os890.cdi.addon.dynamictestbean.usecase.InitializerMethodBean;
import org.os890.cdi.addon.dynamictestbean.usecase.LogService;
import org.os890.cdi.addon.dynamictestbean.usecase.MetricsService;
import org.os890.cdi.addon.dynamictestbean.usecase.NamedConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.NonbindingConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.NotificationSender;
import org.os890.cdi.addon.dynamictestbean.usecase.ObserverBean;
import org.os890.cdi.addon.dynamictestbean.usecase.Order;
import org.os890.cdi.addon.dynamictestbean.usecase.OrderService;
import org.os890.cdi.addon.dynamictestbean.usecase.PaymentService;
import org.os890.cdi.addon.dynamictestbean.usecase.QualifierConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.SelfContainedService;
import org.os890.cdi.addon.dynamictestbean.usecase.ShippingService;
import org.os890.cdi.addon.dynamictestbean.usecase.StereotypedService;
import org.os890.cdi.addon.dynamictestbean.usecase.ValidationService;
import org.os890.cdi.addon.dynamictestbean.usecase.qualifier.Premium;

/**
 * Test suite for {@link DynamicTestBeanExtension}.
 *
 * <p>Bootstraps a CDI SE container with <strong>full classpath scanning</strong>
 * — no {@code disableDiscovery()}, no explicit package filtering. The extension
 * is loaded via its {@code META-INF/services} SPI file, exactly as a consumer
 * would use it.</p>
 *
 * <p>Uses only the standard CDI SE bootstrap API
 * ({@link SeContainerInitializer}) and the standard
 * {@link RequestContextController} — no vendor-specific classes.
 * Runs against both <strong>Weld</strong> (default) and
 * <strong>OpenWebBeans</strong> via Maven profiles:
 * {@code mvn test -Pweld} / {@code mvn test -Powb}.</p>
 *
 * <h2>Test scenarios</h2>
 * <ol>
 *   <li>Extension loads via SPI and registers mocks for unsatisfied injection points</li>
 *   <li>Interface injection point with no implementation ({@link Greeting})</li>
 *   <li>Concrete class injection point with no CDI bean ({@link NotificationSender})</li>
 *   <li>Parameterized generic injection point ({@code BaseDao<Order>})</li>
 *   <li>Mockito stubbing works through the CDI proxy for abstract/generic types</li>
 *   <li>Beans with all dependencies satisfied are left untouched</li>
 *   <li>Extension introspection: mock count, immutability, type descriptions</li>
 *   <li>Extension is accessible from the {@link BeanManager}</li>
 *   <li>Container bootstraps without deployment errors</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamicTestBeanExtensionTest {

    private static SeContainer container;
    private static RequestContextController requestContext;

    /**
     * Bootstraps a CDI SE container with full classpath scanning.
     * The extension is loaded via its SPI registration file, beans are
     * discovered via {@code beans.xml} with {@code bean-discovery-mode="annotated"}.
     */
    @BeforeAll
    static void bootContainer() {
        container = SeContainerInitializer.newInstance().initialize();

        // Activate request context so @RequestScoped mock beans can be resolved
        requestContext = container.select(RequestContextController.class).get();
        requestContext.activate();
    }

    /** Deactivates the request context and shuts down the CDI container. */
    @AfterAll
    static void shutdownContainer() {
        if (requestContext != null) {
            requestContext.deactivate();
        }
        if (container != null && container.isRunning()) {
            container.close();
        }
    }

    // ================================================================
    // 1. Container bootstrap and extension loading
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Container bootstraps without deployment errors")
    void containerBootstrapsSuccessfully() {
        assertNotNull(container, "SeContainer should be initialized");
        assertTrue(container.isRunning(), "SeContainer should be running");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Extension is accessible from the BeanManager")
    void extensionIsAccessibleFromBeanManager() {
        DynamicTestBeanExtension ext = container.getBeanManager()
                .getExtension(DynamicTestBeanExtension.class);
        assertNotNull(ext, "Extension should be registered via SPI");
    }

    // ================================================================
    // 2. Interface injection — Greeting
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Interface with no implementation is injected as a mock")
    void interfaceInjectionPointIsMocked() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        Greeting greeting = consumer.getGreeting();

        assertNotNull(greeting, "Greeting should be injected (as a mock)");
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Mocked interface returns Mockito defaults (null for String)")
    void mockedInterfaceReturnsMockitoDefaults() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        Greeting greeting = consumer.getGreeting();

        assertNull(greeting.greet("world"),
                "Mock should return null (Mockito default for String)");
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Mocked interface returns Mockito defaults consistently across calls")
    void mockedInterfaceReturnsDefaultsConsistently() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        Greeting greeting = consumer.getGreeting();

        assertNull(greeting.greet("Alice"));
        assertNull(greeting.greet("Bob"));
        assertNull(greeting.greet(null));
    }

    // ================================================================
    // 3. Concrete class injection — NotificationSender
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(20)
    @DisplayName("Concrete class with no CDI bean is injected as a mock")
    void concreteClassInjectionPointIsMocked() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        NotificationSender sender = consumer.getNotificationSender();

        assertNotNull(sender, "NotificationSender should be injected (as a mock)");
    }

    @Test
    @org.junit.jupiter.api.Order(21)
    @DisplayName("Mocked concrete class does not throw — real class throws UnsupportedOperationException")
    void mockedConcreteClassDoesNotThrow() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        NotificationSender sender = consumer.getNotificationSender();

        // Real NotificationSender.send() throws UnsupportedOperationException.
        // The mock silently returns void — proving it's a Mockito mock.
        assertDoesNotThrow(() -> sender.send("test"),
                "Mocked send() should be a no-op");
    }

    @Test
    @org.junit.jupiter.api.Order(22)
    @DisplayName("Mocked concrete class void methods are repeatable no-ops")
    void mockedConcreteClassMethodCallsAreNoOps() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        NotificationSender sender = consumer.getNotificationSender();

        assertDoesNotThrow(() -> sender.send("first"));
        assertDoesNotThrow(() -> sender.send("second"));
        assertDoesNotThrow(() -> sender.send(null));
    }

    // ================================================================
    // 4. Parameterized generic injection — BaseDao<Order>
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(30)
    @DisplayName("Parameterized generic injection point is injected as a mock")
    void parameterizedGenericInjectionPointIsMocked() {
        OrderService orderService = container.select(OrderService.class).get();
        BaseDao<Order> dao = orderService.getDao();

        assertNotNull(dao, "BaseDao<Order> should be injected (as a mock)");
    }

    @Test
    @org.junit.jupiter.api.Order(31)
    @DisplayName("Mocked generic DAO returns null by default")
    void mockedGenericDaoReturnsNullByDefault() {
        OrderService orderService = container.select(OrderService.class).get();
        BaseDao<Order> dao = orderService.getDao();

        assertNull(dao.findById(1L),
                "Mock DAO should return null (Mockito default)");
        assertNull(dao.findById(999L));
    }

    @Test
    @org.junit.jupiter.api.Order(32)
    @DisplayName("Mocked generic DAO void methods do not throw")
    void mockedGenericDaoVoidMethodsDoNotThrow() {
        OrderService orderService = container.select(OrderService.class).get();
        BaseDao<Order> dao = orderService.getDao();

        // Real BaseDao.save() throws UnsupportedOperationException
        assertDoesNotThrow(() -> dao.save(new Order("test")));
    }

    @Test
    @org.junit.jupiter.api.Order(33)
    @DisplayName("Mocked generic DAO supports Mockito stubbing with concrete entity")
    void mockedGenericDaoSupportsStubbingWithConcreteEntity() {
        OrderService orderService = container.select(OrderService.class).get();
        BaseDao<Order> dao = orderService.getDao();

        Order expected = new Order("Widget");
        expected.setId(42L);

        // Stubbing works through the CDI proxy for abstract/generic types
        Mockito.when(dao.findById(42L)).thenReturn(expected);

        Order result = dao.findById(42L);
        assertNotNull(result);
        assertEquals("Widget", result.getItem());
        assertEquals(42L, result.getId());
    }

    // ================================================================
    // 5. @Named qualifier injection — AuditService
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(35)
    @DisplayName("@Named injection point is satisfied by a mock")
    void namedInjectionPointIsMocked() {
        NamedConsumer consumer = container.select(NamedConsumer.class).get();
        AuditService auditService = consumer.getAuditService();

        assertNotNull(auditService, "AuditService @Named(\"primaryAudit\") should be injected");
    }

    @Test
    @org.junit.jupiter.api.Order(36)
    @DisplayName("@Named mock void method does not throw")
    void namedMockVoidMethodDoesNotThrow() {
        NamedConsumer consumer = container.select(NamedConsumer.class).get();
        AuditService auditService = consumer.getAuditService();

        // Real AuditService has no implementation — the mock is a no-op
        assertDoesNotThrow(() -> auditService.audit("test-action"));
    }

    // ================================================================
    // 6. Single custom qualifier — @Premium PaymentService
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(60)
    @DisplayName("Single custom qualifier injection point is satisfied by a mock")
    void singleCustomQualifierIsMocked() {
        QualifierConsumer consumer = container.select(QualifierConsumer.class).get();
        PaymentService payment = consumer.getPremiumPayment();

        assertNotNull(payment, "@Premium PaymentService should be injected (as a mock)");
    }

    @Test
    @org.junit.jupiter.api.Order(61)
    @DisplayName("@Premium mock void method does not throw")
    void singleCustomQualifierMockDoesNotThrow() {
        QualifierConsumer consumer = container.select(QualifierConsumer.class).get();
        PaymentService payment = consumer.getPremiumPayment();

        assertDoesNotThrow(() -> payment.pay(99.99));
    }

    // ================================================================
    // 7. Multiple custom qualifiers — @Premium @Reliable ShippingService
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(62)
    @DisplayName("Multiple custom qualifiers injection point is satisfied by a mock")
    void multipleCustomQualifiersAreMocked() {
        QualifierConsumer consumer = container.select(QualifierConsumer.class).get();
        ShippingService shipping = consumer.getReliableShipping();

        assertNotNull(shipping,
                "@Premium @Reliable ShippingService should be injected (as a mock)");
    }

    @Test
    @org.junit.jupiter.api.Order(63)
    @DisplayName("@Premium @Reliable mock void method does not throw")
    void multipleCustomQualifiersMockDoesNotThrow() {
        QualifierConsumer consumer = container.select(QualifierConsumer.class).get();
        ShippingService shipping = consumer.getReliableShipping();

        assertDoesNotThrow(() -> shipping.ship("123 Main St"));
    }

    // ================================================================
    // 8. Qualifier with member value — @ServiceType("express") CacheService
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(64)
    @DisplayName("Qualifier with member value injection point is satisfied by a mock")
    void qualifierWithMemberValueIsMocked() {
        QualifierConsumer consumer = container.select(QualifierConsumer.class).get();
        CacheService cache = consumer.getExpressCache();

        assertNotNull(cache,
                "@ServiceType(\"express\") CacheService should be injected (as a mock)");
    }

    @Test
    @org.junit.jupiter.api.Order(65)
    @DisplayName("@ServiceType(\"express\") mock returns Mockito default")
    void qualifierWithMemberValueMockReturnsDefault() {
        QualifierConsumer consumer = container.select(QualifierConsumer.class).get();
        CacheService cache = consumer.getExpressCache();

        assertNull(cache.get("some-key"),
                "Mock CacheService should return null (Mockito default)");
    }

    // ================================================================
    // 9. Stereotype + qualifier combination — @Monitored @Premium StereotypedService
    //    Scope from stereotype, qualifier declared directly.
    //    Satisfies @Premium HealthCheck. Has unsatisfied MetricsService.
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(66)
    @DisplayName("@Premium HealthCheck satisfied by stereotype-scoped bean is NOT mocked")
    void stereotypeScopedBeanWithQualifierSatisfiesInjectionPoint() {
        QualifierConsumer consumer = container.select(QualifierConsumer.class).get();
        HealthCheck healthCheck = consumer.getHealthCheck();

        assertNotNull(healthCheck, "@Premium HealthCheck should be injected");
        assertTrue(healthCheck.isHealthy(),
                "Should be the real StereotypedService, not a mock");
    }

    @Test
    @org.junit.jupiter.api.Order(67)
    @DisplayName("Stereotype-scoped bean's unsatisfied dependency is mocked")
    void stereotypeScopedBeanUnsatisfiedDependencyIsMocked() {
        StereotypedService service = container.select(StereotypedService.class, Premium.Literal.INSTANCE).get();
        MetricsService metrics = service.getMetrics();

        assertNotNull(metrics, "MetricsService should be injected (as a mock)");
        assertDoesNotThrow(() -> metrics.recordLatency(42L));
    }

    // ================================================================
    // 10. Constructor injection — ConstructorInjectedBean(LogService)
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(70)
    @DisplayName("Constructor-injected dependency is mocked")
    void constructorInjectedDependencyIsMocked() {
        ConstructorInjectedBean bean = container.select(ConstructorInjectedBean.class).get();
        LogService logService = bean.getLogService();

        assertNotNull(logService, "LogService via constructor injection should be mocked");
        assertDoesNotThrow(() -> logService.log("test"));
    }

    // ================================================================
    // 11. Initializer method injection — init(ValidationService)
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(71)
    @DisplayName("Initializer method parameter is mocked")
    void initializerMethodParameterIsMocked() {
        InitializerMethodBean bean = container.select(InitializerMethodBean.class).get();
        ValidationService validationService = bean.getValidationService();

        assertNotNull(validationService, "ValidationService via @Inject method should be mocked");
        // Mockito default for boolean is false
        assertFalse(validationService.isValid("test"));
    }

    // ================================================================
    // 12. @Nonbinding qualifier member — @Traced(description=...)
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(72)
    @DisplayName("@Nonbinding qualifier member: both injection points are satisfied")
    void nonbindingQualifierBothInjectionPointsSatisfied() {
        NonbindingConsumer consumer = container.select(NonbindingConsumer.class).get();

        assertNotNull(consumer.getOrderTracing(),
                "@Traced(description=\"order-processing\") should be injected");
        assertNotNull(consumer.getPaymentTracing(),
                "@Traced(description=\"payment-processing\") should be injected");
    }

    @Test
    @org.junit.jupiter.api.Order(73)
    @DisplayName("@Nonbinding qualifier member: mock void method does not throw")
    void nonbindingQualifierMockDoesNotThrow() {
        NonbindingConsumer consumer = container.select(NonbindingConsumer.class).get();

        assertDoesNotThrow(() -> consumer.getOrderTracing().trace("span-1"));
        assertDoesNotThrow(() -> consumer.getPaymentTracing().trace("span-2"));
    }

    // ================================================================
    // 13. Producer method parameter injection — ProducerBean
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(80)
    @DisplayName("Producer method parameter (FormatService) is mocked")
    void producerMethodParameterIsMocked() {
        // The ProducerBean.formattedValue(FormatService) producer has an
        // unsatisfied FormatService parameter. The extension must mock it
        // so the container can invoke the producer.
        String produced = container.select(String.class).get();
        // FormatService.format() returns null (Mockito default) -> producer returns null
        assertNull(produced);
    }

    // ================================================================
    // 14. Observer method additional parameter injection — ObserverBean
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(81)
    @DisplayName("Observer method additional parameter (LogService) is mocked")
    void observerMethodParameterIsMocked() {
        ObserverBean observer = container.select(ObserverBean.class).get();

        // Fire an event — the observer's LogService parameter must be satisfied
        container.getBeanManager().getEvent().fire(new EventPayload("test-event"));

        assertEquals("test-event", observer.getLastEvent(),
                "Observer should have been invoked with the mocked LogService");
    }

    // ================================================================
    // 15. Satisfied dependencies are NOT mocked
    // ================================================================

    @Test
    @org.junit.jupiter.api.Order(40)
    @DisplayName("Bean with all dependencies satisfied is the real implementation")
    void satisfiedBeanIsNotMocked() {
        SelfContainedService service = container.select(SelfContainedService.class).get();

        assertNotNull(service);
        assertEquals("pong", service.ping(),
                "SelfContainedService should be the real bean, not a mock");
    }

    @Test
    @org.junit.jupiter.api.Order(41)
    @DisplayName("OrderService is a real bean (only its DAO dependency is mocked)")
    void orderServiceIsRealBean() {
        OrderService orderService = container.select(OrderService.class).get();
        assertNotNull(orderService);
        assertNotNull(orderService.describe(),
                "OrderService.describe() should return a non-null string");
    }

    @Test
    @org.junit.jupiter.api.Order(42)
    @DisplayName("GreetingConsumer is a real bean (only its dependencies are mocked)")
    void greetingConsumerIsRealBean() {
        GreetingConsumer consumer = container.select(GreetingConsumer.class).get();
        assertNotNull(consumer);
        assertNotNull(consumer.getGreeting(), "Greeting dependency should be injected");
        assertNotNull(consumer.getNotificationSender(), "NotificationSender dependency should be injected");
    }

}
