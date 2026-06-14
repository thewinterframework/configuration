package com.thewinterframework.configurate.module;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.thewinterframework.configurate.Container;
import com.thewinterframework.configurate.feedback.resource.FeedbackResourceManager;
import com.thewinterframework.configurate.serializer.ConfigurateSerializer;
import com.thewinterframework.configurate.serializer.ConfigurateSerializersRegistry;
import com.thewinterframework.configurate.serializer.provider.ConfigurateSerializerProvider;
import com.thewinterframework.plugin.DataFolder;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.processor.clazz.ClassWireProcessor;
import com.thewinterframework.processor.module.ProcessorModule;
import com.thewinterframework.processor.wire.ClassListWire;
import com.thewinterframework.service.ReloadServiceManager;
import com.thewinterframework.utils.reflect.Reflections;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.TypeSerializer;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A module that provides support for Configurate serializers using the new Wire system.
 */
public class ConfigurateModule implements ProcessorModule {

    private final ConfigurateSerializersRegistry registry = new ConfigurateSerializersRegistry();

    private String langFileName = "lang";

    @Override
    public void configure(final Binder binder) {
        binder.bindScope(ConfigurateSerializer.class, Scopes.SINGLETON);
        binder.bindScope(ConfigurateSerializerProvider.class, Scopes.SINGLETON);
        binder.bind(ConfigurateSerializersRegistry.class).toInstance(registry);
    }

    @Override
    public boolean onLoad(final WinterPlugin plugin) {
        final var start = System.currentTimeMillis();

        try {
            final var serializerWireClass = Class.forName(ClassWireProcessor.canonicalWiredClassName(plugin, ConfigurateSerializer.class));
            final var serializerWire = (ClassListWire) serializerWireClass.getConstructors()[0].newInstance();

            for (final var discoveredSerializer : serializerWire.getWiredClasses()) {
                final var instance = discoveredSerializer.getDeclaredConstructor().newInstance();
                final var type = Reflections.getGenericType(discoveredSerializer, TypeSerializer.class, 0);
                registry.registerSerializer(type, (TypeSerializer<?>) instance);
            }

            final var providerWireClass = Class.forName(ClassWireProcessor.canonicalWiredClassName(plugin, ConfigurateSerializerProvider.class));
            final var providerWire = (ClassListWire) providerWireClass.getConstructors()[0].newInstance();

            for (final var provider : providerWire.getWiredClasses()) {
                final var instance = provider.getDeclaredConstructor().newInstance();
                for (final var method : provider.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(ConfigurateSerializer.class)) {
                        final var returnType = method.getReturnType();
                        if (TypeSerializer.class.isAssignableFrom(returnType)) {
                            method.setAccessible(true);
                            final var serializer = (TypeSerializer<?>) method.invoke(instance);
                            this.registry.registerSerializer(Reflections.getGenericType(returnType, TypeSerializer.class, 0), serializer);
                        } else if (TypeSerializerCollection.class.isAssignableFrom(returnType)) {
                            this.registry.registerCollection((TypeSerializerCollection) method.invoke(instance));
                        }
                    }
                }
            }
        } catch (final Exception e) {
            plugin.getSLF4JLogger().error("Failed to load Configurate components via Wire", e);
            throw new RuntimeException(e);
        }

        plugin.getSLF4JLogger().debug("Loaded Configurate serializers in {}ms", System.currentTimeMillis() - start);
        return true;
    }

    @Override
    public boolean onEnable(final WinterPlugin plugin) {
        final var path = plugin.getInjector().getInstance(Key.get(Path.class, DataFolder.class));
        final var reloadManager = plugin.getInjector().getInstance(ReloadServiceManager.class);

        if (existsInClassLoader(plugin, path.resolve(langFileName + ".yml"), langFileName + ".yml")) {
            final Runnable load = () -> {
                try {
                    final var feedbackNode = Container.loadNode(
                            plugin.getClass(),
                            plugin.getSLF4JLogger(),
                            path,
                            langFileName,
                            options -> options.serializers(builder -> builder.registerAll(registry.getSerializers()))
                    );

                    FeedbackResourceManager.instance()
                            .unregisterProvider(plugin)
                            .registerProvider(plugin, feedbackNode);
                } catch (final IOException e) {
                    plugin.getSLF4JLogger().error("Failed to load feedback resource", e);
                }
            };

            load.run();
            reloadManager.addOnReload(ConfigurateModule.class, load);
        }

        return true;
    }

    public void setLangFileName(final String langFileName) {
        this.langFileName = langFileName;
    }

    private boolean existsInClassLoader(final WinterPlugin plugin, final Path path, final String name) {
        if (Files.exists(path)) {
            return true;
        }

        try (final var rsc = plugin.getClass().getClassLoader().getResourceAsStream(name)) {
            return rsc != null;
        } catch (final IOException e) {
            throw new RuntimeException("Failed to check if resource exists in class loader", e);
        }
    }

    public @NotNull ConfigurateSerializersRegistry registry() {
        return registry;
    }
}