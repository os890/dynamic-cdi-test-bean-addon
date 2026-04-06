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

package org.os890.cdi.addon.dynamictestbean.quarkus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.usecase.AuditService;
import org.os890.cdi.addon.dynamictestbean.usecase.BaseDao;
import org.os890.cdi.addon.dynamictestbean.usecase.CacheService;
import org.os890.cdi.addon.dynamictestbean.usecase.ConstructorInjectedBean;
import org.os890.cdi.addon.dynamictestbean.usecase.EventPayload;
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
import org.os890.cdi.addon.dynamictestbean.usecase.OrderService;
import org.os890.cdi.addon.dynamictestbean.usecase.PaymentService;
import org.os890.cdi.addon.dynamictestbean.usecase.QualifierConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.SelfContainedService;
import org.os890.cdi.addon.dynamictestbean.usecase.BatchPropertyConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.DuplicateNameConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.MultiQualifiedPropertyConsumer;
import org.os890.cdi.addon.dynamictestbean.usecase.ShippingService;
import org.os890.cdi.addon.dynamictestbean.usecase.StereotypedService;
import org.os890.cdi.addon.dynamictestbean.usecase.ValidationService;
import org.os890.cdi.addon.dynamictestbean.usecase.qualifier.Premium;

/**
 * Quarkus/ArC port of the main test suite.
 *
 * <p>Uses {@link EnableTestBeans} to boot an ArC container with auto-mocking
 * for all unsatisfied injection points. Uses {@link Arc#container()} for
 * programmatic lookup.</p>
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamicTestBeanExtensionTest {

    // ================================================================
    // 1. Container bootstrap
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("Container bootstraps without deployment errors")
    void containerBootstrapsSuccessfully() {
        ArcContainer container = Arc.container();
        assertNotNull(container, "ArcContainer should be initialized");
    }

    @Test
    @Order(2)
    @DisplayName("SPI implementation is loaded and BeanManager is accessible")
    void spiImplementationIsLoadedAndBeanManagerAccessible() {
        ArcContainer container = Arc.container();
        assertNotNull(container.beanManager(),
                "BeanManager should be accessible from the ArC container");
    }

    // ================================================================
    // 2. Interface injection — Greeting
    // ================================================================

    @Test
    @Order(10)
    @DisplayName("Interface with no implementation is injected as a mock")
    void interfaceInjectionPointIsMocked() {
        GreetingConsumer consumer = Arc.container().select(GreetingConsumer.class).get();
        Greeting greeting = consumer.getGreeting();
        assertNotNull(greeting, "Greeting should be injected (as a mock)");
    }

    @Test
    @Order(11)
    @DisplayName("Mocked interface returns Mockito defaults (null for String)")
    void mockedInterfaceReturnsMockitoDefaults() {
        GreetingConsumer consumer = Arc.container().select(GreetingConsumer.class).get();
        Greeting greeting = consumer.getGreeting();
        assertNull(greeting.greet("world"),
                "Mock should return null (Mockito default for String)");
    }

    @Test
    @Order(12)
    @DisplayName("Mocked interface returns Mockito defaults consistently across calls")
    void mockedInterfaceReturnsDefaultsConsistently() {
        GreetingConsumer consumer = Arc.container().select(GreetingConsumer.class).get();
        Greeting greeting = consumer.getGreeting();
        assertNull(greeting.greet("Alice"));
        assertNull(greeting.greet("Bob"));
        assertNull(greeting.greet(null));
    }

    // ================================================================
    // 3. Concrete class injection — NotificationSender
    // ================================================================

    @Test
    @Order(20)
    @DisplayName("Concrete class with no CDI bean is injected as a mock")
    void concreteClassInjectionPointIsMocked() {
        GreetingConsumer consumer = Arc.container().select(GreetingConsumer.class).get();
        NotificationSender sender = consumer.getNotificationSender();
        assertNotNull(sender, "NotificationSender should be injected (as a mock)");
    }

    @Test
    @Order(21)
    @DisplayName("Mocked concrete class does not throw")
    void mockedConcreteClassDoesNotThrow() {
        GreetingConsumer consumer = Arc.container().select(GreetingConsumer.class).get();
        NotificationSender sender = consumer.getNotificationSender();
        assertDoesNotThrow(() -> sender.send("test"),
                "Mocked send() should be a no-op");
    }

    @Test
    @Order(22)
    @DisplayName("Mocked concrete class void methods are repeatable no-ops")
    void mockedConcreteClassMethodCallsAreNoOps() {
        GreetingConsumer consumer = Arc.container().select(GreetingConsumer.class).get();
        NotificationSender sender = consumer.getNotificationSender();
        assertDoesNotThrow(() -> sender.send("first"));
        assertDoesNotThrow(() -> sender.send("second"));
        assertDoesNotThrow(() -> sender.send(null));
    }

    // ================================================================
    // 4. Parameterized generic injection — BaseDao<Order>
    // ================================================================

    @Test
    @Order(30)
    @DisplayName("Parameterized generic injection point is injected as a mock")
    void parameterizedGenericInjectionPointIsMocked() {
        OrderService orderService = Arc.container().select(OrderService.class).get();
        BaseDao<org.os890.cdi.addon.dynamictestbean.usecase.Order> dao = orderService.getDao();
        assertNotNull(dao, "BaseDao<Order> should be injected (as a mock)");
    }

    @Test
    @Order(31)
    @DisplayName("Mocked generic DAO returns null by default")
    void mockedGenericDaoReturnsNullByDefault() {
        OrderService orderService = Arc.container().select(OrderService.class).get();
        BaseDao<org.os890.cdi.addon.dynamictestbean.usecase.Order> dao = orderService.getDao();
        assertNull(dao.findById(1L), "Mock DAO should return null (Mockito default)");
        assertNull(dao.findById(999L));
    }

    @Test
    @Order(32)
    @DisplayName("Mocked generic DAO void methods do not throw")
    void mockedGenericDaoVoidMethodsDoNotThrow() {
        OrderService orderService = Arc.container().select(OrderService.class).get();
        BaseDao<org.os890.cdi.addon.dynamictestbean.usecase.Order> dao = orderService.getDao();
        assertDoesNotThrow(() -> dao.save(new org.os890.cdi.addon.dynamictestbean.usecase.Order("test")));
    }

    @Test
    @Order(33)
    @DisplayName("Mocked generic DAO supports Mockito stubbing with concrete entity")
    void mockedGenericDaoSupportsStubbingWithConcreteEntity() {
        OrderService orderService = Arc.container().select(OrderService.class).get();
        BaseDao<org.os890.cdi.addon.dynamictestbean.usecase.Order> dao = orderService.getDao();

        org.os890.cdi.addon.dynamictestbean.usecase.Order expected =
                new org.os890.cdi.addon.dynamictestbean.usecase.Order("Widget");
        expected.setId(42L);

        Mockito.when(dao.findById(42L)).thenReturn(expected);

        org.os890.cdi.addon.dynamictestbean.usecase.Order result = dao.findById(42L);
        assertNotNull(result);
        assertEquals("Widget", result.getItem());
        assertEquals(42L, result.getId());
    }

    // ================================================================
    // 5. @Named qualifier injection — AuditService
    // ================================================================

    @Test
    @Order(35)
    @DisplayName("@Named injection point is satisfied by a mock")
    void namedInjectionPointIsMocked() {
        NamedConsumer consumer = Arc.container().select(NamedConsumer.class).get();
        AuditService auditService = consumer.getAuditService();
        assertNotNull(auditService, "AuditService @Named(\"primaryAudit\") should be injected");
    }

    @Test
    @Order(36)
    @DisplayName("@Named mock void method does not throw")
    void namedMockVoidMethodDoesNotThrow() {
        NamedConsumer consumer = Arc.container().select(NamedConsumer.class).get();
        AuditService auditService = consumer.getAuditService();
        assertDoesNotThrow(() -> auditService.audit("test-action"));
    }

    // ================================================================
    // 6. Single custom qualifier — @Premium PaymentService
    // ================================================================

    @Test
    @Order(60)
    @DisplayName("Single custom qualifier injection point is satisfied by a mock")
    void singleCustomQualifierIsMocked() {
        QualifierConsumer consumer = Arc.container().select(QualifierConsumer.class).get();
        PaymentService payment = consumer.getPremiumPayment();
        assertNotNull(payment, "@Premium PaymentService should be injected (as a mock)");
    }

    @Test
    @Order(61)
    @DisplayName("@Premium mock void method does not throw")
    void singleCustomQualifierMockDoesNotThrow() {
        QualifierConsumer consumer = Arc.container().select(QualifierConsumer.class).get();
        PaymentService payment = consumer.getPremiumPayment();
        assertDoesNotThrow(() -> payment.pay(99.99));
    }

    // ================================================================
    // 7. Multiple custom qualifiers — @Premium @Reliable ShippingService
    // ================================================================

    @Test
    @Order(62)
    @DisplayName("Multiple custom qualifiers injection point is satisfied by a mock")
    void multipleCustomQualifiersAreMocked() {
        QualifierConsumer consumer = Arc.container().select(QualifierConsumer.class).get();
        ShippingService shipping = consumer.getReliableShipping();
        assertNotNull(shipping,
                "@Premium @Reliable ShippingService should be injected (as a mock)");
    }

    @Test
    @Order(63)
    @DisplayName("@Premium @Reliable mock void method does not throw")
    void multipleCustomQualifiersMockDoesNotThrow() {
        QualifierConsumer consumer = Arc.container().select(QualifierConsumer.class).get();
        ShippingService shipping = consumer.getReliableShipping();
        assertDoesNotThrow(() -> shipping.ship("123 Main St"));
    }

    // ================================================================
    // 8. Qualifier with member value — @ServiceType("express") CacheService
    // ================================================================

    @Test
    @Order(64)
    @DisplayName("Qualifier with member value injection point is satisfied by a mock")
    void qualifierWithMemberValueIsMocked() {
        QualifierConsumer consumer = Arc.container().select(QualifierConsumer.class).get();
        CacheService cache = consumer.getExpressCache();
        assertNotNull(cache,
                "@ServiceType(\"express\") CacheService should be injected (as a mock)");
    }

    @Test
    @Order(65)
    @DisplayName("@ServiceType(\"express\") mock returns Mockito default")
    void qualifierWithMemberValueMockReturnsDefault() {
        QualifierConsumer consumer = Arc.container().select(QualifierConsumer.class).get();
        CacheService cache = consumer.getExpressCache();
        assertNull(cache.get("some-key"),
                "Mock CacheService should return null (Mockito default)");
    }

    // ================================================================
    // 9. Stereotype + qualifier combination
    // ================================================================

    @Test
    @Order(66)
    @DisplayName("@Premium HealthCheck satisfied by stereotype-scoped bean is NOT mocked")
    void stereotypeScopedBeanWithQualifierSatisfiesInjectionPoint() {
        QualifierConsumer consumer = Arc.container().select(QualifierConsumer.class).get();
        HealthCheck healthCheck = consumer.getHealthCheck();
        assertNotNull(healthCheck, "@Premium HealthCheck should be injected");
        assertTrue(healthCheck.isHealthy(),
                "Should be the real StereotypedService, not a mock");
    }

    @Test
    @Order(67)
    @DisplayName("Stereotype-scoped bean's unsatisfied dependency is mocked")
    void stereotypeScopedBeanUnsatisfiedDependencyIsMocked() {
        StereotypedService service = Arc.container().select(StereotypedService.class, Premium.Literal.INSTANCE).get();
        MetricsService metrics = service.getMetrics();
        assertNotNull(metrics, "MetricsService should be injected (as a mock)");
        assertDoesNotThrow(() -> metrics.recordLatency(42L));
    }

    // ================================================================
    // 10. Constructor injection — ConstructorInjectedBean(LogService)
    // ================================================================

    @Test
    @Order(70)
    @DisplayName("Constructor-injected dependency is mocked")
    void constructorInjectedDependencyIsMocked() {
        ConstructorInjectedBean bean = Arc.container().select(ConstructorInjectedBean.class).get();
        LogService logService = bean.getLogService();
        assertNotNull(logService, "LogService via constructor injection should be mocked");
        assertDoesNotThrow(() -> logService.log("test"));
    }

    // ================================================================
    // 11. Initializer method injection — init(ValidationService)
    // ================================================================

    @Test
    @Order(71)
    @DisplayName("Initializer method parameter is mocked")
    void initializerMethodParameterIsMocked() {
        InitializerMethodBean bean = Arc.container().select(InitializerMethodBean.class).get();
        ValidationService validationService = bean.getValidationService();
        assertNotNull(validationService, "ValidationService via @Inject method should be mocked");
        assertFalse(validationService.isValid("test"));
    }

    // ================================================================
    // 12. @Nonbinding qualifier member
    // ================================================================

    @Test
    @Order(72)
    @DisplayName("@Nonbinding qualifier member: both injection points are satisfied")
    void nonbindingQualifierBothInjectionPointsSatisfied() {
        NonbindingConsumer consumer = Arc.container().select(NonbindingConsumer.class).get();
        assertNotNull(consumer.getOrderTracing(),
                "@Traced(description=\"order-processing\") should be injected");
        assertNotNull(consumer.getPaymentTracing(),
                "@Traced(description=\"payment-processing\") should be injected");
    }

    @Test
    @Order(73)
    @DisplayName("@Nonbinding qualifier member: mock void method does not throw")
    void nonbindingQualifierMockDoesNotThrow() {
        NonbindingConsumer consumer = Arc.container().select(NonbindingConsumer.class).get();
        assertDoesNotThrow(() -> consumer.getOrderTracing().trace("span-1"));
        assertDoesNotThrow(() -> consumer.getPaymentTracing().trace("span-2"));
    }

    // ================================================================
    // 13. Producer method parameter injection — ProducerBean
    // ================================================================

    @Test
    @Order(80)
    @DisplayName("Producer method parameter (FormatService) is mocked")
    void producerMethodParameterIsMocked() {
        String produced = Arc.container().select(String.class).get();
        assertNull(produced);
    }

    // ================================================================
    // 14. Observer method additional parameter injection
    // ================================================================

    @Test
    @Order(81)
    @DisplayName("Observer method additional parameter (LogService) is mocked")
    void observerMethodParameterIsMocked() {
        ObserverBean observer = Arc.container().select(ObserverBean.class).get();
        Arc.container().beanManager().getEvent().fire(new EventPayload("test-event"));
        assertEquals("test-event", observer.getLastEvent(),
                "Observer should have been invoked with the mocked LogService");
    }

    // ================================================================
    // 15. Satisfied dependencies are NOT mocked
    // ================================================================

    @Test
    @Order(40)
    @DisplayName("Bean with all dependencies satisfied is the real implementation")
    void satisfiedBeanIsNotMocked() {
        SelfContainedService service = Arc.container().select(SelfContainedService.class).get();
        assertNotNull(service);
        assertEquals("pong", service.ping(),
                "SelfContainedService should be the real bean, not a mock");
    }

    @Test
    @Order(41)
    @DisplayName("OrderService is a real bean (only its DAO dependency is mocked)")
    void orderServiceIsRealBean() {
        OrderService orderService = Arc.container().select(OrderService.class).get();
        assertNotNull(orderService);
        assertNotNull(orderService.describe(),
                "OrderService.describe() should return a non-null string");
    }

    @Test
    @Order(42)
    @DisplayName("GreetingConsumer is a real bean (only its dependencies are mocked)")
    void greetingConsumerIsRealBean() {
        GreetingConsumer consumer = Arc.container().select(GreetingConsumer.class).get();
        assertNotNull(consumer);
        assertNotNull(consumer.getGreeting(), "Greeting dependency should be injected");
        assertNotNull(consumer.getNotificationSender(), "NotificationSender dependency should be injected");
    }

    // ================================================================
    // 16. Qualified JDK type — @BatchProperty String (InjectionPoint-aware producer absent)
    // ================================================================

    @Test
    @Order(90)
    @DisplayName("@BatchProperty String injection points are satisfied when producer is absent")
    void batchPropertyStringInjectionPointsAreSatisfied() {
        BatchPropertyConsumer consumer = Arc.container().select(BatchPropertyConsumer.class).get();
        assertNotNull(consumer, "BatchPropertyConsumer should be resolvable");
    }

    @Test
    @Order(91)
    @DisplayName("@BatchProperty String values are null (Mockito/mock default)")
    void batchPropertyStringValuesAreNull() {
        BatchPropertyConsumer consumer = Arc.container().select(BatchPropertyConsumer.class).get();

        assertNull(consumer.getJobName(),
                "@BatchProperty(name=\"job.name\") should resolve to null");
        assertNull(consumer.getChunkSize(),
                "@BatchProperty(name=\"chunk.size\") should resolve to null");
    }

    // ================================================================
    // 17. Same simple name in different packages — unique mock names
    // ================================================================

    @Test
    @Order(95)
    @DisplayName("Same simple name in different packages: both injection points are satisfied")
    void duplicateSimpleNameBothSatisfied() {
        DuplicateNameConsumer consumer = Arc.container().select(DuplicateNameConsumer.class).get();
        assertNotNull(consumer.getServiceA(), "pkga.DuplicateNameService should be injected");
        assertNotNull(consumer.getServiceB(), "pkgb.DuplicateNameService should be injected");
    }

    @Test
    @Order(96)
    @DisplayName("Same simple name in different packages: mocks are distinct instances")
    void duplicateSimpleNameMocksAreDistinct() {
        DuplicateNameConsumer consumer = Arc.container().select(DuplicateNameConsumer.class).get();
        assertNotNull(consumer.getServiceA());
        assertNotNull(consumer.getServiceB());
        assertNull(consumer.getServiceA().greetA(), "Mock should return null (Mockito default)");
        assertNull(consumer.getServiceB().greetB(), "Mock should return null (Mockito default)");
    }

    // ================================================================
    // 18. Multiple qualifiers with @Nonbinding on the same JDK type
    // ================================================================

    @Test
    @Order(100)
    @DisplayName("Different qualifiers on same JDK type: all injection points satisfied")
    void multiQualifiedPropertyAllSatisfied() {
        MultiQualifiedPropertyConsumer consumer =
                Arc.container().select(MultiQualifiedPropertyConsumer.class).get();
        assertNotNull(consumer, "MultiQualifiedPropertyConsumer should be resolvable");
    }

    @Test
    @Order(101)
    @DisplayName("Different qualifiers on same JDK type: all values are null (mock default)")
    void multiQualifiedPropertyAllNull() {
        MultiQualifiedPropertyConsumer consumer =
                Arc.container().select(MultiQualifiedPropertyConsumer.class).get();

        // @BatchProperty fields — both share one mock (name is @Nonbinding)
        assertNull(consumer.getBatchJobName(),
                "@BatchProperty(name=\"batch.jobName\") should be null");
        assertNull(consumer.getBatchStepId(),
                "@BatchProperty(name=\"batch.stepId\") should be null");

        // @ConfigProperty fields — both share a different mock (separate qualifier type)
        assertNull(consumer.getConfigTimeout(),
                "@ConfigProperty(name=\"app.timeout\") should be null");
        assertNull(consumer.getConfigRetries(),
                "@ConfigProperty(name=\"app.retries\") should be null");
    }
}
