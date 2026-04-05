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

package org.os890.cdi.addon.dynamictestbean.internal;

import org.os890.cdi.addon.dynamictestbean.TestBean;
import org.os890.cdi.addon.dynamictestbean.TestBeans;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Scope;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.PassivationCapable;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    private final Set<Type> inlineProducerTypes = new LinkedHashSet<>();

    /**
     * <strong>Phase -2 — Force discovery of scope-less alternatives.</strong>
     *
     * <p>If a {@link TestBean}-referenced class has {@code @Alternative}
     * but no CDI scope, it won't be discovered in
     * {@code bean-discovery-mode="annotated"}. This observer adds it
     * explicitly via {@code addAnnotatedType()} and assigns
     * {@code @Dependent} as the default scope.</p>
     */
    void forceDiscoveryOfScopelessAlternatives(
            @Observes jakarta.enterprise.inject.spi.BeforeBeanDiscovery bbd) {
        Class<?> activeTestClass = DynamicTestBeanContext.getActiveTestClass();
        if (activeTestClass == null) {
            return;
        }
        for (Class<?> alt : getSelectedAlternatives(activeTestClass)) {
            if (alt.isAnnotationPresent(Alternative.class) && !hasScopeAnnotation(alt)) {
                bbd.addAnnotatedType(alt, "DynamicTestBean#" + alt.getName());
                LOG.info("[DynamicTestBean] Forced discovery of scope-less @Alternative: "
                        + alt.getName());
            }
        }
    }

    /**
     * <strong>Phase -1 — Veto beans based on mode.</strong>
     *
     * <p>In normal mode: vetoes unselected {@code @Alternative} beans to
     * prevent their {@code @Produces} methods from conflicting with mocks.</p>
     *
     * <p>In whitelist mode ({@code limitToTestBeans = true}): vetoes ALL
     * beans except those explicitly declared via {@link TestBean} and
     * CDI/vendor internal types.</p>
     *
     * @param pat the process-annotated-type event
     * @param bm  the bean manager
     * @param <T> the annotated type
     */
    <T> void vetoUnselectedBeans(@Observes ProcessAnnotatedType<T> pat, BeanManager bm) {
        Class<T> javaClass = pat.getAnnotatedType().getJavaClass();
        Class<?> activeTestClass = DynamicTestBeanContext.getActiveTestClass();
        Set<Class<?>> selected = activeTestClass != null
                ? getSelectedAlternatives(activeTestClass) : Collections.emptySet();

        if (DynamicTestBeanContext.isLimitToTestBeans() && activeTestClass != null) {
            // Whitelist mode: veto everything except selected beans,
            // the test class, and CDI/vendor internals
            if (!selected.contains(javaClass)
                    && !javaClass.equals(activeTestClass)
                    && !isInternalType(javaClass)) {
                pat.veto();
                return;
            }
        }

        // Normal mode (or selected bean in whitelist mode):
        // handle @Alternative veto/scope logic.
        if (javaClass.isAnnotationPresent(Alternative.class)) {
            if (activeTestClass == null) {
                // No @EnableTestBeans — veto alternatives without @Priority
                // (they'd be inactive anyway, but their producers could conflict).
                // @Alternative @Priority beans are CDI-managed, leave them.
                if (!javaClass.isAnnotationPresent(Priority.class)) {
                    pat.veto();
                }
                return;
            }

            if (selected.contains(javaClass)) {
                // This is a @TestBean-selected alternative — add scope if missing.
                if (!hasScopeAnnotation(javaClass, bm)) {
                    pat.configureAnnotatedType().add(Dependent.Literal.INSTANCE);
                    LOG.info("[DynamicTestBean] Added @Dependent scope to: " + javaClass.getName());
                }
                return;
            }

            // Not selected — veto only if its bean types clash with
            // any @TestBean-declared alternative's bean types.
            // @Typed is respected on both sides.
            Set<Type> candidateTypes = resolveBeanTypes(javaClass);
            if (hasTypeClashWithSelected(candidateTypes, selected)) {
                LOG.info("[DynamicTestBean] Vetoing @Alternative (type clash): " + javaClass.getName());
                pat.veto();
            }
        }
    }

    /**
     * Collects all {@link TestBean} references from the given class,
     * recursively walking meta-annotations. Duplicates are filtered.
     */
    private static Set<Class<?>> getSelectedAlternatives(Class<?> testClass) {
        Set<Class<?>> selected = new LinkedHashSet<>();
        Set<Class<? extends Annotation>> visited = new LinkedHashSet<>();
        collectTestBeans(testClass.getAnnotations(), selected, visited);
        return selected;
    }

    /**
     * Recursively collects {@link TestBean} references from annotations
     * and their meta-annotations. Tracks visited annotation types to
     * prevent infinite loops with circular meta-annotations.
     */
    private static void collectTestBeans(Annotation[] annotations,
                                         Set<Class<?>> selected,
                                         Set<Class<? extends Annotation>> visited) {
        for (Annotation ann : annotations) {
            Class<? extends Annotation> annType = ann.annotationType();

            if (ann instanceof TestBean) {
                TestBean tb = (TestBean) ann;
                if (tb.bean() != void.class) {
                    selected.add(tb.bean());
                }
                if (tb.beanProducer() != void.class) {
                    selected.add(tb.beanProducer());
                }
            } else if (ann instanceof TestBeans) {
                for (TestBean tb : ((TestBeans) ann).value()) {
                    if (tb.bean() != void.class) {
                        selected.add(tb.bean());
                    }
                    if (tb.beanProducer() != void.class) {
                        selected.add(tb.beanProducer());
                    }
                }
            }

            // Recurse into meta-annotations (skip JDK/CDI annotations)
            if (!visited.contains(annType)
                    && !annType.getName().startsWith("java.")
                    && !annType.getName().startsWith("jakarta.")) {
                visited.add(annType);
                collectTestBeans(annType.getAnnotations(), selected, visited);
            }
        }
    }

    /**
     * Returns {@code true} for types that must never be vetoed:
     * CDI internals, vendor types, JDK, and this extension's own types.
     */
    private static boolean isInternalType(Class<?> clazz) {
        String name = clazz.getName();
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jakarta.")
                || name.startsWith("org.jboss.weld.")
                || name.startsWith("org.apache.webbeans.")
                || name.startsWith("org.apache.deltaspike.")
                || name.startsWith("org.os890.cdi.addon.dynamictestbean.internal.");
    }

    /**
     * Returns {@code true} if the class has any CDI scope annotation,
     * using {@link BeanManager#isScope(Class)} for detection.
     */
    private static boolean hasScopeAnnotation(Class<?> clazz, BeanManager bm) {
        for (Annotation ann : clazz.getAnnotations()) {
            if (bm.isScope(ann.annotationType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the CDI bean types for a class, respecting {@link Typed}.
     * If {@code @Typed} is present, only the listed types + {@code Object}
     * are returned. Otherwise: the class, all interfaces, all superclasses,
     * and {@code Object}.
     */
    private static Set<Type> resolveBeanTypes(Class<?> clazz) {
        Typed typed = clazz.getAnnotation(Typed.class);
        Set<Type> types = new LinkedHashSet<>();
        if (typed != null) {
            for (Class<?> t : typed.value()) {
                types.add(t);
            }
        } else {
            types.add(clazz);
            for (Type iface : clazz.getGenericInterfaces()) {
                types.add(iface);
            }
            Class<?> superClass = clazz.getSuperclass();
            while (superClass != null && superClass != Object.class) {
                types.add(superClass);
                superClass = superClass.getSuperclass();
            }
        }
        types.add(Object.class);
        return types;
    }

    /**
     * Returns {@code true} if the candidate's bean types overlap with the
     * bean types of any class in the selected {@code @TestBean} set.
     * {@code Object.class} is excluded from the comparison.
     */
    private static boolean hasTypeClashWithSelected(Set<Type> candidateTypes, Set<Class<?>> selected) {
        for (Class<?> selectedClass : selected) {
            Set<Type> selectedTypes = resolveBeanTypes(selectedClass);
            for (Type t : candidateTypes) {
                if (t != Object.class && selectedTypes.contains(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Simple scope check for use before {@link BeanManager} is available.
     * Checks for {@code @Scope} and {@code @NormalScope} meta-annotations.
     */
    private static boolean hasScopeAnnotation(Class<?> clazz) {
        for (Annotation ann : clazz.getAnnotations()) {
            Class<? extends Annotation> type = ann.annotationType();
            if (type.isAnnotationPresent(Scope.class)
                    || type.isAnnotationPresent(NormalScope.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * <strong>Phase 0 — Enable {@link TestBean} alternatives.</strong>
     *
     * <p>Reads {@link TestBean} annotations from the active test class
     * (set by the JUnit extension via {@link DynamicTestBeanContext})
     * and enables each referenced {@code @Alternative} via
     * {@link AfterTypeDiscovery#getAlternatives()}. No
     * {@code ProcessAnnotatedType} or stereotype needed — the JUnit
     * extension communicates the active test class directly.</p>
     *
     * @param atd the after-type-discovery event
     */
    void enableTestBeanAlternatives(@Observes AfterTypeDiscovery atd) {
        Class<?> activeTestClass = DynamicTestBeanContext.getActiveTestClass();
        if (activeTestClass == null) {
            return;
        }

        for (Class<?> alternative : getSelectedAlternatives(activeTestClass)) {
            enableAlternative(alternative, activeTestClass, atd);
        }
    }

    private void enableAlternative(Class<?> alternative, Class<?> testClass, AfterTypeDiscovery atd) {
        if (alternative == void.class) {
            return;
        }
        atd.getAlternatives().add(alternative);
        LOG.info("[DynamicTestBean] Enabled @TestBean alternative: " + alternative.getName()
                + " (declared on " + testClass.getSimpleName() + ")");
    }

    /**
     * Scans the active test class for static fields annotated with
     * {@code @TestBean} and registers each as a synthetic CDI bean
     * using the field's current value. Qualifiers on the field are
     * carried over to the bean.
     */
    private void registerInlineProducerFields(Class<?> testClass,
                                              AfterBeanDiscovery abd,
                                              BeanManager bm) {
        for (Field field : testClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(TestBean.class)) {
                continue;
            }
            if (!Modifier.isStatic(field.getModifiers())) {
                LOG.warning("[DynamicTestBean] @TestBean field must be static: "
                        + testClass.getSimpleName() + "." + field.getName());
                continue;
            }

            field.setAccessible(true);
            Object value;
            try {
                value = field.get(null);
            } catch (IllegalAccessException e) {
                LOG.warning("[DynamicTestBean] Cannot read @TestBean field: "
                        + testClass.getSimpleName() + "." + field.getName());
                continue;
            }
            if (value == null) {
                LOG.warning("[DynamicTestBean] @TestBean field is null: "
                        + testClass.getSimpleName() + "." + field.getName());
                continue;
            }

            Type fieldType = field.getGenericType();
            Set<Annotation> qualifiers = new LinkedHashSet<>();
            for (Annotation ann : field.getAnnotations()) {
                if (bm.isQualifier(ann.annotationType())) {
                    qualifiers.add(ann);
                }
            }
            // Remove @TestBean itself from qualifiers (it's not a CDI qualifier)
            qualifiers.removeIf(a -> a.annotationType() == TestBean.class);

            if (qualifiers.isEmpty()) {
                qualifiers.add(Default.Literal.INSTANCE);
            }
            qualifiers.add(Any.Literal.INSTANCE);

            abd.addBean()
                    .beanClass(field.getType())
                    .addType(fieldType)
                    .addType(Object.class)
                    .qualifiers(qualifiers.toArray(new Annotation[0]))
                    .scope(Singleton.class)
                    .createWith(ctx -> value);

            inlineProducerTypes.add(fieldType);
            LOG.info("[DynamicTestBean] Registered inline @TestBean field: "
                    + testClass.getSimpleName() + "." + field.getName()
                    + " (type=" + field.getType().getSimpleName() + ")");
        }
    }

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
     * <strong>Phase 2 — Register replacement and mock beans.</strong>
     *
     * <p>First registers any {@link TestBean} replacement classes as
     * {@code @RequestScoped} CDI beans. Then, for each remaining unsatisfied
     * injection point, registers a Mockito mock.</p>
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
        // Register the test class as a @Singleton CDI bean if requested
        Class<?> activeTestClass = DynamicTestBeanContext.getActiveTestClass();
        if (activeTestClass != null && DynamicTestBeanContext.isAddTestClass()) {
            abd.addBean()
                    .beanClass(activeTestClass)
                    .addType(activeTestClass)
                    .addType(Object.class)
                    .scope(Singleton.class)
                    .createWith(ctx -> {
                        try {
                            return activeTestClass.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            throw new RuntimeException(
                                    "Failed to create test class bean: " + activeTestClass.getName(), e);
                        }
                    });
            LOG.info("[DynamicTestBean] Registered test class as @Singleton bean: "
                    + activeTestClass.getSimpleName());
        }

        // Register inline @TestBean producer fields from the active test class
        if (activeTestClass != null) {
            registerInlineProducerFields(activeTestClass, abd, bm);
        }

        int mockCount = 0;
        for (InjectionPointKey key : collectedInjectionPoints) {
            if (isSatisfied(key, bm)) {
                continue;
            }

            Class<?> rawType = getRawType(key.type);
            if (rawType == null || hasSyntheticBeanBinding(rawType)
                    || inlineProducerTypes.contains(key.type)) {
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
    }
}
