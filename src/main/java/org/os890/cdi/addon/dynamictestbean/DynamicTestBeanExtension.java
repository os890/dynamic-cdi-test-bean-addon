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

import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.PassivationCapable;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.inject.Named;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.enterprise.util.Nonbinding;

import org.mockito.Mockito;

/**
 * CDI portable extension that automatically registers Mockito-backed beans
 * for all unsatisfied injection points discovered during container bootstrap.
 *
 * <p>This extension is designed as a <strong>test-only</strong> library. It enables
 * CDI containers to start successfully in modules that don't have all bean
 * implementations on the classpath — for example, a {@code common} module whose
 * abstract services inject DAOs that are only implemented in an {@code app} module.</p>
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li><strong>{@link ProcessInjectionPoint}</strong> — collects every injection
 *       point's type and qualifiers during bean discovery.</li>
 *   <li><strong>{@link ProcessBean}</strong> — catches inherited generic injection
 *       points that some CDI implementations (notably OpenWebBeans) do not report
 *       via {@code ProcessInjectionPoint}.</li>
 *   <li><strong>{@link AfterBeanDiscovery}</strong> — for each injection point that
 *       has no matching bean, registers a {@link RequestScoped @RequestScoped}
 *       synthetic bean backed by {@link Mockito#mock(Class)}.</li>
 * </ol>
 *
 * <h2>Supported injection point types</h2>
 * <ul>
 *   <li>Interfaces — e.g. {@code @Inject AuditService}</li>
 *   <li>Concrete classes — e.g. {@code @Inject NotificationSender}</li>
 *   <li>Parameterized generics — e.g. {@code @Inject BaseDao<Order>}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>Add as a test-scoped dependency. The extension registers itself via
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension} — no
 * manual configuration is needed.</p>
 * <pre>{@code
 * <dependency>
 *     <groupId>org.os890.cdi.addon</groupId>
 *     <artifactId>dynamic-cdi-test-bean-addon</artifactId>
 *     <version>1.0.0-SNAPSHOT</version>
 *     <scope>test</scope>
 * </dependency>
 * }</pre>
 *
 * <p><strong>Important:</strong> Mock beans return Mockito defaults (null, 0,
 * false, empty collections). Do not use this extension in production.</p>
 *
 * @author AI-generated (Claude, Anthropic)
 * @see Extension
 * @see Mockito#mock(Class)
 */
public class DynamicTestBeanExtension implements Extension {

    private static final Logger LOG = Logger.getLogger(DynamicTestBeanExtension.class.getName());

    private final Set<InjectionPointKey> collectedInjectionPoints = new LinkedHashSet<>();

    /**
     * <strong>Phase 1 — Collect injection points.</strong>
     *
     * <p>Invoked by the CDI container for every injection point in every
     * discovered bean. Filters out built-in CDI / JDK / vendor types and
     * records the rest for later evaluation.</p>
     *
     * @param pip the injection point event fired by the container
     * @param <T> the declared type of the injection point
     */
    <T> void collectInjectionPoints(@Observes ProcessInjectionPoint<?, T> pip) {
        InjectionPoint ip = pip.getInjectionPoint();
        Type type = ip.getType();

        if (isBuiltInType(type)) {
            return;
        }

        Set<Annotation> qualifiers = filterImplicitQualifiers(ip.getQualifiers());
        collectedInjectionPoints.add(new InjectionPointKey(type, qualifiers));
    }

    /**
     * <strong>Phase 1b — Collect injection points from managed beans.</strong>
     *
     * <p>Some CDI implementations (notably OpenWebBeans) do not fire
     * {@code ProcessInjectionPoint} for inherited generic fields. This
     * observer catches those by inspecting each bean's injection points
     * directly during {@code ProcessBean}.</p>
     *
     * @param pb the process-bean event fired by the container
     * @param <T> the bean type
     */
    <T> void collectFromBean(@Observes ProcessBean<T> pb) {
        for (InjectionPoint ip : pb.getBean().getInjectionPoints()) {
            Type type = ip.getType();
            if (isBuiltInType(type)) {
                continue;
            }
            Set<Annotation> qualifiers = filterImplicitQualifiers(ip.getQualifiers());
            collectedInjectionPoints.add(new InjectionPointKey(type, qualifiers));
        }
    }

    /**
     * <strong>Phase 2 — Register mock beans for unsatisfied injection points.</strong>
     *
     * <p>After all beans have been discovered, iterates over the collected
     * injection points and checks whether each one is satisfied. For every
     * unsatisfied injection point, a synthetic {@link RequestScoped @RequestScoped}
     * bean backed by {@link Mockito#mock(Class)} is registered.</p>
     *
     * <p>The {@code @Priority(Integer.MAX_VALUE)} ensures this observer runs
     * <em>after</em> other extensions (e.g. DeltaSpike partial beans) have
     * registered their synthetic beans, so that those beans are visible
     * during the {@code isSatisfied()} check.</p>
     *
     * @param abd the after-bean-discovery event, used to register synthetic beans
     * @param bm  the bean manager, used to check whether an injection point is satisfied
     */
    void registerMockBeans(@Observes @Priority(Integer.MAX_VALUE) AfterBeanDiscovery abd, BeanManager bm) {
        int mockCount = 0;
        for (InjectionPointKey key : collectedInjectionPoints) {
            if (isSatisfied(key, bm)) {
                continue;
            }

            Class<?> rawType = getRawType(key.type);
            if (rawType == null || hasSyntheticBeanBinding(rawType)) {
                continue;
            }

            String description = formatType(key.type)
                    + (key.qualifiers.isEmpty() ? "" : " " + key.qualifiers);

            LOG.info("[DynamicTestBean] Registering mock for: " + description);
            abd.addBean(new MockBean<>(rawType, key.type, key.qualifiers));
            mockCount++;
        }

        if (mockCount > 0) {
            LOG.info("[DynamicTestBean] Registered " + mockCount
                    + " mock bean(s) for unsatisfied injection points.");
        }
    }

    // ======================== Type utilities ========================

    /**
     * Returns {@code true} if the given type is a CDI built-in, JDK,
     * or vendor-internal type that should not be mocked.
     */
    private static boolean isBuiltInType(Type type) {
        Class<?> raw = getRawType(type);
        if (raw == null) {
            return true;
        }
        String name = raw.getName();
        return name.startsWith("javax.enterprise.")
                || name.startsWith("javax.inject.")
                || name.startsWith("jakarta.enterprise.")
                || name.startsWith("jakarta.inject.")
                || name.startsWith("java.")
                || name.startsWith("org.jboss.weld.")
                || name.startsWith("org.apache.webbeans.");
    }

    /**
     * Returns {@code true} if the type carries an annotation that signals
     * a third-party CDI extension will provide a synthetic bean for it.
     * Currently detects DeltaSpike {@code @PartialBeanBinding}.
     *
     * <p>Uses annotation name strings to avoid compile-time dependencies
     * on third-party libraries.</p>
     */
    private static boolean hasSyntheticBeanBinding(Class<?> type) {
        for (Annotation ann : type.getAnnotations()) {
            for (Annotation metaAnn : ann.annotationType().getAnnotations()) {
                String name = metaAnn.annotationType().getName();
                if ("org.apache.deltaspike.partialbean.api.PartialBeanBinding".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Extracts the raw {@link Class} from a {@link Type}, handling both
     * plain classes and {@link ParameterizedType parameterized types}.
     *
     * @return the raw class, or {@code null} if the type cannot be resolved
     */
    private static Class<?> getRawType(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            if (raw instanceof Class) {
                return (Class<?>) raw;
            }
        }
        return null;
    }

    /** Formats a type for human-readable logging output. */
    private static String formatType(Type type) {
        if (type instanceof ParameterizedType) {
            return type.toString();
        }
        if (type instanceof Class) {
            return ((Class<?>) type).getSimpleName();
        }
        return type.toString();
    }

    /**
     * Strips implicit {@code @Default} and {@code @Any} qualifiers so that
     * the collected key only contains explicit, user-defined qualifiers.
     */
    private static Set<Annotation> filterImplicitQualifiers(Set<Annotation> qualifiers) {
        Set<Annotation> filtered = new LinkedHashSet<>();
        for (Annotation q : qualifiers) {
            String name = q.annotationType().getName();
            if (!name.endsWith(".Default") && !name.endsWith(".Any")) {
                filtered.add(q);
            }
        }
        return filtered;
    }

    /**
     * Checks whether the given injection point key already has at least one
     * matching bean registered in the {@link BeanManager}.
     */
    private static boolean isSatisfied(InjectionPointKey key, BeanManager bm) {
        try {
            Annotation[] qualifiers = key.qualifiers.toArray(new Annotation[0]);
            Set<Bean<?>> beans = bm.getBeans(key.type, qualifiers);
            return !beans.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // ======================== InjectionPointKey ========================

    /**
     * Value object that uniquely identifies an injection point by its
     * resolved type and set of explicit qualifiers. Used as a key in a
     * {@link LinkedHashSet} to deduplicate injection points across
     * {@link ProcessInjectionPoint} and {@link ProcessBean} observers.
     *
     * <p>Equality and hashing respect {@link Nonbinding} semantics:
     * qualifier members annotated with {@code @Nonbinding} are excluded
     * from comparison, so two injection points that differ only in
     * {@code @Nonbinding} member values are considered identical.</p>
     */
    private static class InjectionPointKey {

        final Type type;
        final Set<Annotation> qualifiers;

        InjectionPointKey(Type type, Set<Annotation> qualifiers) {
            this.type = type;
            this.qualifiers = qualifiers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof InjectionPointKey)) {
                return false;
            }
            InjectionPointKey that = (InjectionPointKey) o;
            return type.equals(that.type)
                    && qualifiers.size() == that.qualifiers.size()
                    && qualifiersMatch(qualifiers, that.qualifiers);
        }

        @Override
        public int hashCode() {
            int h = type.hashCode();
            for (Annotation q : qualifiers) {
                h = 31 * h + qualifierHash(q);
            }
            return h;
        }

        /**
         * Compares two qualifier sets respecting {@link Nonbinding}
         * semantics: only binding member values are compared.
         */
        private static boolean qualifiersMatch(Set<Annotation> a, Set<Annotation> b) {
            for (Annotation qa : a) {
                boolean found = false;
                for (Annotation qb : b) {
                    if (qualifierEquals(qa, qb)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Compares two qualifier annotations by annotation type and
         * binding member values only ({@link Nonbinding} members are ignored).
         */
        private static boolean qualifierEquals(Annotation a, Annotation b) {
            if (!a.annotationType().equals(b.annotationType())) {
                return false;
            }
            for (Method m : a.annotationType().getDeclaredMethods()) {
                if (m.isAnnotationPresent(Nonbinding.class)) {
                    continue;
                }
                try {
                    Object va = m.invoke(a);
                    Object vb = m.invoke(b);
                    if (!memberValueEquals(va, vb)) {
                        return false;
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Computes a hash code for a qualifier annotation using only
         * the annotation type and binding member values.
         */
        private static int qualifierHash(Annotation a) {
            int h = a.annotationType().hashCode();
            for (Method m : a.annotationType().getDeclaredMethods()) {
                if (m.isAnnotationPresent(Nonbinding.class)) {
                    continue;
                }
                try {
                    Object v = m.invoke(a);
                    h = 31 * h + memberValueHash(v);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    // fall through
                }
            }
            return h;
        }

        private static boolean memberValueEquals(Object a, Object b) {
            if (a instanceof Object[] && b instanceof Object[]) {
                return Arrays.deepEquals((Object[]) a, (Object[]) b);
            }
            return Objects.equals(a, b);
        }

        private static int memberValueHash(Object v) {
            if (v instanceof Object[]) {
                return Arrays.deepHashCode((Object[]) v);
            }
            return Objects.hashCode(v);
        }
    }

    // ======================== MockBean ========================

    /**
     * Synthetic CDI bean backed by {@link Mockito#mock(Class)}.
     *
     * <p>Registered as {@link RequestScoped @RequestScoped} to match typical
     * application-server scoping and to ensure a fresh mock instance per
     * request context. Implements {@link PassivationCapable} to satisfy Weld's
     * requirement for serializable-capable normal-scoped beans.</p>
     *
     * @param <T> the bean type
     */
    private static class MockBean<T> implements Bean<T>, PassivationCapable {

        private final Class<T> rawType;
        private final Type beanType;
        private final Set<Annotation> qualifiers;
        private final String id;
        private final String name;

        @SuppressWarnings("unchecked")
        MockBean(Class<?> rawType, Type beanType, Set<Annotation> qualifiers) {
            this.rawType = (Class<T>) rawType;
            this.beanType = beanType;
            this.qualifiers = new LinkedHashSet<>(qualifiers);
            // CDI spec: @Default is only present when the bean declares no qualifier
            // other than @Named and @Any. If custom qualifiers exist, omit @Default.
            if (!hasCustomQualifier(qualifiers)) {
                this.qualifiers.add(Default.Literal.INSTANCE);
            }
            this.qualifiers.add(Any.Literal.INSTANCE);
            this.id = "DynamicTestBean#" + beanType + "#" + qualifiers;
            this.name = extractName(qualifiers, rawType);
        }

        /**
         * Returns {@code true} if the qualifier set contains any custom qualifier
         * (i.e. anything other than {@code @Named}, {@code @Default}, or {@code @Any}).
         */
        private static boolean hasCustomQualifier(Set<Annotation> qualifiers) {
            for (Annotation q : qualifiers) {
                Class<? extends Annotation> type = q.annotationType();
                if (type != Default.class
                        && type != Any.class
                        && type != Named.class) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Extracts the bean name from the qualifiers. If a {@code @Named}
         * qualifier is present, its value is used so that the mock matches
         * the injection point. Otherwise falls back to a descriptive default.
         */
        private static String extractName(Set<Annotation> qualifiers, Class<?> rawType) {
            for (Annotation q : qualifiers) {
                if (q instanceof Named) {
                    String value = ((Named) q).value();
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }
            return "dynamicTestBean_" + rawType.getSimpleName();
        }

        /** {@inheritDoc} */
        @Override
        public String getId() {
            return id;
        }

        /** {@inheritDoc} */
        @Override
        public Set<Type> getTypes() {
            Set<Type> types = new LinkedHashSet<>();
            types.add(beanType);
            types.add(rawType);
            types.add(Object.class);
            return types;
        }

        /** {@inheritDoc} */
        @Override
        public Set<Annotation> getQualifiers() {
            return qualifiers;
        }

        /** Returns {@link RequestScoped} — mock beans are scoped per request. */
        @Override
        public Class<? extends Annotation> getScope() {
            return RequestScoped.class;
        }

        /** Returns a descriptive name for logging and container diagnostics. */
        @Override
        public String getName() {
            return name;
        }

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        /** Returns {@code false} — mock beans are not alternatives. */
        @Override
        public boolean isAlternative() {
            return false;
        }

        /** {@inheritDoc} */
        @Override
        public Class<?> getBeanClass() {
            return rawType;
        }

        /** Returns an empty set — mock beans have no injection points of their own. */
        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        /** Creates a new Mockito mock of the raw type. */
        @Override
        public T create(CreationalContext<T> ctx) {
            return Mockito.mock(rawType);
        }

        /** Releases the creational context. */
        @Override
        public void destroy(T instance, CreationalContext<T> ctx) {
            ctx.release();
        }
    }
}
