package com.thewinterframework.configurate.module;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.thewinterframework.configurate.Container;
import com.thewinterframework.configurate.feedback.resource.FeedbackResourceManager;
import com.thewinterframework.configurate.serializer.ConfigurateSerializer;
import com.thewinterframework.configurate.serializer.ConfigurateSerializersRegistry;
import com.thewinterframework.configurate.serializer.processor.ConfigurateSerializerAnnotationProcessor;
import com.thewinterframework.plugin.DataFolder;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.plugin.module.PluginModule;
import com.thewinterframework.service.ReloadServiceManager;
import com.thewinterframework.utils.Reflections;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A module that provides support for Configurate serializers.
 */
public class ConfigurateModule implements PluginModule {

	private final ConfigurateSerializersRegistry registry = new ConfigurateSerializersRegistry();

	@Override
	public void configure(Binder binder) {
		binder.bindScope(ConfigurateSerializer.class, Scopes.SINGLETON);
		binder.bind(ConfigurateSerializersRegistry.class).toInstance(registry);
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean onLoad(WinterPlugin plugin) {
		try {
			final var serializers = ConfigurateSerializerAnnotationProcessor.scan(plugin.getClass(), ConfigurateSerializer.class).getClassList();

			for (final var discoveredSerializer : serializers) {
				final var instance = discoveredSerializer.newInstance();
				final var type = Reflections.getGenericType(discoveredSerializer, TypeSerializer.class, 0);
				registry.registerSerializer(type, (TypeSerializer<?>) instance);
			}
		} catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
			plugin.getSLF4JLogger().error("Failed to scan for module components", e);
			throw new RuntimeException(e);
		}

		return true;
	}

	@Override
	public boolean onEnable(WinterPlugin plugin) {
		final var path = plugin.getInjector().getInstance(Key.get(Path.class, DataFolder.class));
		final var reloadManager = plugin.getInjector().getInstance(ReloadServiceManager.class);

		//TODO: custom lang file name!
		if (existsInClassLoader(plugin, path.resolve("lang.yml"), "lang.yml")) {
			final Runnable load = () -> {
				try {
					final var feedbackNode = Container.loadNode(
							plugin.getSLF4JLogger(),
							path,
							"lang",
							options -> options.serializers(builder -> builder.registerAll(registry.getSerializers()))
					);

					FeedbackResourceManager.instance()
							.unregisterProvider(plugin)
							.registerProvider(plugin, feedbackNode);
				} catch (IOException e) {
					plugin.getSLF4JLogger().error("Failed to load feedback resource", e);
				}
			};

			load.run();
			reloadManager.addOnReload(ConfigurateModule.class, load);
		}

		return true;
	}

	private boolean existsInClassLoader(WinterPlugin plugin, Path path, String name) {
		if (Files.exists(path)) {
			return true;
		}

		try (final var rsc = plugin.getClass().getClassLoader().getResourceAsStream(name)) {
			return rsc != null;
		} catch (IOException e) {
			throw new RuntimeException("Failed to check if resource exists in class loader", e);
		}
	}

	public @NotNull ConfigurateSerializersRegistry registry() {
		return registry;
	}
}
