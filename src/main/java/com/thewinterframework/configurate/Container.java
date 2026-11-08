package com.thewinterframework.configurate;

import com.thewinterframework.configurate.config.Configurate;
import io.leangen.geantyref.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * A container for a configuration file.
 *
 * <p>String values support {@code ${VARIABLE}} environment references and
 * {@code ${VARIABLE:-default}} fallbacks for missing or empty variables.
 * Use {@code $${VARIABLE}} for a literal reference. Resolution happens before
 * deserialization on load and reload, including nested maps and lists.
 * Missing variables without a fallback fail the load. Values are resolved
 * once and are never parsed as YAML.</p>
 *
 * <p>The backing node retains the original references. Saving or updating
 * unrelated fields preserves them; explicitly changing a referenced field
 * through {@link #update(UnaryOperator)} replaces its reference.</p>
 *
 * @param <C> the type of the configuration object
 */
public final class Container<C> {
	private final AtomicReference<C> config;
	private final YamlConfigurationLoader loader;
	private final ConfigurationNode node;
	private final Class<C> clazz;
	private final TypeToken<C> typeToken;
	private final ObjectMapper<C> mapper;
	private final Logger logger;
	private final URL defaultUrl;
	private final Path configPath;

	private Container(
			final C config,
			final Class<C> clazz,
			final TypeToken<C> typeToken,
			final YamlConfigurationLoader loader,
			final ConfigurationNode node,
			final ObjectMapper<C> mapper,
			final Logger logger,
			final URL defaultUrl,
			final Path configPath
	) {
		this.config = new AtomicReference<>(config);
		this.loader = loader;
		this.clazz = clazz;
		this.typeToken = typeToken;
		this.node = node;
		this.mapper = mapper;
		this.logger = logger;
		this.defaultUrl = defaultUrl;
		this.configPath = configPath;
	}

	/**
	 * Reloads the configuration file.
	 * @return {@code true} if the reload was successful, {@code false} otherwise
	 */
	public synchronized boolean reload() {
		try {
			if (completesDefaults(clazz, defaultUrl)) {
				final var loaded = loadCompleted(loader, mapper, typeToken, defaultUrl, configPath);
				this.node.from(loaded.node());
				config.set(loaded.value());
				return true;
			}
			final ConfigurationNode reloadedNode = loader.load();
			final C newConfig = mapper.load(EnvironmentVariables.resolve(reloadedNode));
			this.node.from(reloadedNode);
			config.set(newConfig);
			return true;
		} catch (final Exception exception) {
			logger.error("Could not reload {} configuration file", clazz.getSimpleName(), exception);
			return false;
		}
	}

	/** Gets the configuration object. */
	public @NotNull C get() {
		return this.config.get();
	}

	/**
	 * Gets the configuration node, retaining environment references for saving.
	 * @return the configuration node
	 */
	public ConfigurationNode getNode() {
		return this.node;
	}

	/**
	 * Updates the configuration object using the provided updater function.
	 * @param updater the function to apply to the current config
	 * @return {@code true} if the update and save were successful, {@code false} otherwise
	 */
	public synchronized boolean update(final UnaryOperator<C> updater) {
		try {
			final var previousNode = node.copy();
			previousNode.set(typeToken, config.get());
			final C newConfig = updater.apply(config.get());
			final var updatedNode = node.copy();
			updatedNode.set(typeToken, newConfig);
			EnvironmentVariables.preserveExpressions(node, previousNode, updatedNode);
			node.from(updatedNode);
			config.set(newConfig);
			return saveWithComments();
		} catch (final Exception exception) {
			logger.error("Could not update {} configuration", clazz.getSimpleName(), exception);
			return false;
		}
	}

	/**
	 * Saves the current configuration to the file.
	 * @return {@code true} if the save was successful, {@code false} otherwise
	 */
	public synchronized boolean save() {
		return saveWithComments();
	}

	/**
	 * Saves the current configuration to the file while preserving its existing
	 * comments, custom keys, and formatting when a resource template is available.
	 */
	private boolean saveWithComments() {
		try {
			if (defaultUrl != null && configPath != null) {
				YamlCommentWriter.writeWithComments(configPath, node);
			} else {
				loader.save(node);
			}
			return true;
		} catch (final IOException exception) {
			logger.error("Could not save {} configuration file", clazz.getSimpleName(), exception);
			return false;
		}
	}

	/**
	 * Loads a configuration file.
	 * @param logger the logger
	 * @param path the path to the configuration file
	 * @param clazz the class of the configuration object
	 * @param file the name of the configuration file
	 * @param options the configuration options
	 * @return the container
	 * @param <C> the type of the configuration object
	 * @throws IOException if an I/O error occurs
	 */
	public static <C> Container<C> load(
			final Logger logger,
			final Path path,
			final Class<C> clazz,
			final String file,
			final UnaryOperator<ConfigurationOptions> options
	) throws IOException {
		return load(logger, path, clazz, file, clazz, false, options);
	}

	private static <C> Container<C> load(
			final Logger logger,
			final Path path,
			final Class<C> clazz,
			final String file,
			final Class<?> resourceClass,
			final boolean explicitResourceClass,
			final UnaryOperator<ConfigurationOptions> options
	) throws IOException {
		try {
			final var mapper = ObjectMapper.factory().get(clazz);
			final var typeToken = TypeToken.get(clazz);
			final var fileName = file.endsWith(".yml") ? file : file + ".yml";
			if (!Files.exists(path)) {
				Files.createDirectories(path);
			}

			final var configPath = generateFile(resourceClass, path.resolve(fileName), fileName);
			final var loader = YamlConfigurationLoader.builder()
					.defaultOptions(options)
					.nodeStyle(NodeStyle.BLOCK)
					.path(configPath)
					.build();

			final URL defaultUrl = resourceClass.getClassLoader().getResource(fileName);
			if (completesDefaults(clazz, defaultUrl)) {
				final var loaded = loadCompleted(loader, mapper, typeToken, defaultUrl, configPath);
				return new Container<>(loaded.value(), clazz, typeToken, loader, loaded.node(), mapper, logger, defaultUrl, configPath);
			}
			final var node = loader.load();
			final var saveTemplate = explicitResourceClass ? resourceClass.getResource(fileName) : defaultUrl;
			return new Container<>(EnvironmentVariables.resolve(node).get(typeToken), clazz, typeToken,
					loader, node, mapper, logger, saveTemplate, configPath);
		} catch (final IOException exception) {
			logger.error("Could not load {} configuration file", clazz.getSimpleName(), exception);
			throw exception;
		}
	}

	/**
	 * Loads a configuration node.
	 * Environment references in string values are resolved in the returned node.
	 * @param logger the logger
	 * @param path the path to the configuration file
	 * @param file the name of the configuration file
	 * @param options the configuration options
	 * @return the configuration node
	 * @throws IOException if an I/O error occurs
	 */
	public static ConfigurationNode loadNode(
			final Class<?> clazz,
			final Logger logger,
			final Path path,
			final String file,
			final UnaryOperator<ConfigurationOptions> options
	) throws IOException {
		try {
			final var fileName = file.endsWith(".yml") ? file : file + ".yml";
			if (!Files.exists(path)) {
				Files.createDirectories(path);
			}

			final var configPath = generateFile(clazz, path.resolve(fileName), fileName);
			final YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
					.defaultOptions(options)
					.nodeStyle(NodeStyle.BLOCK)
					.path(configPath)
					.build();

			return EnvironmentVariables.resolve(loader.load());
		} catch (final IOException exception) {
			logger.error("Could not load configuration file", exception);
			throw exception;
		}
	}

	/**
	 * Loads a configuration file.
	 * @param logger the logger
	 * @param path the path to the configuration file
	 * @param clazz the class of the configuration object
	 * @param file the name of the configuration file
	 * @return the container
	 * @param <C> the type of the configuration object
	 * @throws IOException if an I/O error occurs
	 */
	public static <C> Container<C> load(
			final Logger logger,
			final Path path,
			final Class<C> clazz,
			final String file
	) throws IOException {
		return load(logger, path, clazz, file, opts -> opts.shouldCopyDefaults(false));
	}

	/**
	 * Loads a configuration file.
	 * @param logger the logger
	 * @param path the path to the configuration file
	 * @param clazz the class of the configuration object
	 * @param file the name of the configuration file
	 * @param clazzLoader the class loader
	 * @return the container
	 * @param <C> the type of the configuration object
     */
	public static <C> Container<C> load(
			final Logger logger,
			final Path path,
			final Class<C> clazz,
			final String file,
			final Class<?> clazzLoader
	) throws IOException {
		return load(logger, path, clazz, file, clazzLoader, true, UnaryOperator.identity());
	}

	private static boolean completesDefaults(Class<?> clazz, URL template) {
		final var annotation = clazz.getAnnotation(Configurate.class);
		return template != null && annotation != null && annotation.fillMissing();
	}

	private static <C> Loaded<C> loadCompleted(YamlConfigurationLoader loader, ObjectMapper<C> mapper,
			TypeToken<C> type, URL template, Path path) throws IOException {
		final String original = Files.readString(path);
		final String defaults;
		try (var input = template.openStream()) {
			defaults = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		final var options = loader.defaultOptions().shouldCopyDefaults(false);
		final String completed;
		try {
			completed = MissingDefaults.complete(original, defaults, mapper, options);
		} catch (org.yaml.snakeyaml.error.YAMLException error) {
			throw new IOException("Could not read YAML for default completion: " + path, error);
		}
		final var node = YamlConfigurationLoader.builder()
				.defaultOptions(options)
				.source(() -> new BufferedReader(new StringReader(completed)))
				.build().load();
		final C value = EnvironmentVariables.resolve(node.copy()).get(type);
		if (value == null) {
			throw new IOException("Could not deserialize configuration " + path);
		}
		if (!original.equals(completed)) {
			writeCompleted(path, original, completed);
		}
		return new Loaded<>(node, value);
	}

	private static void writeCompleted(Path path, String original, String completed) throws IOException {
		final var target = path.toRealPath();
		final var temporary = Files.createTempFile(target.getParent(), ".winter-config-", ".yml");
		try {
			Files.copy(target, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			Files.writeString(temporary, completed);
			if (!Files.readString(target).equals(original)) {
				throw new IOException("Configuration changed during default completion: " + path);
			}
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private record Loaded<C>(ConfigurationNode node, C value) {}

	private static Path generateFile(final Class<?> clazz, final Path path, final String name) throws IOException {
		if (Files.exists(path)) {
			return path;
		}

		try (final var rsc = clazz.getClassLoader().getResourceAsStream(name)) {
			if (rsc == null) {
				if (!Files.exists(path)) {
					throw new IOException("Could not find resource " + path.getFileName());
				}
				return path;
			}

			Files.copy(rsc, path);
			return path;
		}
	}
}
