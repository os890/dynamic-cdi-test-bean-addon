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

package org.os890.cdi.addon.dynamictestbean.quarkus.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.Type;

import org.junit.jupiter.api.extension.ExtensionContext;

import org.os890.cdi.addon.dynamictestbean.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.TestBean;
import org.os890.cdi.addon.dynamictestbean.TestBeans;
import org.os890.cdi.addon.dynamictestbean.spi.TestBeanContainerManager;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.ArcInitConfig;
import io.quarkus.arc.ComponentsProvider;
import io.quarkus.arc.processor.BeanArchives;
import io.quarkus.arc.processor.BeanConfigurator;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BeanProcessor;
import io.quarkus.arc.processor.BeanRegistrar;
import io.quarkus.arc.processor.BuiltinBean;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.arc.processor.ResourceOutput;

/**
 * Quarkus/ArC implementation of {@link TestBeanContainerManager}.
 *
 * <p>Manages the ArC container lifecycle and registers mock beans for
 * unsatisfied injection points. Modeled after ArC's own
 * {@code ArcTestContainer} and Quarkus's
 * {@code QuarkusComponentTestExtension}.</p>
 *
 * <p>If a Quarkus test annotation ({@code @QuarkusTest} or
 * {@code @QuarkusComponentTest}) is present on the test class, all
 * container lifecycle management is skipped — the Quarkus test
 * framework handles the container in that case.</p>
 */
public class DynamicTestBeanJUnitExtension implements TestBeanContainerManager {

    private static final Logger LOG = Logger.getLogger(DynamicTestBeanJUnitExtension.class.getName());

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(DynamicTestBeanJUnitExtension.class);

    private static final String KEY_OLD_TCCL = "oldTccl";
    private static final String KEY_QUARKUS_MANAGED = "quarkusManaged";
    private static final String TARGET_TEST_CLASSES = "target/test-classes";
    private static final String TARGET_CLASSES = "target/classes";

    /** Quarkus test annotations whose presence means the container is externally managed. */
    private static final String[] QUARKUS_TEST_ANNOTATIONS = {
        "io.quarkus.test.junit.QuarkusTest",
        "io.quarkus.test.component.QuarkusComponentTest"
    };

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getTestClass().ifPresent(testClass -> {
            EnableTestBeans config = testClass.getAnnotation(EnableTestBeans.class);
            if (config == null) {
                return;
            }

            // If a Quarkus test annotation is present, the container is
            // already managed by the Quarkus test framework — skip our
            // container lifecycle handling entirely.
            if (isQuarkusManagedContainer(testClass)) {
                context.getStore(NS).put(KEY_QUARKUS_MANAGED, Boolean.TRUE);
                LOG.info("[DynamicTestBean] Quarkus test annotation detected on "
                        + testClass.getSimpleName() + " — skipping container management");
                return;
            }

            boolean addTestClass = config.addTestClass();
            boolean limitToTestBeans = config.limitToTestBeans();

            // Collect @TestBean-selected alternatives (with meta-annotation support)
            Set<Class<?>> selectedAlternatives = getSelectedAlternatives(testClass);

            // Collect inline @TestBean producer fields
            Set<InlineField> inlineFields = collectInlineFields(testClass);

            // Collect all bean classes: test class + alternatives + inline field types +
            // all classes from test-classes directory via classpath scanning
            Set<Class<?>> beanClasses = new LinkedHashSet<>();
            if (addTestClass) {
                beanClasses.add(testClass);
            }
            beanClasses.addAll(selectedAlternatives);

            // Scan the test-classes directory for all application classes
            Set<Class<?>> discoveredClasses = discoverTestClasses(testClass);
            if (!limitToTestBeans) {
                // Add discovered classes but exclude OTHER test classes
                // (they have their own @EnableTestBeans / @Produces fields that
                // would conflict in this test's bean archive)
                for (Class<?> cls : discoveredClasses) {
                    if (cls.equals(testClass)) {
                        continue; // Already handled above
                    }
                    if (cls.isAnnotationPresent(EnableTestBeans.class)) {
                        continue; // Other test class — skip
                    }
                    // Also skip classes that are JUnit test classes with @Produces @TestBean fields
                    // from other tests (they are nested in other test's context)
                    if (hasTestBeanField(cls) && !cls.equals(testClass)) {
                        continue;
                    }
                    beanClasses.add(cls);
                }
            } else {
                // Whitelist mode: only add selected beans
                beanClasses.addAll(selectedAlternatives);
                for (InlineField f : inlineFields) {
                    beanClasses.add(f.fieldType());
                }
            }

            // Build ArC and boot the container
            ClassLoader oldTccl = buildAndBoot(testClass, beanClasses,
                    selectedAlternatives, inlineFields, addTestClass, limitToTestBeans);
            context.getStore(NS).put(KEY_OLD_TCCL, oldTccl);
        });
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (isQuarkusManaged(context)) {
            return;
        }
        try {
            ArcContainer container = Arc.container();
            if (container != null) {
                container.requestContext().activate();
            }
        } catch (Exception e) {
            // Container not running
        }
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        if (isQuarkusManaged(context)) {
            return;
        }
        ArcContainer container = Arc.container();
        if (container == null) {
            return;
        }
        // Inject @Inject fields using ArC's programmatic lookup
        injectFields(testInstance, testInstance.getClass(), container);
    }

    /**
     * Checks whether the test class has a Quarkus test annotation,
     * detected by name to avoid a compile-time dependency on the
     * Quarkus test framework.
     */
    private static boolean isQuarkusManagedContainer(Class<?> testClass) {
        for (Annotation ann : testClass.getAnnotations()) {
            String name = ann.annotationType().getName();
            for (String quarkusAnnotation : QUARKUS_TEST_ANNOTATIONS) {
                if (name.equals(quarkusAnnotation)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isQuarkusManaged(ExtensionContext context) {
        return Boolean.TRUE.equals(context.getStore(NS).get(KEY_QUARKUS_MANAGED, Boolean.class));
    }

    private void injectFields(Object testInstance, Class<?> clazz, ArcContainer container) {
        if (clazz == null || clazz == Object.class) {
            return;
        }
        // Inject superclass fields first
        injectFields(testInstance, clazz.getSuperclass(), container);

        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(jakarta.inject.Inject.class)) {
                continue;
            }
            field.setAccessible(true);
            try {
                // Collect qualifiers
                List<Annotation> qualifiers = new java.util.ArrayList<>();
                for (Annotation ann : field.getAnnotations()) {
                    if (ann.annotationType().isAnnotationPresent(jakarta.inject.Qualifier.class)) {
                        qualifiers.add(ann);
                    }
                }
                Annotation[] qualifierArray = qualifiers.toArray(new Annotation[0]);

                Object value = container.select(field.getType(), qualifierArray).get();
                field.set(testInstance, value);
            } catch (Exception e) {
                LOG.warning("[DynamicTestBean] Failed to inject field "
                        + clazz.getSimpleName() + "." + field.getName() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (isQuarkusManaged(context)) {
            return;
        }
        try {
            ArcContainer container = Arc.container();
            if (container != null) {
                container.requestContext().terminate();
            }
        } catch (Exception e) {
            // Container not running
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (isQuarkusManaged(context)) {
            return;
        }
        Arc.shutdown();
        ClassLoader oldTccl = context.getStore(NS).remove(KEY_OLD_TCCL, ClassLoader.class);
        if (oldTccl != null) {
            Thread.currentThread().setContextClassLoader(oldTccl);
        }
    }

    // ======================== ArC Build + Boot ========================

    private ClassLoader buildAndBoot(Class<?> testClass,
                                     Set<Class<?>> beanClasses,
                                     Set<Class<?>> selectedAlternatives,
                                     Set<InlineField> inlineFields,
                                     boolean addTestClass,
                                     boolean limitToTestBeans) {
        // Make sure Arc is down
        Arc.shutdown();

        ClassLoader old = Thread.currentThread().getContextClassLoader();

        try {
            // Build Jandex index
            Index rawIndex = indexClasses(beanClasses);
            IndexView immutableIndex = BeanArchives.buildImmutableBeanArchiveIndex(rawIndex);
            IndexView computingIndex = BeanArchives.buildComputingBeanArchiveIndex(
                    old, new ConcurrentHashMap<>(), immutableIndex);

            // Determine output paths based on the test class location
            File testOutputDirectory = resolveTestOutputDirectory(testClass);
            File generatedSourcesDir = new File(testOutputDirectory.getParentFile(), "generated-arc-sources");
            File componentsProviderFile = new File(
                    generatedSourcesDir + "/" + testClass.getPackageName().replace('.', '/'),
                    ComponentsProvider.class.getSimpleName());

            // Set up the BeanProcessor
            Set<DotName> altDotNames = new HashSet<>();
            for (Class<?> alt : selectedAlternatives) {
                altDotNames.add(DotName.createSimple(alt.getName()));
            }

            BeanProcessor.Builder builder = BeanProcessor.builder()
                    .setName(testClass.getName().replace('.', '_'))
                    .setImmutableBeanArchiveIndex(immutableIndex)
                    .setComputingBeanArchiveIndex(computingIndex);

            // Add @Priority to selected @Alternative classes
            if (!altDotNames.isEmpty()) {
                builder.addAnnotationTransformation(
                        AnnotationTransformation.forClasses()
                                .whenClass(c -> altDotNames.contains(c.name()))
                                .transform(ctx -> {
                                    if (!ctx.hasAnnotation(DotNames.PRIORITY)) {
                                        ctx.add(AnnotationInstance.builder(DotNames.PRIORITY)
                                                .add("value", Integer.MAX_VALUE).build());
                                    }
                                    // Add @Dependent scope if no scope present
                                    if (!hasScope(ctx, computingIndex)) {
                                        ctx.add(AnnotationInstance.builder(DotNames.SINGLETON).build());
                                    }
                                }));
            }

            // Exclude unselected @Alternative classes that type-clash with selected ones
            if (!selectedAlternatives.isEmpty() && !limitToTestBeans) {
                builder.addExcludeType(classInfo -> {
                    if (!classInfo.hasAnnotation(DotNames.ALTERNATIVE)) {
                        return false;
                    }
                    if (altDotNames.contains(classInfo.name())) {
                        return false; // Don't exclude selected ones
                    }
                    if (classInfo.hasAnnotation(DotNames.PRIORITY)) {
                        return false; // Has @Priority, CDI-managed
                    }
                    return hasTypeClash(classInfo, selectedAlternatives, computingIndex);
                });
            }

            // BeanRegistrar for mock beans + inline producer fields
            builder.addBeanRegistrar(new BeanRegistrar() {
                @Override
                public void register(RegistrationContext registrationContext) {
                    List<BeanInfo> beans = registrationContext.beans().collect();

                    // Register inline @TestBean field beans
                    for (InlineField field : inlineFields) {
                        DotName typeName = DotName.createSimple(field.fieldType().getName());
                        BeanConfigurator<Object> configurator = registrationContext
                                .configure(typeName)
                                .scope(Singleton.class)
                                .addType(ClassType.create(typeName))
                                .defaultBean()
                                .creator(InlineFieldBeanCreator.class)
                                .param("declaringClass", field.declaringClass().getName())
                                .param("fieldName", field.fieldName());

                        // Add qualifiers
                        for (Annotation q : field.qualifiers()) {
                            configurator.addQualifier(
                                    AnnotationInstance.builder(DotName.createSimple(q.annotationType().getName()))
                                            .build());
                        }
                        if (field.qualifiers().isEmpty()) {
                            configurator.addQualifier(AnnotationInstance.builder(DotNames.DEFAULT).build());
                        }
                        configurator.done();

                        LOG.info("[DynamicTestBean] Registered inline @TestBean field: "
                                + field.declaringClass().getSimpleName() + "." + field.fieldName());
                    }

                    // Collect types already covered by inline fields
                    Set<DotName> inlineFieldTypes = new HashSet<>();
                    for (InlineField field : inlineFields) {
                        inlineFieldTypes.add(DotName.createSimple(field.fieldType().getName()));
                    }

                    // Re-read beans after inline registrations
                    beans = registrationContext.beans().collect();

                    // Collect @Nonbinding members for qualifier deduplication
                    io.quarkus.arc.processor.BeanDeployment deployment =
                            registrationContext.get(io.quarkus.arc.processor.BuildExtension.Key.DEPLOYMENT);
                    Map<DotName, Set<String>> nonbindingMembers = new HashMap<>();
                    for (ClassInfo qualifier : deployment.getQualifiers()) {
                        Set<String> nb = new HashSet<>();
                        for (org.jboss.jandex.MethodInfo m : qualifier.methods()) {
                            if (m.hasAnnotation(DotNames.NONBINDING)) {
                                nb.add(m.name());
                            }
                        }
                        nb.addAll(deployment.getQualifierNonbindingMembers(qualifier.name()));
                        if (!nb.isEmpty()) {
                            nonbindingMembers.put(qualifier.name(), nb);
                        }
                    }

                    // Register mock beans for unsatisfied injection points
                    // Key: type + qualifier names (ignoring @Nonbinding member values)
                    Set<String> registeredMockKeys = new HashSet<>();
                    for (InjectionPointInfo ip : registrationContext.getInjectionPoints()) {
                        BuiltinBean builtin = BuiltinBean.resolve(ip);
                        if (builtin != null && builtin != BuiltinBean.INSTANCE && builtin != BuiltinBean.LIST) {
                            continue;
                        }

                        Type requiredType = ip.getRequiredType();
                        Set<AnnotationInstance> requiredQualifiers = ip.getRequiredQualifiers();

                        if (isBuiltInType(requiredType)) {
                            continue;
                        }

                        // Skip types already provided by inline @TestBean fields
                        if (inlineFieldTypes.contains(requiredType.name())) {
                            continue;
                        }

                        if (isSatisfied(requiredType, requiredQualifiers, beans)) {
                            continue;
                        }

                        // Deduplicate considering @Nonbinding
                        String mockKey = buildMockKey(requiredType, requiredQualifiers, nonbindingMembers);
                        if (!registeredMockKeys.add(mockKey)) {
                            continue;
                        }

                        ClassInfo implClass = computingIndex.getClassByName(requiredType.name());
                        if (implClass == null) {
                            continue;
                        }

                        // For qualifiers with @Nonbinding members, strip the
                        // differing values so only one mock bean is registered
                        Set<AnnotationInstance> cleanedQualifiers = stripNonbindingValues(
                                requiredQualifiers, nonbindingMembers);

                        registrationContext.configure(requiredType.name())
                                .scope(Singleton.class)
                                .addType(requiredType)
                                .qualifiers(cleanedQualifiers.toArray(new AnnotationInstance[0]))
                                .creator(MockBeanCreator.class)
                                .param("implementationClass", implClass)
                                .defaultBean()
                                .done();

                        LOG.info("[DynamicTestBean] Registered mock for: " + requiredType);
                    }
                }
            });

            // Don't remove unused beans (we want all test beans available)
            builder.setRemoveUnusedBeans(false);

            // Output handler
            builder.setOutput(new ResourceOutput() {
                @Override
                public void writeResource(Resource resource) throws IOException {
                    switch (resource.getType()) {
                        case JAVA_CLASS:
                            resource.writeTo(testOutputDirectory);
                            break;
                        case SERVICE_PROVIDER:
                            if (resource.getName().endsWith(ComponentsProvider.class.getName())) {
                                componentsProviderFile.getParentFile().mkdirs();
                                try (FileOutputStream out = new FileOutputStream(componentsProviderFile)) {
                                    out.write(resource.getData());
                                }
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported: " + resource.getType());
                    }
                }
            });

            // Process
            BeanProcessor processor = builder.build();
            processor.process();

            // Set up classloader and boot ArC
            ArcTestClassLoader testClassLoader = new ArcTestClassLoader(old, componentsProviderFile);
            Thread.currentThread().setContextClassLoader(testClassLoader);

            Arc.initialize(ArcInitConfig.builder().setTestMode(true).build());

            LOG.info("[DynamicTestBean] ArC container started for: " + testClass.getSimpleName());

        } catch (Exception e) {
            LOG.severe("[DynamicTestBean] Failed to build and boot ArC container: " + e.getMessage());
            throw new RuntimeException("Failed to build and boot ArC container", e);
        }

        return old;
    }

    private static File resolveTestOutputDirectory(Class<?> testClass) {
        String testClassResource = testClass.getName().replace('.', '/') + ".class";
        java.net.URL testClassUrl = testClass.getClassLoader().getResource(testClassResource);
        if (testClassUrl != null) {
            try {
                String testClassPath = new File(testClassUrl.toURI()).getAbsolutePath();
                int targetIdx = testClassPath.indexOf(TARGET_TEST_CLASSES);
                if (targetIdx > 0) {
                    return new File(
                            testClassPath.substring(0, targetIdx) + TARGET_TEST_CLASSES);
                }
            } catch (java.net.URISyntaxException e) {
                // fall through to default
            }
        }
        return new File(TARGET_TEST_CLASSES);
    }

    // ======================== Jandex Indexing ========================

    private Index indexClasses(Set<Class<?>> classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            indexClass(indexer, clazz);
        }
        return indexer.complete();
    }

    private void indexClass(Indexer indexer, Class<?> clazz) throws IOException {
        if (clazz == null || clazz == Object.class) {
            return;
        }
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = getClass().getClassLoader();
        }
        try (InputStream stream = cl.getResourceAsStream(resourceName)) {
            if (stream != null) {
                indexer.index(stream);
            }
        }
        // Index superclass and interfaces for type hierarchy
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            indexClass(indexer, clazz.getSuperclass());
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            indexClass(indexer, iface);
        }
    }

    // ======================== TestBean Collection ========================

    /**
     * Collects all @TestBean-selected alternative classes from the test class,
     * recursively walking meta-annotations.
     */
    static Set<Class<?>> getSelectedAlternatives(Class<?> testClass) {
        Set<Class<?>> selected = new LinkedHashSet<>();
        Set<Class<? extends Annotation>> visited = new LinkedHashSet<>();
        collectTestBeans(testClass.getAnnotations(), selected, visited);
        return selected;
    }

    private static void collectTestBeans(Annotation[] annotations,
                                         Set<Class<?>> selected,
                                         Set<Class<? extends Annotation>> visited) {
        for (Annotation ann : annotations) {
            Class<? extends Annotation> annType = ann.annotationType();

            if (ann instanceof TestBean tb) {
                if (tb.bean() != void.class) {
                    selected.add(tb.bean());
                }
                if (tb.beanProducer() != void.class) {
                    selected.add(tb.beanProducer());
                }
            } else if (ann instanceof TestBeans tbs) {
                for (TestBean tb : tbs.value()) {
                    if (tb.bean() != void.class) {
                        selected.add(tb.bean());
                    }
                    if (tb.beanProducer() != void.class) {
                        selected.add(tb.beanProducer());
                    }
                }
            }

            // Recurse into meta-annotations
            if (!visited.contains(annType)
                    && !annType.getName().startsWith("java.")
                    && !annType.getName().startsWith("jakarta.")) {
                visited.add(annType);
                collectTestBeans(annType.getAnnotations(), selected, visited);
            }
        }
    }

    // ======================== Inline Fields ========================

    private static Set<InlineField> collectInlineFields(Class<?> testClass) {
        Set<InlineField> fields = new LinkedHashSet<>();
        for (Field field : testClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(TestBean.class)) {
                continue;
            }
            if (!Modifier.isStatic(field.getModifiers())) {
                LOG.warning("[DynamicTestBean] @TestBean field must be static: "
                        + testClass.getSimpleName() + "." + field.getName());
                continue;
            }

            Set<Annotation> qualifiers = new LinkedHashSet<>();
            for (Annotation ann : field.getAnnotations()) {
                if (ann.annotationType().isAnnotationPresent(jakarta.inject.Qualifier.class)) {
                    qualifiers.add(ann);
                }
            }
            qualifiers.removeIf(a -> a.annotationType() == TestBean.class);

            fields.add(new InlineField(testClass, field.getName(), field.getType(), qualifiers));
        }
        return fields;
    }

    private static boolean hasTestBeanField(Class<?> cls) {
        for (Field field : cls.getDeclaredFields()) {
            if (field.isAnnotationPresent(TestBean.class)) {
                return true;
            }
        }
        return false;
    }

    // ======================== Class Discovery ========================

    /**
     * Discovers all classes from the test-classes directory and from
     * classpath JARs that contain addon usecase/test classes.
     */
    private static Set<Class<?>> discoverTestClasses(Class<?> testClass) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        ClassLoader cl = testClass.getClassLoader();
        try {
            // 1. Scan the test-classes and classes directories (local classes)
            String testClassResource = testClass.getName().replace('.', '/') + ".class";
            java.net.URL url = cl.getResource(testClassResource);
            if (url != null) {
                String path = url.getFile();
                int idx = path.indexOf(TARGET_TEST_CLASSES);
                if (idx >= 0) {
                    String projectBase = path.substring(0, idx);
                    // Scan test-classes (test sources)
                    File testClassesDir = new File(projectBase + TARGET_TEST_CLASSES);
                    if (testClassesDir.isDirectory()) {
                        scanDirectory(testClassesDir, testClassesDir, cl, classes);
                    }
                    // Scan classes (main sources — application beans)
                    File classesDir = new File(projectBase + TARGET_CLASSES);
                    if (classesDir.isDirectory()) {
                        scanDirectory(classesDir, classesDir, cl, classes);
                    }
                }
            }

            // 2. Scan classpath JARs for bean classes (e.g., dependency JARs,
            //    test-jars with shared beans). Only JARs containing a beans.xml
            //    or CDI-annotated classes are relevant.
            scanClasspathJars(cl, classes);
        } catch (Exception e) {
            LOG.warning("[DynamicTestBean] Failed to discover test classes: " + e.getMessage());
        }
        return classes;
    }

    /**
     * Scans all JARs on the classpath that contain a {@code META-INF/beans.xml}
     * (CDI bean archive marker). All {@code .class} entries in those JARs are
     * loaded, excluding JDK/vendor/addon internal packages.
     */
    private static void scanClasspathJars(ClassLoader cl, Set<Class<?>> classes) {
        try {
            java.util.Enumeration<java.net.URL> beansXmls = cl.getResources("META-INF/beans.xml");
            Set<String> scannedJars = new HashSet<>();
            while (beansXmls.hasMoreElements()) {
                java.net.URL beansUrl = beansXmls.nextElement();
                if (!"jar".equals(beansUrl.getProtocol())) {
                    continue;
                }
                java.net.JarURLConnection conn =
                        (java.net.JarURLConnection) beansUrl.openConnection();
                String jarPath = conn.getJarFileURL().toString();
                if (!scannedJars.add(jarPath)) {
                    continue; // Already scanned this JAR
                }
                try (java.util.jar.JarFile jar = conn.getJarFile()) {
                    java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        java.util.jar.JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!name.endsWith(".class") || name.contains("$")) {
                            continue;
                        }
                        if (isVendorOrJdkPath(name)) {
                            continue;
                        }
                        String className = name.replace('/', '.').replace(".class", "");
                        try {
                            classes.add(cl.loadClass(className));
                        } catch (ClassNotFoundException | NoClassDefFoundError e) {
                            // Skip — class may have unsatisfied dependencies
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("[DynamicTestBean] Failed to scan classpath JARs: " + e.getMessage());
        }
    }

    /** Package prefix of this addon's internal classes (derived, not hardcoded). */
    private static final String ADDON_INTERNAL_PREFIX =
            DynamicTestBeanJUnitExtension.class.getPackageName().replace('.', '/') + "/";

    private static boolean isVendorOrJdkPath(String classFilePath) {
        return classFilePath.startsWith("java/")
                || classFilePath.startsWith("javax/")
                || classFilePath.startsWith("jakarta/")
                || classFilePath.startsWith("io/quarkus/arc/")
                || classFilePath.startsWith("org/jboss/weld/")
                || classFilePath.startsWith("org/apache/webbeans/")
                || classFilePath.startsWith("org/apache/deltaspike/")
                || classFilePath.startsWith(ADDON_INTERNAL_PREFIX)
                || classFilePath.startsWith("META-INF/");
    }

    private static final int MAX_SCAN_DEPTH = 50;

    private static void scanDirectory(File root, File dir, ClassLoader cl, Set<Class<?>> classes) {
        scanDirectory(root, dir, cl, classes, 0);
    }

    private static void scanDirectory(File root, File dir, ClassLoader cl,
                                      Set<Class<?>> classes, int depth) {
        if (depth > MAX_SCAN_DEPTH) {
            LOG.warning("[DynamicTestBean] Max scan depth reached, possible symlink loop: " + dir);
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(root, file, cl, classes, depth + 1);
            } else if (file.getName().endsWith(".class")) {
                String relativePath = root.toURI().relativize(file.toURI()).getPath();
                String className = relativePath.replace('/', '.').replace(".class", "");
                // Skip internal/generated classes
                if (className.contains("$") || className.startsWith("META-INF")) {
                    continue;
                }
                try {
                    classes.add(cl.loadClass(className));
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // Skip
                }
            }
        }
    }

    // ======================== Type Utilities ========================

    /**
     * Builds a deduplication key for mock beans, ignoring @Nonbinding member values.
     */
    private static String buildMockKey(Type type, Set<AnnotationInstance> qualifiers,
                                       Map<DotName, Set<String>> nonbindingMembers) {
        StringBuilder sb = new StringBuilder(type.toString());
        // Sort qualifiers by name for consistent key
        qualifiers.stream()
                .sorted(java.util.Comparator.comparing(a -> a.name().toString()))
                .forEach(q -> {
                    sb.append(':').append(q.name());
                    Set<String> nb = nonbindingMembers.getOrDefault(q.name(), Collections.emptySet());
                    // Only include binding member values in the key
                    for (AnnotationValue v : q.values()) {
                        if (!nb.contains(v.name())) {
                            sb.append('.').append(v.name()).append('=').append(v.toString());
                        }
                    }
                });
        return sb.toString();
    }

    /**
     * Strips @Nonbinding member values from qualifier annotations so that
     * ArC doesn't create separate beans for each @Nonbinding value.
     */
    private static Set<AnnotationInstance> stripNonbindingValues(
            Set<AnnotationInstance> qualifiers, Map<DotName, Set<String>> nonbindingMembers) {
        Set<AnnotationInstance> result = new LinkedHashSet<>();
        for (AnnotationInstance q : qualifiers) {
            Set<String> nb = nonbindingMembers.get(q.name());
            if (nb != null && !nb.isEmpty()) {
                // Rebuild annotation without @Nonbinding members
                var builder = AnnotationInstance.builder(q.name());
                for (AnnotationValue v : q.values()) {
                    if (!nb.contains(v.name())) {
                        builder.add(v);
                    }
                }
                result.add(builder.build());
            } else {
                result.add(q);
            }
        }
        return result;
    }

    private static boolean isBuiltInType(Type type) {
        String name = type.name().toString();
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jakarta.")
                || name.startsWith("io.quarkus.arc.");
    }

    private static boolean isSatisfied(Type requiredType,
                                       Set<AnnotationInstance> requiredQualifiers,
                                       List<BeanInfo> beans) {
        for (BeanInfo bean : beans) {
            if (bean.getTypes().stream().anyMatch(t -> t.equals(requiredType))) {
                // Simple type match — for a more robust check we'd use BeanResolver
                return true;
            }
        }
        return false;
    }

    private static final DotName DEPENDENT = DotName.createSimple("jakarta.enterprise.context.Dependent");
    private static final DotName REQUEST_SCOPED = DotName.createSimple("jakarta.enterprise.context.RequestScoped");

    private static boolean hasScope(AnnotationTransformation.TransformationContext ctx,
                                    IndexView index) {
        // Check common scopes
        return ctx.hasAnnotation(DotNames.SINGLETON)
                || ctx.hasAnnotation(DEPENDENT)
                || ctx.hasAnnotation(DotNames.APPLICATION_SCOPED)
                || ctx.hasAnnotation(REQUEST_SCOPED);
    }

    private static boolean hasTypeClash(ClassInfo altClass,
                                        Set<Class<?>> selected,
                                        IndexView index) {
        Set<DotName> altTypes = collectBeanTypes(altClass);
        for (Class<?> sel : selected) {
            ClassInfo selInfo = index.getClassByName(DotName.createSimple(sel.getName()));
            if (selInfo != null) {
                Set<DotName> selTypes = collectBeanTypes(selInfo);
                for (DotName t : altTypes) {
                    if (!t.equals(DotNames.OBJECT) && selTypes.contains(t)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Set<DotName> collectBeanTypes(ClassInfo classInfo) {
        Set<DotName> types = new HashSet<>();
        types.add(classInfo.name());
        for (DotName iface : classInfo.interfaceNames()) {
            types.add(iface);
        }
        if (classInfo.superName() != null) {
            types.add(classInfo.superName());
        }
        types.add(DotNames.OBJECT);
        return types;
    }

    // ======================== Records ========================

    record InlineField(Class<?> declaringClass, String fieldName,
                       Class<?> fieldType, Set<Annotation> qualifiers) {
    }

    // ======================== ClassLoader ========================

    /**
     * ClassLoader that resolves the generated {@link ComponentsProvider}
     * service file, allowing ArC to find the generated bean definitions.
     */
    static class ArcTestClassLoader extends ClassLoader {
        private final File componentsProviderFile;

        ArcTestClassLoader(ClassLoader parent, File componentsProviderFile) {
            super(parent);
            this.componentsProviderFile = componentsProviderFile;
        }

        @Override
        public java.util.Enumeration<java.net.URL> getResources(String name) throws IOException {
            if (("META-INF/services/" + ComponentsProvider.class.getName()).equals(name)) {
                if (componentsProviderFile.canRead()) {
                    var parentResources = super.getResources(name);
                    var list = new java.util.ArrayList<java.net.URL>();
                    while (parentResources.hasMoreElements()) {
                        list.add(parentResources.nextElement());
                    }
                    list.add(componentsProviderFile.toURI().toURL());
                    return Collections.enumeration(list);
                }
            }
            return super.getResources(name);
        }
    }
}
