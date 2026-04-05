# Porting Plan: dynamic-cdi-test-bean-addon → Quarkus Extension

**Target:** Quarkus 3.x (latest stable), JVM mode only  
**Source:** `dynamic-cdi-test-bean-addon` (CDI SE portable extension)

---

## 1. Feature Inventory (What the Addon Does)

| # | Feature | Current Implementation | Used By |
|---|---------|----------------------|---------|
| F1 | **Auto-mock unsatisfied injection points** | `DynamicTestBeanExtension`: collects all IPs via `ProcessInjectionPoint`/`ProcessBean`, then in `AfterBeanDiscovery` registers a `MockBean<T>` (Mockito-backed, `@RequestScoped`) for each unsatisfied IP | All tests implicitly |
| F2 | **Activate `@Alternative` beans per test** | `@TestBean(bean = X.class)` on test class → `AfterTypeDiscovery.getAlternatives().add(X)` | `TestBeanTest`, `TypedAlternativeTest`, `PriorityAlternativeTest` |
| F3 | **Activate `@Alternative` producer beans** | `@TestBean(beanProducer = X.class)` → same as F2 but for producer classes | `ProducerTestBeanTest` |
| F4 | **Inline producer fields** | `@Produces @TestBean static Greeting mock = ...` on test class → reflectively reads field value, registers as `@Singleton` synthetic bean with qualifiers carried over | `InlineProducerTest`, `InlineProducerWithStubbingTest`, isolation tests |
| F5 | **Whitelist mode** | `@EnableTestBeans(limitToTestBeans = true)` → vetoes ALL beans except `@TestBean`-declared ones and internals | `WhitelistModeTest` |
| F6 | **Veto unselected alternatives** | In normal mode, vetoes `@Alternative` beans whose types clash with `@TestBean`-selected ones (prevents ambiguous resolution) | `AlternativeVetoTest` |
| F7 | **Meta-annotation support** | Recursive annotation walking to collect `@TestBean` from meta-annotations (e.g., `@GreetingMocks` → `@TestBean(bean = CustomGreeting.class)`) | `MetaAnnotationSimpleTest`, `MetaAnnotationNestedTest`, `MetaAnnotationDeepWithDuplicatesTest` |
| F8 | **Test class as CDI bean** | `@EnableTestBeans(addTestClass = true)` (default) → registers test class as `@Singleton`, injects `@Inject` fields | `InjectIntoTestClassTest`, most tests |
| F9 | **Container lifecycle management** | `@EnableTestBeans(manageContainer = true)` (default) → boots CDI SE container in `@BeforeAll`, manages request scope per test, shuts down in `@AfterAll` | All tests |
| F10 | **`@Nonbinding` qualifier semantics** | `InjectionPointKey` respects `@Nonbinding` on qualifier members for deduplication | Qualifier tests |

---

## 2. Architecture: CDI SE → Quarkus Mapping

### 2.1 Current Architecture (3 classes + 3 annotations)

```
@EnableTestBeans ──@ExtendWith──→ DynamicTestBeanJUnitExtension
                                    ├── beforeAll: sets DynamicTestBeanContext, boots SeContainer
                                    ├── beforeEach: activates RequestContext
                                    ├── postProcessTestInstance: injects @Inject fields
                                    ├── afterEach: deactivates RequestContext
                                    └── afterAll: closes container, resets context

DynamicTestBeanContext (static volatile bridge)
    ├── activeTestClass
    ├── addTestClass / limitToTestBeans / manageContainer

DynamicTestBeanExtension (CDI portable extension, SPI-registered)
    ├── BeforeBeanDiscovery: force-discover scope-less alternatives
    ├── ProcessAnnotatedType: veto unselected/clashing alternatives (or whitelist-veto)
    ├── AfterTypeDiscovery: enable @TestBean alternatives
    ├── ProcessInjectionPoint: collect all IPs
    ├── ProcessBean: collect inherited generic IPs
    └── AfterBeanDiscovery: register test class bean, inline producers, mock beans
```

### 2.2 Target Architecture (Quarkus Extension)

```
runtime module:
    @EnableTestBeans          (marker annotation, no @ExtendWith)
    @TestBean / @TestBeans    (reused as-is)
    MockBeanCreator           (BeanCreator<Object> — calls Mockito.mock())
    InlineFieldBeanCreator    (BeanCreator<Object> — reads static field value)

deployment module:
    DynamicTestBeanProcessor  (@BuildStep methods)
        ├── collectTestBeanAnnotations()     — scan Jandex for @EnableTestBeans/@TestBean
        ├── enableAlternatives()             — produce config override for quarkus.arc.selected-alternatives
        ├── transformAlternatives()          — add @Priority to selected alternatives via AnnotationsTransformerBuildItem
        ├── vetoUnselectedAlternatives()     — produce ExcludedTypeBuildItem for clashing alternatives
        ├── whitelistMode()                  — produce ExcludedTypeBuildItem for everything except whitelist
        ├── registerInlineProducerBeans()    — produce SyntheticBeanBuildItem for @TestBean static fields
        └── registerMockBeans()              — produce SyntheticBeanBuildItem for unsatisfied IPs

NO DynamicTestBeanContext    (not needed — build steps read annotations directly from Jandex)
NO DynamicTestBeanJUnitExtension (not needed — @QuarkusTest handles container + injection)
```

---

## 3. Module Structure

```
dynamic-test-bean-quarkus/
├── pom.xml                                      (parent POM)
├── runtime/
│   ├── pom.xml
│   └── src/main/java/org/os890/cdi/addon/dynamictestbean/quarkus/
│       ├── EnableTestBeans.java
│       ├── TestBean.java
│       ├── TestBeans.java
│       └── runtime/
│           ├── MockBeanCreator.java
│           └── InlineFieldBeanCreator.java
├── deployment/
│   ├── pom.xml
│   └── src/main/java/org/os890/cdi/addon/dynamictestbean/quarkus/deployment/
│       ├── DynamicTestBeanProcessor.java
│       └── TestBeanConfig.java                  (collected config record)
└── integration-tests/
    ├── pom.xml
    └── src/test/java/...                        (ported tests)
```

---

## 4. Detailed Implementation Steps

### Step 1: Parent POM

```xml
<project>
    <groupId>org.os890.cdi.addon</groupId>
    <artifactId>dynamic-test-bean-quarkus-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <quarkus.version>3.21.3</quarkus.version>  <!-- latest stable, verify at build time -->
        <mockito.version>5.23.0</mockito.version>
        <compiler.release>17</compiler.release>     <!-- Quarkus 3.x minimum -->
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-bom</artifactId>
                <version>${quarkus.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <modules>
        <module>runtime</module>
        <module>deployment</module>
        <module>integration-tests</module>
    </modules>
</project>
```

### Step 2: Runtime Module POM

```xml
<project>
    <parent>
        <groupId>org.os890.cdi.addon</groupId>
        <artifactId>dynamic-test-bean-quarkus-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>dynamic-test-bean-quarkus</artifactId>

    <dependencies>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>${mockito.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-extension-maven-plugin</artifactId>
                <version>${quarkus.version}</version>
                <executions>
                    <execution>
                        <goals><goal>extension-descriptor</goal></goals>
                        <configuration>
                            <deployment>${project.groupId}:${project.artifactId}-deployment:${project.version}</deployment>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 3: Deployment Module POM

```xml
<project>
    <parent>
        <groupId>org.os890.cdi.addon</groupId>
        <artifactId>dynamic-test-bean-quarkus-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>dynamic-test-bean-quarkus-deployment</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.os890.cdi.addon</groupId>
            <artifactId>dynamic-test-bean-quarkus</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc-deployment</artifactId>
        </dependency>
        <!-- Test -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### Step 4: Runtime Annotations

#### `@EnableTestBeans` (adapted)

```java
package org.os890.cdi.addon.dynamictestbean.quarkus;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marker annotation for test classes. Must be used with @QuarkusTest.
 * Enables auto-mocking of unsatisfied injection points and @TestBean support.
 *
 * Replaces manageContainer (always true in Quarkus, managed by @QuarkusTest).
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface EnableTestBeans {
    /** Whether to register the test class as a CDI bean. Default true. */
    boolean addTestClass() default true;

    /** Whitelist mode: veto all beans except @TestBean-declared ones. Default false. */
    boolean limitToTestBeans() default false;
}
```

#### `@TestBean` and `@TestBeans` — copy as-is from original

No changes needed. Same package, same semantics.

#### `MockBeanCreator` (runtime)

```java
package org.os890.cdi.addon.dynamictestbean.quarkus.runtime;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import org.mockito.Mockito;

/**
 * BeanCreator that produces a Mockito mock for the configured type.
 * The target class name is passed via synthetic bean params.
 */
public class MockBeanCreator implements BeanCreator<Object> {
    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        // "mockType" param set by the deployment processor
        String className = context.getParams().get("mockType").toString();
        try {
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            return Mockito.mock(clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot create mock for: " + className, e);
        }
    }
}
```

**Note:** `SyntheticBeanBuildItem` supports passing params to `BeanCreator` via
`.param("mockType", className)` on the `ExtendedBeanConfigurator`. However, looking
at the API more closely, the params are set via the recorder pattern. Alternative
approach: use `.creator(mc -> mc.invokeStaticMethod(...))` bytecode generation, or
use a `Supplier` registered via a `@Recorder`. The simplest JVM-mode approach is
a recorder that captures the class name:

```java
// In DynamicTestBeanRecorder (runtime):
@Recorder
public class DynamicTestBeanRecorder {
    public <T> Supplier<T> mockSupplier(String className) {
        return () -> {
            try {
                @SuppressWarnings("unchecked")
                Class<T> clazz = (Class<T>) Thread.currentThread()
                        .getContextClassLoader().loadClass(className);
                return Mockito.mock(clazz);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public <T> Supplier<T> staticFieldSupplier(String declaringClass, String fieldName) {
        return () -> {
            try {
                Class<?> clazz = Thread.currentThread()
                        .getContextClassLoader().loadClass(declaringClass);
                java.lang.reflect.Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                T value = (T) f.get(null);
                return value;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
```

### Step 5: Deployment Processor — `DynamicTestBeanProcessor`

This is the core class. Each feature maps to one or more `@BuildStep` methods.

```java
package org.os890.cdi.addon.dynamictestbean.quarkus.deployment;

import io.quarkus.arc.deployment.*;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.jandex.*;
import org.os890.cdi.addon.dynamictestbean.quarkus.EnableTestBeans;
import org.os890.cdi.addon.dynamictestbean.quarkus.TestBean;
import org.os890.cdi.addon.dynamictestbean.quarkus.TestBeans;
import org.os890.cdi.addon.dynamictestbean.quarkus.runtime.DynamicTestBeanRecorder;

import java.util.*;

public class DynamicTestBeanProcessor {
    // DotName constants
    static final DotName ENABLE_TEST_BEANS = DotName.createSimple(EnableTestBeans.class);
    static final DotName TEST_BEAN = DotName.createSimple(TestBean.class);
    static final DotName TEST_BEANS = DotName.createSimple(TestBeans.class);
    static final DotName ALTERNATIVE = DotName.createSimple("jakarta.enterprise.inject.Alternative");
    static final DotName PRIORITY = DotName.createSimple("jakarta.annotation.Priority");

    // ... @BuildStep methods below
}
```

#### Build Step 5a: Scan Test Class Annotations

```java
/**
 * Finds the test class annotated with @EnableTestBeans in the Jandex index.
 * Collects all @TestBean declarations (including meta-annotations).
 * Produces a TestBeanConfig record for downstream steps.
 */
@BuildStep
TestBeanConfigBuildItem scanTestBeans(CombinedIndexBuildItem combinedIndex) {
    IndexView index = combinedIndex.getComputingIndex();

    // Find classes annotated with @EnableTestBeans
    Collection<AnnotationInstance> enableAnnotations = index.getAnnotations(ENABLE_TEST_BEANS);
    if (enableAnnotations.isEmpty()) {
        return null; // Extension not active
    }

    // There should be exactly one test class (per test run)
    AnnotationInstance enableAnn = enableAnnotations.iterator().next();
    ClassInfo testClass = enableAnn.target().asClass();

    boolean addTestClass = enableAnn.valueWithDefault(index, "addTestClass").asBoolean();
    boolean limitToTestBeans = enableAnn.valueWithDefault(index, "limitToTestBeans").asBoolean();

    // Collect all @TestBean declarations recursively (meta-annotation support)
    Set<String> selectedBeans = new LinkedHashSet<>();
    Set<String> selectedProducers = new LinkedHashSet<>();
    Set<DotName> visited = new HashSet<>();
    collectTestBeans(testClass, index, selectedBeans, selectedProducers, visited);

    // Collect inline @TestBean fields (static fields on test class)
    List<InlineProducerField> inlineFields = new ArrayList<>();
    for (FieldInfo field : testClass.fields()) {
        if (field.hasAnnotation(TEST_BEAN) && java.lang.reflect.Modifier.isStatic(field.flags())) {
            // Collect qualifier annotations on the field
            List<AnnotationInstance> qualifiers = new ArrayList<>();
            for (AnnotationInstance ann : field.annotations()) {
                // Check if it's a CDI qualifier (has @Qualifier meta-annotation)
                ClassInfo annClass = index.getClassByName(ann.name());
                if (annClass != null && annClass.hasAnnotation(
                        DotName.createSimple("jakarta.inject.Qualifier"))) {
                    qualifiers.add(ann);
                }
            }
            inlineFields.add(new InlineProducerField(
                    testClass.name().toString(),
                    field.name(),
                    field.type(),
                    qualifiers));
        }
    }

    return new TestBeanConfigBuildItem(
            testClass.name().toString(),
            addTestClass,
            limitToTestBeans,
            selectedBeans,
            selectedProducers,
            inlineFields);
}
```

**Meta-annotation recursive collection:**

```java
private void collectTestBeans(ClassInfo classInfo, IndexView index,
                              Set<String> beans, Set<String> producers,
                              Set<DotName> visited) {
    // Direct @TestBean annotations
    for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
        processTestBeanAnnotation(ann, index, beans, producers, visited);
    }
}

private void processTestBeanAnnotation(AnnotationInstance ann, IndexView index,
                                       Set<String> beans, Set<String> producers,
                                       Set<DotName> visited) {
    if (ann.name().equals(TEST_BEAN)) {
        AnnotationValue beanVal = ann.value("bean");
        if (beanVal != null && !beanVal.asClass().name().equals(DotName.createSimple("void"))) {
            beans.add(beanVal.asClass().name().toString());
        }
        AnnotationValue producerVal = ann.value("beanProducer");
        if (producerVal != null && !producerVal.asClass().name().equals(DotName.createSimple("void"))) {
            producers.add(producerVal.asClass().name().toString());
        }
    } else if (ann.name().equals(TEST_BEANS)) {
        for (AnnotationInstance nested : ann.value().asNestedArray()) {
            processTestBeanAnnotation(nested, index, beans, producers, visited);
        }
    } else {
        // Meta-annotation: recurse into the annotation type's own annotations
        DotName annType = ann.name();
        if (!visited.contains(annType)
                && !annType.toString().startsWith("java.")
                && !annType.toString().startsWith("jakarta.")) {
            visited.add(annType);
            ClassInfo annClassInfo = index.getClassByName(annType);
            if (annClassInfo != null) {
                for (AnnotationInstance metaAnn : annClassInfo.declaredAnnotations()) {
                    processTestBeanAnnotation(metaAnn, index, beans, producers, visited);
                }
            }
        }
    }
}
```

#### Build Step 5b: Enable Alternatives

```java
/**
 * F2/F3: Enable @TestBean-selected alternatives via annotation transformation.
 * Adds @Priority(Integer.MAX_VALUE) so ArC selects them automatically.
 */
@BuildStep
void enableAlternatives(TestBeanConfigBuildItem config,
                        BuildProducer<AnnotationsTransformerBuildItem> transformers) {
    if (config == null) return;

    Set<String> allAlternatives = new HashSet<>();
    allAlternatives.addAll(config.selectedBeans());
    allAlternatives.addAll(config.selectedProducers());

    if (allAlternatives.isEmpty()) return;

    Set<DotName> altDotNames = new HashSet<>();
    for (String name : allAlternatives) {
        altDotNames.add(DotName.createSimple(name));
    }

    // Add @Priority(MAX_VALUE) to selected @Alternative classes so ArC activates them
    transformers.produce(new AnnotationsTransformerBuildItem(
            AnnotationTransformation.forClasses()
                    .whenClass(c -> altDotNames.contains(c.name()))
                    .transform(ctx -> {
                        if (!ctx.hasAnnotation(PRIORITY)) {
                            ctx.add(AnnotationInstance.builder(PRIORITY)
                                    .add("value", Integer.MAX_VALUE).build());
                        }
                        // If it has @Alternative but no scope, add @Dependent
                        if (ctx.hasAnnotation(ALTERNATIVE) && !hasScope(ctx)) {
                            ctx.add(AnnotationInstance.builder(
                                    DotName.createSimple("jakarta.enterprise.context.Dependent"))
                                    .build());
                        }
                    })));
}
```

#### Build Step 5c: Veto Unselected / Whitelist Mode

```java
/**
 * F5: Whitelist mode — veto all application beans except selected ones.
 * F6: Normal mode — veto @Alternative beans that clash with selected ones.
 */
@BuildStep
void vetoUnselectedBeans(TestBeanConfigBuildItem config,
                         CombinedIndexBuildItem combinedIndex,
                         BuildProducer<ExcludedTypeBuildItem> excludedTypes) {
    if (config == null) return;
    IndexView index = combinedIndex.getComputingIndex();

    Set<String> selected = new HashSet<>();
    selected.addAll(config.selectedBeans());
    selected.addAll(config.selectedProducers());
    selected.add(config.testClassName());

    if (config.limitToTestBeans()) {
        // WHITELIST MODE: veto everything except selected beans and internals.
        // We iterate all known classes in the index and exclude non-selected ones.
        // ExcludedTypeBuildItem works by class name match.
        for (ClassInfo ci : index.getKnownClasses()) {
            String name = ci.name().toString();
            if (!selected.contains(name) && !isInternalType(name)) {
                excludedTypes.produce(new ExcludedTypeBuildItem(name));
            }
        }
    } else {
        // NORMAL MODE: veto @Alternative beans that clash with selected ones.
        // Only veto alternatives without @Priority that type-clash with a selected bean.
        for (AnnotationInstance altAnn : index.getAnnotations(ALTERNATIVE)) {
            if (altAnn.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            ClassInfo altClass = altAnn.target().asClass();
            String altName = altClass.name().toString();

            if (selected.contains(altName)) continue;  // This one is selected
            if (altClass.hasAnnotation(PRIORITY)) continue;  // Has @Priority, CDI-managed

            // Check type clash with any selected alternative
            if (hasTypeClashWithSelected(altClass, selected, index)) {
                excludedTypes.produce(new ExcludedTypeBuildItem(altName));
            }
        }
    }
}

private static boolean isInternalType(String name) {
    return name.startsWith("java.")
            || name.startsWith("javax.")
            || name.startsWith("jakarta.")
            || name.startsWith("io.quarkus.")
            || name.startsWith("org.jboss.")
            || name.startsWith("org.os890.cdi.addon.dynamictestbean.");
}
```

#### Build Step 5d: Register Inline Producer Fields

```java
/**
 * F4: Register @TestBean static fields as synthetic beans.
 * Uses a recorder to create a Supplier that reads the static field at runtime.
 */
@BuildStep
@Record(ExecutionTime.STATIC_INIT)
void registerInlineProducerBeans(TestBeanConfigBuildItem config,
                                 DynamicTestBeanRecorder recorder,
                                 BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
    if (config == null) return;

    for (InlineProducerField field : config.inlineFields()) {
        var builder = SyntheticBeanBuildItem
                .configure(DotName.createSimple(field.rawTypeName()))
                .addType(field.type())
                .addType(DotName.createSimple(Object.class))
                .scope(BuiltinScope.SINGLETON.getInfo())
                .unremovable()
                .supplier(recorder.staticFieldSupplier(
                        field.declaringClass(), field.fieldName()));

        // Add qualifiers from the field
        for (AnnotationInstance qualifier : field.qualifiers()) {
            builder.addQualifier(qualifier);
        }
        // If no custom qualifiers, add @Default
        if (field.qualifiers().isEmpty()) {
            builder.addQualifier(AnnotationInstance.builder(
                    DotName.createSimple("jakarta.enterprise.inject.Default")).build());
        }

        syntheticBeans.produce(builder.done());
    }
}
```

#### Build Step 5e: Register Mock Beans for Unsatisfied Injection Points (CRITICAL)

This is the most complex step. It must run during the bean registration phase
so it can detect which injection points are unsatisfied.

```java
/**
 * F1: Auto-mock unsatisfied injection points.
 *
 * This step participates in the BeanRegistrationPhase to access the
 * list of all injection points and check which ones are already satisfied.
 * For each unsatisfied IP, it registers a SyntheticBeanBuildItem backed
 * by Mockito.mock().
 *
 * IMPORTANT: This @BuildStep must consume BeanRegistrationPhaseBuildItem
 * and produce BeanConfiguratorBuildItem entries. The SyntheticBeanBuildItem
 * approach won't work here because we need to see what's already registered.
 * Instead, we use the BeanRegistrationPhaseBuildItem.getContext() API.
 */
@BuildStep
@Record(ExecutionTime.STATIC_INIT)
void registerMockBeans(TestBeanConfigBuildItem config,
                       DynamicTestBeanRecorder recorder,
                       BeanDiscoveryFinishedBuildItem beanDiscovery,
                       BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
    if (config == null) return;

    // Get all injection points from the discovery
    Collection<InjectionPointInfo> injectionPoints = beanDiscovery.getInjectionPoints();

    // Get the bean resolver to check satisfaction
    // BeanResolver beanResolver = beanDiscovery.getBeanResolver();

    Set<String> alreadyMocked = new HashSet<>();

    for (InjectionPointInfo ip : injectionPoints) {
        org.jboss.jandex.Type ipType = ip.getRequiredType();
        String rawTypeName = getRawTypeName(ipType);

        if (rawTypeName == null || isBuiltInType(rawTypeName)) continue;
        if (alreadyMocked.contains(makeKey(ipType, ip.getRequiredQualifiers()))) continue;

        // Check if this IP is satisfied by an existing bean
        if (!beanDiscovery.beanStream().matchType(ipType)
                .matchQualifiers(ip.getRequiredQualifiers()).isEmpty()) {
            continue;
        }

        // Not satisfied → register a Mockito mock
        var builder = SyntheticBeanBuildItem
                .configure(DotName.createSimple(rawTypeName))
                .addType(ipType)
                .addType(DotName.createSimple(Object.class))
                .scope(BuiltinScope.REQUEST.getInfo())  // @RequestScoped like original
                .unremovable()
                .defaultBean()  // yields to any real bean
                .supplier(recorder.mockSupplier(rawTypeName));

        // Add qualifiers from the injection point
        for (AnnotationInstance q : ip.getRequiredQualifiers()) {
            builder.addQualifier(q);
        }

        syntheticBeans.produce(builder.done());
        alreadyMocked.add(makeKey(ipType, ip.getRequiredQualifiers()));
    }
}
```

**Alternative approach if `BeanDiscoveryFinishedBuildItem` doesn't expose enough:**

Use `BeanRegistrationPhaseBuildItem` + `BeanConfiguratorBuildItem`:

```java
@BuildStep
void registerMockBeans(TestBeanConfigBuildItem config,
                       BeanRegistrationPhaseBuildItem beanRegistration,
                       BuildProducer<BeanConfiguratorBuildItem> configurators) {
    if (config == null) return;

    var context = beanRegistration.getContext();
    // Use context to get injection points and beans, register via BeanConfiguratorBuildItem
}
```

### Step 6: Integration Tests Module POM

```xml
<project>
    <parent>
        <groupId>org.os890.cdi.addon</groupId>
        <artifactId>dynamic-test-bean-quarkus-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>dynamic-test-bean-quarkus-integration-tests</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.os890.cdi.addon</groupId>
            <artifactId>dynamic-test-bean-quarkus</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.os890.cdi.addon</groupId>
            <artifactId>dynamic-test-bean-quarkus-deployment</artifactId>
            <version>${project.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.version}</version>
                <executions>
                    <execution>
                        <goals><goal>build</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 7: Test Porting Guide

Each original test class maps as follows:

| Original Test | Quarkus Test | Key Changes |
|--------------|-------------|-------------|
| `TestBeanTest` | `TestBeanTest` | Add `@QuarkusTest`, replace `CDI.current().select()` with `@Inject` |
| `InlineProducerTest` | `InlineProducerTest` | Add `@QuarkusTest`, keep `@Inject` (already used) |
| `WhitelistModeTest` | `WhitelistModeTest` | Add `@QuarkusTest`, replace `CDI.current().getBeanManager()` with `@Inject BeanManager` or `Arc.container()` |
| `ManualContainerTest` | Remove or adapt | Manual container management not applicable in Quarkus |
| `MetaAnnotation*Test` | Same | Add `@QuarkusTest`, meta-annotations work the same |
| `InjectIntoTestClassTest` | Same | Injection already works via `@QuarkusTest` |
| `PlainBeanTest` | Same | Add `@QuarkusTest` |
| `AlternativeVetoTest` | Same | Add `@QuarkusTest` |
| Isolation tests (A/B) | Same | Add `@QuarkusTest`, verify isolation still works |

**Example ported test:**

```java
@QuarkusTest
@EnableTestBeans
@TestBean(bean = CustomGreeting.class)
class TestBeanTest {

    @Inject
    GreetingConsumer consumer;

    @Test
    void replacementAlternativeIsInjected() {
        Greeting greeting = consumer.getGreeting();
        assertNotNull(greeting);
        assertEquals("Hello, world!", greeting.greet("world"));
    }
}
```

---

## 5. Quarkus API Reference (Key Classes)

| Class | Module | Purpose |
|-------|--------|---------|
| `SyntheticBeanBuildItem` | `quarkus-arc-deployment` | Register synthetic beans at build time |
| `SyntheticBeanBuildItem.ExtendedBeanConfigurator` | `quarkus-arc-deployment` | Fluent builder: `.configure()`, `.addType()`, `.scope()`, `.supplier()`, `.unremovable()`, `.defaultBean()`, `.done()` |
| `BeanCreator<T>` | `arc-runtime` | Interface for creating bean instances: `T create(SyntheticCreationalContext<T>)` |
| `ExcludedTypeBuildItem` | `quarkus-arc-deployment` | Veto/exclude beans by class name pattern |
| `AnnotationsTransformerBuildItem` | `quarkus-arc-deployment` | Transform annotations at build time (e.g., add `@Priority`) |
| `AdditionalBeanBuildItem` | `quarkus-arc-deployment` | Register additional classes for bean discovery |
| `UnremovableBeanBuildItem` | `quarkus-arc-deployment` | Prevent ArC from removing beans that appear unused |
| `BeanRegistrationPhaseBuildItem` | `quarkus-arc-deployment` | Hook into bean registration phase |
| `BeanDiscoveryFinishedBuildItem` | `quarkus-arc-deployment` | Access discovered beans and injection points |
| `CombinedIndexBuildItem` | `quarkus-core-deployment` | Access Jandex index (includes test classes) |
| `TestClassBeanBuildItem` | `quarkus-core-deployment` | Register test class as CDI bean |
| `@Recorder` | `quarkus-core` | Mark recorder class for build-time/runtime bridge |
| `BuiltinScope` | `arc-processor` | Enum: `DEPENDENT`, `SINGLETON`, `REQUEST`, `APPLICATION` |
| `AnnotationTransformation` | `arc-processor` | Modern API for `AnnotationsTransformerBuildItem` |

**Quarkus source references (for deeper understanding):**

| File | Path in ~/workspace/quarkus |
|------|-----------------------------|
| SyntheticBeanBuildItem | `extensions/arc/deployment/src/main/java/io/quarkus/arc/deployment/SyntheticBeanBuildItem.java` |
| ArcProcessor | `extensions/arc/deployment/src/main/java/io/quarkus/arc/deployment/ArcProcessor.java` |
| ArcConfig | `extensions/arc/deployment/src/main/java/io/quarkus/arc/deployment/ArcConfig.java` |
| SyntheticBeansProcessor | `extensions/arc/deployment/src/main/java/io/quarkus/arc/deployment/SyntheticBeansProcessor.java` |
| TestsAsBeansProcessor | `extensions/arc/deployment/src/main/java/io/quarkus/arc/deployment/TestsAsBeansProcessor.java` |
| BeanResolver | `independent-projects/arc/processor/src/main/java/io/quarkus/arc/processor/BeanResolver.java` |
| QuarkusTestExtension | `test-framework/junit/src/main/java/io/quarkus/test/junit/QuarkusTestExtension.java` |
| QuarkusMock | `test-framework/junit/src/main/java/io/quarkus/test/junit/QuarkusMock.java` |
| TestProfileAndProperties | `test-framework/junit/src/main/java/io/quarkus/test/junit/TestProfileAndProperties.java` |
| SchedulerProcessor | `extensions/scheduler/deployment/src/main/java/io/quarkus/scheduler/deployment/SchedulerProcessor.java` |

---

## 6. Implementation Order

1. **Create parent POM + module structure** (15 min)
2. **Port annotations to runtime module** — `@EnableTestBeans`, `@TestBean`, `@TestBeans` (10 min)
3. **Create `DynamicTestBeanRecorder`** — `mockSupplier()`, `staticFieldSupplier()` (10 min)
4. **Create `DynamicTestBeanProcessor` skeleton** — empty `@BuildStep` methods (10 min)
5. **Implement `scanTestBeans()`** — Jandex scanning + meta-annotation recursion (30 min)
6. **Implement `enableAlternatives()`** — `AnnotationsTransformerBuildItem` (15 min)
7. **Implement `vetoUnselectedBeans()`** — `ExcludedTypeBuildItem` (20 min)
8. **Implement `registerInlineProducerBeans()`** — `SyntheticBeanBuildItem` + recorder (20 min)
9. **Implement `registerMockBeans()`** — `BeanDiscoveryFinishedBuildItem` + `SyntheticBeanBuildItem` (45 min)
10. **Create integration tests module** — port 3-4 representative tests first (30 min)
11. **Port remaining tests** (30 min)
12. **Debug & fix** (60 min buffer)

---

## 7. Findings & Potential Blockers

### FINDING 1 — Auto-mocking timing (Medium Risk)

**Problem:** ArC validates injection points at build time. If an IP is unsatisfied,
the build fails before we can register a mock. We must produce `SyntheticBeanBuildItem`
entries before ArC's validation runs.

**Solution:** Use `BeanDiscoveryFinishedBuildItem` which fires AFTER bean discovery
but BEFORE validation. At this point we can inspect all IPs and register synthetic
beans. The `SyntheticBeansProcessor` in Quarkus itself follows this pattern.
Alternatively, use `BeanRegistrationPhaseBuildItem` + `BeanConfiguratorBuildItem`.

**Fallback:** If neither works cleanly, use a two-pass approach: first pass discovers
all types via Jandex index scanning (all `@Inject` fields and constructor params),
second pass (build step) registers mock beans for types with no matching bean class
in the index. This is less precise but avoids the timing issue.

### FINDING 2 — QuarkusMock only works for normal-scoped beans (Low Risk)

`QuarkusMock.installMockForType()` requires client proxies (`@ApplicationScoped` /
`@RequestScoped`). The inline producer fields in the original addon use `@Singleton`.
For the Quarkus port, inline producers should use `@ApplicationScoped` instead.
This is a minor behavioral change but ensures compatibility with `QuarkusMock`.

### FINDING 3 — Inline producer fields use runtime reflection (Low Risk for JVM)

Reading `static` field values via `Field.get(null)` works fine in JVM mode.
The recorder's `staticFieldSupplier()` captures the class/field name as strings
and resolves them at runtime. No native-mode concern since we're JVM-only.

### FINDING 4 — Jandex meta-annotation traversal (Low Risk)

Jandex `IndexView` doesn't have built-in recursive meta-annotation resolution.
We implement it manually by walking `ClassInfo.declaredAnnotations()` recursively.
This is straightforward — the original addon does the same with Java reflection.

### FINDING 5 — Whitelist mode scope (Medium Risk)

`ExcludedTypeBuildItem` excludes by class name. In whitelist mode, we need to
exclude ALL application beans except the selected ones. This means iterating
`index.getKnownClasses()` which could be large. Performance should be fine at
build time, but we need to be careful not to exclude Quarkus internal beans.
The `isInternalType()` check must include `io.quarkus.*`, `org.jboss.*`, etc.

### FINDING 6 — No DynamicTestBeanContext needed (Info)

The static volatile bridge between JUnit extension and CDI extension is completely
eliminated. Build steps read annotations directly from the Jandex index.

### FINDING 7 — CDI SE replaced by ArC (Info)

No Weld/OWB profiles needed. Quarkus uses ArC exclusively.

### FINDING 8 — Test isolation across test classes (Medium Risk)

In the original addon, each test class boots a fresh CDI SE container. In Quarkus,
`@QuarkusTest` reuses the application across tests in the same profile. Tests with
different `@TestBean` configurations need different Quarkus test profiles or the
application must be restarted. This may require using `@TestProfile` or
`@QuarkusTestResource(restrictToAnnotatedClass = true)`.

**Solution:** If two test classes have different `@TestBean` annotations, they
effectively need different ArC configurations. Quarkus handles this by restarting
the application when the test profile changes. We can make `@EnableTestBeans`
configuration part of the test profile key so Quarkus restarts between incompatible
test classes. This might mean implementing a `QuarkusTestProfile` adapter or using
a custom `@TestProfile` per configuration.

### FINDING 9 — `@Nonbinding` qualifier semantics at build time (Low Risk)

The original addon handles `@Nonbinding` on qualifier members for IP deduplication.
At build time in Quarkus, `InjectionPointInfo.getRequiredQualifiers()` already
handles this correctly — ArC's bean resolver respects `@Nonbinding`. No custom
implementation needed.

---

## 8. Files to Delete (Not Ported)

| File | Reason |
|------|--------|
| `DynamicTestBeanJUnitExtension.java` | Replaced by `@QuarkusTest` |
| `DynamicTestBeanContext.java` | Not needed — no runtime bridge |
| `META-INF/services/jakarta.enterprise.inject.spi.Extension` | Not an SPI extension anymore |
| `ManualContainerTest.java` | Manual container management N/A in Quarkus |
