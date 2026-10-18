package com.thewinterframework.configurate;

import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves environment references without modifying the node used for saving. */
final class EnvironmentVariables {

	private static final Pattern EXPRESSION = Pattern.compile(
			"\\$\\$?\\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^{}]*))?}"
	);

	private EnvironmentVariables() {}

	static ConfigurationNode resolve(final ConfigurationNode node) throws IOException {
		return resolve(node, System::getenv);
	}

	static ConfigurationNode resolve(
			final ConfigurationNode node,
			final Function<String, String> environment
	) throws IOException {
		if (!containsExpressions(node)) {
			return node;
		}
		final var resolved = node.copy();
		resolveInPlace(resolved, environment);
		return resolved;
	}

	private static boolean containsExpressions(final ConfigurationNode node) {
		if (node.isMap()) {
			return node.childrenMap().values().stream().anyMatch(EnvironmentVariables::containsExpressions);
		}
		if (node.isList()) {
			return node.childrenList().stream().anyMatch(EnvironmentVariables::containsExpressions);
		}
		return node.rawScalar() instanceof String value && EXPRESSION.matcher(value).find();
	}

	private static void resolveInPlace(
			final ConfigurationNode node,
			final Function<String, String> environment
	) throws IOException {
		if (node.isMap()) {
			for (final var child : node.childrenMap().values()) {
				resolveInPlace(child, environment);
			}
		} else if (node.isList()) {
			for (final var child : node.childrenList()) {
				resolveInPlace(child, environment);
			}
		} else if (node.rawScalar() instanceof String value) {
			final var matcher = EXPRESSION.matcher(value);
			final var resolved = new StringBuilder();
			while (matcher.find()) {
				final String replacement;
				if (matcher.group().startsWith("$$")) {
					replacement = matcher.group().substring(1);
				} else {
					final var variable = environment.apply(matcher.group(1));
					final var fallback = matcher.group(2);
					if (fallback != null && (variable == null || variable.isEmpty())) {
						replacement = fallback;
					} else if (variable != null) {
						replacement = variable;
					} else {
						throw new IOException("Missing environment variable " + matcher.group(1)
								+ " at configuration path " + node.path());
					}
				}
				matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
			}
			matcher.appendTail(resolved);
			node.raw(resolved.toString());
		}
	}

	/** Keep references for fields whose serialized value did not change during an update. */
	static void preserveExpressions(
			final ConfigurationNode original,
			final ConfigurationNode previous,
			final ConfigurationNode updated
	) {
		if (original.isMap() && previous.isMap() && updated.isMap()) {
			for (final var entry : original.childrenMap().entrySet()) {
				preserveExpressions(entry.getValue(), previous.node(entry.getKey()), updated.node(entry.getKey()));
			}
		} else if (original.isList() && previous.isList() && updated.isList()) {
			final var originals = original.childrenList();
			final var previousValues = previous.childrenList();
			final var updatedValues = updated.childrenList();
			final var size = Math.min(originals.size(), Math.min(previousValues.size(), updatedValues.size()));
			for (int i = 0; i < size; i++) {
				preserveExpressions(originals.get(i), previousValues.get(i), updatedValues.get(i));
			}
		} else if (original.rawScalar() instanceof String value
				&& EXPRESSION.matcher(value).find()
				&& Objects.equals(previous.raw(), updated.raw())) {
			updated.raw(value);
		}
	}
}
