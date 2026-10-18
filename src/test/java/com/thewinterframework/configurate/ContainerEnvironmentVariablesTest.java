package com.thewinterframework.configurate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContainerEnvironmentVariablesTest {

	private static final String YAML = """
			label: original
			host: "${WINTER_CONFIG_TEST_VALUE}"
			port: "${WINTER_CONFIG_TEST_PORT}"
			enabled: "${WINTER_CONFIG_TEST_ENABLED}"
			literal: "$${WINTER_CONFIG_TEST_VALUE}"
			servers:
			  - "${WINTER_CONFIG_TEST_VALUE}"
			replicas:
			  - host: "${WINTER_CONFIG_TEST_VALUE}"
			custom: keep-me
			""";

	@TempDir
	Path directory;

	@Test
	void loadsTypedValuesAndKeepsReferencesInTheBackingNode() throws IOException {
		write(YAML);
		final var container = load();

		assertResolved(container.get());
		assertEquals("${WINTER_CONFIG_TEST_VALUE}", container.getNode().node("host").getString());
		assertTrue(container.save());
		assertEquals("${WINTER_CONFIG_TEST_VALUE}", saved().node("host").getString());
		assertFalse(Files.readString(file()).contains("test-secret"));
	}

	@Test
	void resolvesValuesWhenUsingCustomOptions() throws IOException {
		write(YAML);
		final var container = Container.load(NOPLogger.NOP_LOGGER, directory, TestConfig.class, "config",
				options -> options.shouldCopyDefaults(false));

		assertResolved(container.get());
	}

	@Test
	void configurationWithoutReferencesStillCopiesDefaultsWhenRequested() throws IOException {
		write("{}");
		final var container = Container.load(NOPLogger.NOP_LOGGER, directory, DefaultsConfig.class, "config",
				options -> options.shouldCopyDefaults(true));

		assertEquals("localhost", container.get().host);
		assertEquals("localhost", container.getNode().node("host").getString());
	}

	@Test
	void resolvesValuesWhenUsingAnExplicitResourceClass() throws IOException {
		write(YAML);
		final var container = Container.load(NOPLogger.NOP_LOGGER, directory, TestConfig.class, "config",
				ContainerEnvironmentVariablesTest.class);

		assertResolved(container.get());
	}

	@Test
	void resolvesValuesInLoadNode() throws IOException {
		write(YAML);
		final var node = Container.loadNode(TestConfig.class, NOPLogger.NOP_LOGGER, directory, "config",
				options -> options);

		assertEquals("test-secret", node.node("host").getString());
		assertEquals(25570, node.node("port").getInt());
		assertEquals("test-secret", node.node("replicas", 0, "host").getString());
		assertEquals("${WINTER_CONFIG_TEST_VALUE}", node.node("literal").getString());
	}

	@Test
	void preservesReferencesDuringAnInPlaceUpdateOfAnotherField() throws IOException {
		write(YAML);
		final var container = load();

		assertTrue(container.update(config -> {
			config.label = "updated";
			return config;
		}));

		final var saved = saved();
		assertEquals("updated", saved.node("label").getString());
		assertEquals("${WINTER_CONFIG_TEST_VALUE}", saved.node("host").getString());
		assertEquals("${WINTER_CONFIG_TEST_PORT}", saved.node("port").getString());
		assertEquals("${WINTER_CONFIG_TEST_ENABLED}", saved.node("enabled").getString());
		assertEquals("$${WINTER_CONFIG_TEST_VALUE}", saved.node("literal").getString());
		assertEquals("${WINTER_CONFIG_TEST_VALUE}", saved.node("servers", 0).getString());
		assertEquals("${WINTER_CONFIG_TEST_VALUE}", saved.node("replicas", 0, "host").getString());
		assertEquals("keep-me", saved.node("custom").getString());
		assertFalse(Files.readString(file()).contains("test-secret"));
		assertTrue(container.reload());
		assertResolved(container.get());
	}

	@Test
	void explicitlyChangingAReferencedFieldReplacesItsReference() throws IOException {
		write(YAML);
		final var container = load();

		assertTrue(container.update(config -> {
			config.host = "explicit-host";
			return config;
		}));

		assertEquals("explicit-host", saved().node("host").getString());
		assertTrue(container.reload());
		assertEquals("explicit-host", container.get().host);
	}

	@Test
	void resolvesAgainOnReload() throws IOException {
		write(YAML);
		final var container = load();
		final var originalNode = container.getNode();
		write(YAML.replace("${WINTER_CONFIG_TEST_VALUE}", "${WINTER_CONFIG_TEST_PORT}"));

		assertTrue(container.reload());
		assertEquals("25570", container.get().host);
		assertSame(originalNode, container.getNode());
		assertEquals("${WINTER_CONFIG_TEST_PORT}", originalNode.node("host").getString());
	}

	@Test
	void failedReloadKeepsThePreviousConfigurationAndBackingNode() throws IOException {
		write(YAML);
		final var container = load();
		final var previousConfig = container.get();
		final var previousRaw = container.getNode().raw();
		write(YAML.replace("${WINTER_CONFIG_TEST_VALUE}", "${WINTER_CONFIG_TEST_UNSET_7FBEA2}"));

		assertFalse(container.reload());
		assertSame(previousConfig, container.get());
		assertEquals(previousRaw, container.getNode().raw());
	}

	@Test
	void missingRequiredVariableFailsInitialLoad() throws IOException {
		write(YAML.replace("${WINTER_CONFIG_TEST_VALUE}", "${WINTER_CONFIG_TEST_UNSET_7FBEA2}"));

		assertThrows(IOException.class, this::load);
	}

	@Test
	void deserializesAndPreservesATypedFallback() throws IOException {
		write(YAML.replace("${WINTER_CONFIG_TEST_PORT}", "${WINTER_CONFIG_TEST_UNSET_7FBEA2:-25580}"));
		final var container = load();

		assertEquals(25580, container.get().port);
		assertTrue(container.update(config -> {
			config.label = "updated";
			return config;
		}));
		assertEquals("${WINTER_CONFIG_TEST_UNSET_7FBEA2:-25580}", saved().node("port").getString());
	}

	@Test
	void invalidTypedValueKeepsThePreviousConfigurationOnReload() throws IOException {
		write(YAML);
		final var container = load();
		final var previousConfig = container.get();
		final var previousRaw = container.getNode().raw();
		write(YAML.replace("${WINTER_CONFIG_TEST_PORT}", "${WINTER_CONFIG_TEST_VALUE}"));

		assertFalse(container.reload());
		assertSame(previousConfig, container.get());
		assertEquals(previousRaw, container.getNode().raw());
	}

	@Test
	void preservesReferencesAndCommentsWhenUsingAResourceTemplate() throws IOException {
		final var container = Container.load(NOPLogger.NOP_LOGGER, directory, TestConfig.class,
				"environment-template");
		assertTrue(container.save());
		assertTrue(container.update(config -> {
			config.label = "updated";
			return config;
		}));

		final var path = directory.resolve("environment-template.yml");
		final var yaml = Files.readString(path);
		assertTrue(yaml.contains("# Environment configuration"));
		assertTrue(yaml.contains("# Keep this reference"));
		assertTrue(yaml.contains("${WINTER_CONFIG_TEST_VALUE}"));
		assertFalse(yaml.contains("test-secret"));
		assertEquals("updated", YamlConfigurationLoader.builder().path(path).build().load()
				.node("label").getString());
	}

	private void write(final String yaml) throws IOException {
		Files.writeString(file(), yaml);
	}

	private Path file() {
		return directory.resolve("config.yml");
	}

	private org.spongepowered.configurate.ConfigurationNode saved() throws IOException {
		return YamlConfigurationLoader.builder().path(file()).build().load();
	}

	private Container<TestConfig> load() throws IOException {
		return Container.load(NOPLogger.NOP_LOGGER, directory, TestConfig.class, "config");
	}

	private static void assertResolved(final TestConfig config) {
		assertEquals("test-secret", config.host);
		assertEquals(25570, config.port);
		assertTrue(config.enabled);
		assertEquals("${WINTER_CONFIG_TEST_VALUE}", config.literal);
		assertEquals(List.of("test-secret"), config.servers);
		assertEquals("test-secret", config.replicas.getFirst().host);
	}

	@ConfigSerializable
	public static class DefaultsConfig {
		public String host = "localhost";
	}

	@ConfigSerializable
	public static class TestConfig {
		public String label;
		public String host;
		public int port;
		public boolean enabled;
		public String literal;
		public List<String> servers;
		public List<Replica> replicas;
	}

	@ConfigSerializable
	public static class Replica {
		public String host;
	}
}
