package com.thewinterframework.configurate;

import com.thewinterframework.configurate.config.Configurate;
import com.thewinterframework.configurate.config.FillMissingKeys;
import com.thewinterframework.configurate.config.OptionalNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MissingDefaultsTest {
	@TempDir Path directory;

	@Test
	void addsMappedDefaultsWithoutWritingOptionalOrExistingValues() throws IOException {
		var file = directory.resolve("fill-missing.yml");
		Files.writeString(file, """
				# keep this
				workers: 0 # operator's value
				feature:
				  enabled: false
				values: {}
				messages: {custom: edited}
				aliases: []
				custom: null
				""");
		var container = Container.load(NOPLogger.NOP_LOGGER, directory, Config.class, "fill-missing");
		var result = Files.readString(file);
		assertTrue(result.contains("# keep this\nworkers: 0 # operator's value"));
		assertTrue(result.contains("enabled: false"));
		assertTrue(result.contains("limit: 12"));
		assertTrue(result.contains("values: {}"));
		assertTrue(result.contains("aliases: []\ncustom: null"));
		assertEquals(Map.of("custom", "edited", "missing", "new"), container.get().messages());
		assertTrue(result.contains("${WINTER_CONFIG_TEST_VALUE}"));
		assertFalse(result.contains("optional:"));
		assertFalse(result.contains("token:"));
		assertFalse(result.contains("unknown-template-key:"));
		assertEquals("test-secret", container.get().label());
		assertEquals(0, container.get().workers());
		assertTrue(container.reload());
		assertEquals(result, Files.readString(file));

		Files.writeString(file, "workers: 4\nfeature: {enabled: false}\noptional: null\n");
		assertTrue(container.reload());
		assertEquals(12, container.get().feature().limit());
		assertTrue(Files.readString(file).contains("optional: null"));
		assertNull(container.get().feature().token());
	}

	@Test
	void completesAnEmptyDocumentAndAnOptedInEmptyMap() throws IOException {
		var file = directory.resolve("fill-missing.yml");
		Files.writeString(file, "{} # operator comment\n");
		var container = Container.load(NOPLogger.NOP_LOGGER, directory, Config.class, "fill-missing");
		assertEquals(2, container.get().workers());
		assertTrue(Files.readString(file).endsWith("# operator comment\n"));

		Files.writeString(file, "values: {}\nmessages: {}\n");
		assertTrue(container.reload());
		assertTrue(container.get().values().isEmpty());
		assertEquals(Map.of("custom", "default", "missing", "new"), container.get().messages());
	}

	@Test
	void leavesTheOriginalFileAndLastValidObjectOnFailure() throws IOException {
		var file = directory.resolve("fill-missing.yml");
		Files.writeString(file, "workers: 4\n");
		var container = Container.load(NOPLogger.NOP_LOGGER, directory, Config.class, "fill-missing");
		assertEquals(12, container.get().feature().limit());
		assertNull(container.get().feature().token());
		var previous = container.get();
		var invalid = "workers: invalid\n";
		Files.writeString(file, invalid);
		assertFalse(container.reload());
		assertSame(previous, container.get());
		assertEquals(invalid, Files.readString(file));
	}

	@ConfigSerializable
	@Configurate(value = "fill-missing", fillMissing = true)
	record Config(Integer workers, Feature feature, @OptionalNode Feature optional, Map<String, Boolean> values,
			@FillMissingKeys Map<String, String> messages, List<String> aliases, String label) {}

	@ConfigSerializable
	record Feature(Boolean enabled, Integer limit, @OptionalNode String token) {}
}
