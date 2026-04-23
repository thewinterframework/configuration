package com.thewinterframework.configurate;

import io.leangen.geantyref.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * A container for a configuration file.
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
	 * @return {@code true} if the save was successful, {@code false} otherwise
	 */
	public boolean reload() {
		try {
			final ConfigurationNode reloadedNode = loader.load();
			if (defaultUrl != null) {
				try {
					final var defaultLoader = YamlConfigurationLoader.builder()
							.nodeStyle(NodeStyle.BLOCK)
							.url(defaultUrl)
							.build();
					final var defaultNode = defaultLoader.load();
					reloadedNode.mergeFrom(defaultNode);
				} catch (final Exception exception) {
					logger.error("Could not merge default values into {} configuration", clazz.getSimpleName(), exception);
				}
			}
			final C newConfig = mapper.load(reloadedNode);
			this.node.from(reloadedNode);
			config.set(newConfig);
			return saveWithComments();
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
	 * Gets the configuration node.
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
	public boolean update(UnaryOperator<C> updater) {
		try {
			final C newConfig = updater.apply(config.get());
			config.set(newConfig);
			node.set(typeToken, newConfig);
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
	public boolean save() {
		return saveWithComments();
	}

	/**
	 * Saves the current configuration to the file, preserving comments
	 * from the resource template when available.
	 */
	private boolean saveWithComments() {
		try {
			if (defaultUrl != null && configPath != null) {
				YamlCommentWriter.writeWithComments(configPath, defaultUrl, node);
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
		try {
			final var mapper = ObjectMapper.factory().get(clazz);
			final var typeToken = TypeToken.get(clazz);
			final var fileName = file.endsWith(".yml") ? file : file + ".yml";
			if (!Files.exists(path)) {
				Files.createDirectories(path);
			}

			final var configPath = generateFile(clazz, path.resolve(fileName), fileName);
			final var loader = YamlConfigurationLoader.builder()
					.defaultOptions(options)
					.nodeStyle(NodeStyle.BLOCK)
					.path(configPath)
					.build();

			final var node = loader.load();

			URL defaultUrl = clazz.getClassLoader().getResource(fileName);
			if (defaultUrl != null) {
				try {
					final var defaultLoader = YamlConfigurationLoader.builder()
							.nodeStyle(NodeStyle.BLOCK)
							.url(defaultUrl)
							.build();
					final var defaultNode = defaultLoader.load();
					node.mergeFrom(defaultNode);
				} catch (final Exception exception) {
					logger.error("Could not merge default values into {} configuration", clazz.getSimpleName(), exception);
				}
			}

			final C newConfig = node.get(typeToken);

			Container<C> container = new Container<>(newConfig, clazz, typeToken, loader, node, mapper, logger, defaultUrl, configPath);
			container.saveWithComments();
			return container;
		} catch (final IOException exception) {
			logger.error("Could not load {} configuration file", clazz.getSimpleName(), exception);
			throw exception;
		}
	}

	/**
	 * Loads a configuration node.
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

			return loader.load();
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
		return load(logger, path, clazz, file, opts -> opts.shouldCopyDefaults(true));
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
		try {
			final var mapper = ObjectMapper.factory().get(clazz);
			final var typeToken = TypeToken.get(clazz);
			final var fileName = file.endsWith(".yml") ? file : file + ".yml";
			if (!Files.exists(path)) {
				Files.createDirectories(path);
			}

			final var configPath = generateFile(clazzLoader, path.resolve(fileName), fileName);
			final var loader = YamlConfigurationLoader.builder()
					.nodeStyle(NodeStyle.BLOCK)
					.path(configPath)
					.build();

			final var node = loader.load();

			final URL defaultUrl = clazzLoader.getResource(fileName);
			if (defaultUrl != null) {
				try {
					final var defaultLoader = YamlConfigurationLoader.builder()
							.nodeStyle(NodeStyle.BLOCK)
							.url(defaultUrl)
							.build();
					final var defaultNode = defaultLoader.load();
					node.mergeFrom(defaultNode);
				} catch (final Exception exception) {
					logger.error("Could not merge default values into {} configuration", clazz.getSimpleName(), exception);
				}
			}

			final C newConfig = node.get(typeToken);

			Container<C> container = new Container<>(newConfig, clazz, typeToken, loader, node, mapper, logger, defaultUrl, configPath);
			container.saveWithComments();
			return container;
		} catch (final IOException exception) {
			logger.error("Could not load {} configuration file", clazz.getSimpleName(), exception);
			throw exception;
		}
	}

	private static Path generateFile(final Class<?> clazz, final Path path, final String name) throws IOException {
		if (Files.exists(path) && Files.size(path) > 0) {
			return path;
		}

		try (final var rsc = clazz.getClassLoader().getResourceAsStream(name)) {
			if (rsc == null) {
				if (!Files.exists(path)) {
					throw new IOException("Could not find resource " + path.getFileName());
				}
				return path;
			}

			if (Files.exists(path)) {
				Files.copy(rsc, path, StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.copy(rsc, path);
			}
			return path;
		}
	}
}
