package com.thewinterframework.configurate;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentVariablesTest {

	@Test
	void resolvesMultipleReferencesWithoutChangingTheOriginal() throws IOException {
		final var node = BasicConfigurationNode.root();
		node.node("uri").raw("postgres://${USER}:${PASSWORD}@${HOST}/database");
		final var environment = Map.of("USER", "admin", "PASSWORD", "a$1\\secret", "HOST", "localhost");

		final var resolved = EnvironmentVariables.resolve(node, environment::get);

		assertEquals("postgres://admin:a$1\\secret@localhost/database", resolved.node("uri").getString());
		assertEquals("postgres://${USER}:${PASSWORD}@${HOST}/database", node.node("uri").getString());
	}

	@Test
	void usesFallbackForMissingOrEmptyVariables() throws IOException {
		final var node = BasicConfigurationNode.root();
		node.raw(List.of("${MISSING:-localhost}", "${EMPTY:-25565}", "${HOST:-fallback}", "${MISSING:-}"));
		final var environment = Map.of("EMPTY", "", "HOST", "example.com");

		assertEquals(List.of("localhost", "25565", "example.com", ""),
				EnvironmentVariables.resolve(node, environment::get).raw());
	}

	@Test
	void acceptsAnEmptyVariableWithoutAFallback() throws IOException {
		final var node = BasicConfigurationNode.root().raw("prefix-${EMPTY}-suffix");

		assertEquals("prefix--suffix", EnvironmentVariables.resolve(node, name -> "").getString());
	}

	@Test
	void resolvesNestedMapsAndListsWithoutChangingKeysOrScalarTypes() throws IOException {
		final var node = BasicConfigurationNode.root();
		node.node("servers").raw(List.of(Map.of("host", "${HOST}"), "${PORT}", 10, true));
		node.node("${HOST}").raw("unchanged-key");
		final var environment = Map.of("HOST", "localhost", "PORT", "25565");

		final var resolved = EnvironmentVariables.resolve(node, environment::get);

		assertEquals("localhost", resolved.node("servers", 0, "host").getString());
		assertEquals(25565, resolved.node("servers", 1).getInt());
		assertEquals(10, resolved.node("servers", 2).raw());
		assertEquals(true, resolved.node("servers", 3).raw());
		assertEquals("unchanged-key", resolved.node("${HOST}").getString());
	}

	@Test
	void escapesReferencesWithoutLookingThemUp() throws IOException {
		final var node = BasicConfigurationNode.root().raw("$${MISSING} $${MISSING:-default}");

		final var resolved = EnvironmentVariables.resolve(node, name -> {
			fail("Escaped references must not access the environment");
			return null;
		});

		assertEquals("${MISSING} ${MISSING:-default}", resolved.getString());
	}

	@Test
	void doesNotExpandVariableContentsRecursivelyOrParseThemAsYaml() throws IOException {
		final var node = BasicConfigurationNode.root().raw("${VALUE}");
		final var value = "${OTHER}: [true, 123] # literal";

		final var resolved = EnvironmentVariables.resolve(node, name -> value);

		assertEquals(value, resolved.raw());
		assertFalse(resolved.isMap());
		assertFalse(resolved.isList());
	}

	@Test
	void leavesOtherPlaceholderSyntaxUntouched() throws IOException {
		final var node = BasicConfigurationNode.root().raw("${player.name} ${} ${UNCLOSED");

		assertEquals(node.raw(), EnvironmentVariables.resolve(node, name -> null).raw());
	}

	@Test
	void reportsMissingVariablesWithoutIncludingResolvedSecrets() {
		final var node = BasicConfigurationNode.root();
		node.node("database", "uri").raw("${PASSWORD}@${MISSING}");

		final var error = assertThrows(IOException.class, () -> EnvironmentVariables.resolve(node,
				name -> name.equals("PASSWORD") ? "private-value" : null));

		assertTrue(error.getMessage().contains("MISSING"));
		assertTrue(error.getMessage().contains("database"));
		assertTrue(error.getMessage().contains("uri"));
		assertFalse(error.getMessage().contains("private-value"));
		assertEquals("${PASSWORD}@${MISSING}", node.node("database", "uri").getString());
	}
}
