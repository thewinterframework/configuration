# Winter Configuration

Configuration `3.0.0` integrates Configurate `4.2.0` with Winter `3.0.0` on Java 21. It provides typed YAML containers, serializers, configurable Adventure feedback, environment variable expansion, and Winter reload callbacks.

## Installation

Use the same version for compilation and annotation processing. The plugin also needs the appropriate Winter Paper or Velocity dependency and processor.

```kotlin
dependencies {
    implementation("com.thewinterframework:configuration:3.0.0")
    annotationProcessor("com.thewinterframework:configuration:3.0.0")
    implementation("org.spongepowered:configurate-yaml:4.2.0")
}

tasks.processResources {
    exclude("winter.properties")
}
```

Package or load the runtime dependencies with the plugin. Keep module-specific Winter compiler settings in `src/main/resources/winter.properties`.

## Typed configuration and reload

Annotate a public Configurate-mapped class with `@Configurate("settings")` and inject `Container<Settings>`. The processor generates a singleton provider for `settings.yml`; include its default resource in the plugin JAR. Custom serializers use `@ConfigurateSerializer`, and serializer factory classes use `@ConfigurateSerializerProvider`.

Generated containers and `lang.yml` callbacks register with the injector-local `ConfigurationReloads` component. Its `@OnReload` decorator participates in Winter's `ReloadServiceManager.reload()` and clears callbacks during disable. The configuration module unregisters its feedback provider when stopped. `ConfigurateModule` runs synchronously on the lifecycle thread and depends on Winter's `ComponentModule`.

Use `Feedback.path("feature.message").send(audience, resolvers)` for configured feedback. `Feedback.asComponent` and `asComponents` render it for other text destinations.

String values support `${VARIABLE}`, `${VARIABLE:-default}`, and escaped `$${VARIABLE}` references. Expansion occurs during load and reload without parsing substituted values as YAML. Missing variables without a fallback fail the load. Saving preserves the original references unless the corresponding value is explicitly changed.

## Migration from 2.x

- Upgrade both Winter and its annotation processor to `3.0.0`, and Configuration to `3.0.0`.
- Replace removed `Feedback.sendFeedback(...)` calls with `Feedback.send(...)`.
- Custom processors use `WinterProcessorExtension` with annotation names and `process`/round callbacks. Configuration no longer implements or registers the deprecated `WinterAnnotationProcessor` adapter or subclasses its class-wire processor.
- Continue using `Container<T>`, `@Configurate`, serializer annotations, and `ReloadServiceManager`; generated bindings now use the unified component reload path.

## Build

Run `./gradlew clean build` with JDK 21 (`gradlew.bat` on Windows). Tests cover environment expansion and persistence, generated configuration/module wires with Winter 3, and callbacks through the real component reload decorator.
