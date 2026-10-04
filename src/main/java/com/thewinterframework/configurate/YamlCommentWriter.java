package com.thewinterframework.configurate;

import org.spongepowered.configurate.ConfigurationNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Utility to write YAML files while preserving the user's custom comments,
 * custom keys, and formatting.
 */
final class YamlCommentWriter {

	private YamlCommentWriter() {}

	static void writeWithComments(
			final Path configPath,
			final ConfigurationNode configNode
	) throws IOException {
		final var userLines = Files.exists(configPath)
				? Files.readAllLines(configPath, StandardCharsets.UTF_8)
				: List.<String>of();
		final var userTree = parse(userLines);

		// Only serialize keys that are already present in the user's file. Missing
		// keys may have been removed intentionally and must not be restored.
		final var outputLines = new ArrayList<String>();
		serialize(userTree, configNode, outputLines);

		Files.write(configPath, outputLines, StandardCharsets.UTF_8);
	}

	private static class YamlNode {
		String key;
		int indent = -1;
		List<String> comments = new ArrayList<>();
		String keyLine;
		List<String> valueLines = new ArrayList<>();
		Map<String, YamlNode> children = new LinkedHashMap<>();
	}

	private static YamlNode parse(final List<String> lines) {
		final var root = new YamlNode();
		var current = root;
		final var stack = new ArrayList<YamlNode>();
		stack.add(root);

		final var pendingComments = new ArrayList<String>();

		var insideOpenQuote = false;  
		var openQuoteChar = ' ';

		for (final var line : lines) {
			final var trimmed = line.trim();

			if (insideOpenQuote) {
				current.valueLines.addAll(pendingComments);
				pendingComments.clear();
				current.valueLines.add(line);
				if (lineClosesQuote(trimmed, openQuoteChar)) {
					insideOpenQuote = false;
				}
				continue;
			}

			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				pendingComments.add(line);
				continue;
			}

			final var indent = countLeadingSpaces(line);
			final var colonIndex = findYamlKeyColon(trimmed);
			final var isKey = colonIndex >= 0
					&& !trimmed.startsWith("-")
					&& !isContinuationLine(indent, current, stack);

			if (isKey) {
				while (stack.size() > 1 && stack.get(stack.size() - 1).indent >= indent) {
					stack.remove(stack.size() - 1);
				}
				final var parent = stack.get(stack.size() - 1);

				final var node = new YamlNode();
				node.key = unquote(trimmed.substring(0, colonIndex).trim());
				node.indent = indent;
				node.comments.addAll(pendingComments);
				pendingComments.clear();
				node.keyLine = line;

				parent.children.put(node.key, node);
				stack.add(node);
				current = node;

				final var afterColon = trimmed.substring(colonIndex + 1).trim();
				if (!afterColon.isEmpty()) {
					final var firstChar = afterColon.charAt(0);
					if (firstChar == '"' || firstChar == '\'') {
						if (!isQuoteClosed(afterColon, firstChar)) {
							insideOpenQuote = true;
							openQuoteChar = firstChar;
						}
					}
				}
			} else {
				current.valueLines.addAll(pendingComments);
				pendingComments.clear();
				current.valueLines.add(line);
			}
		}
		root.valueLines.addAll(pendingComments);
		return root;
	}

	private static void serialize(final YamlNode node, final ConfigurationNode configNode, final List<String> out) {
		out.addAll(node.comments);

		if (node.keyLine != null) {
			var line = node.keyLine;

			final var hasMultiLineValue = !node.valueLines.isEmpty();
			if (!hasMultiLineValue && configNode != null && !configNode.virtual()
					&& configNode.raw() != null && !configNode.isMap() && !configNode.isList()) {
				final var colonIndex = findYamlKeyColon(line.trim());
				if (colonIndex >= 0) {
					final var trimmed = line.trim();
					final var indentStr = line.substring(0, line.length() - line.stripLeading().length());
					final var keyPart = trimmed.substring(0, colonIndex);
					final var afterColon = trimmed.substring(colonIndex + 1);
					final var inlineComment = extractInlineComment(afterColon);
					final var newValue = formatScalar(configNode.raw());

					if (inlineComment != null) {
						line = indentStr + keyPart + ": " + newValue + " " + inlineComment;
					} else {
						line = indentStr + keyPart + ": " + newValue;
					}
				}
			}
			out.add(line);
		}

		out.addAll(node.valueLines);

		for (final var entry : node.children.entrySet()) {
			final var childConfig = configNode != null ? configNode.node(entry.getKey()) : null;
			serialize(entry.getValue(), childConfig, out);
		}
	}

	private static int countLeadingSpaces(final String line) {
		int count = 0;
		for (final char c : line.toCharArray()) {
			if (c == ' ') {
				count++;
			} else {
				break;
			}
		}
		return count;
	}

	/**
	 * Finds a colon that is a valid YAML key separator: not inside quotes,
	 * and followed by a space or at the end of the string.
	 * This prevents matching colons inside values like MiniMessage tags or URLs.
	 */
	private static int findYamlKeyColon(final String str) {
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		for (int i = 0; i < str.length(); i++) {
			final char c = str.charAt(i);
			if (c == '\'' && !inDoubleQuote) {
				inSingleQuote = !inSingleQuote;
			} else if (c == '"' && !inSingleQuote) {
				inDoubleQuote = !inDoubleQuote;
			} else if (c == ':' && !inSingleQuote && !inDoubleQuote) {
				if (i + 1 == str.length() || str.charAt(i + 1) == ' ') {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Returns true if the given line is a continuation line of a multi-line scalar.
	 * A continuation line has greater indentation than the current node AND
	 * the current node already has value lines (meaning its scalar started on a
	 * previous line) OR the current node is a list-item context.
	 */
	private static boolean isContinuationLine(final int indent, final YamlNode current, final java.util.List<YamlNode> stack) {
		if (stack.size() <= 1) {
			return false;
		}
		if (!current.valueLines.isEmpty() && indent > current.indent) {
			return true;
		}
		final var parent = stack.get(stack.size() - 1);
        return !parent.children.isEmpty() && indent > current.indent + 1;
    }

	/**
	 * Returns true if the given string value has a closed quote of the given type.
	 */
	private static boolean isQuoteClosed(final String value, final char quoteChar) {
		boolean open = false;
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (c == quoteChar) {
				if (quoteChar == '\'' && i + 1 < value.length() && value.charAt(i + 1) == '\'') {
					i++;
				} else {
					open = !open;
				}
			} else if (c == '\\' && quoteChar == '"' && i + 1 < value.length()) {
				i++; 
			}
		}
		return !open;
	}

	/**
	 * Returns true if this continuation line closes an open quote.
	 */
	private static boolean lineClosesQuote(final String trimmed, final char quoteChar) {
		boolean inEscape = false;
		int lastQuoteIndex = -1;
		for (int i = 0; i < trimmed.length(); i++) {
			final char c = trimmed.charAt(i);
			if (inEscape) {
				inEscape = false;
			} else if (c == '\\' && quoteChar == '"') {
				inEscape = true;
			} else if (c == quoteChar) {
				if (quoteChar == '\'' && i + 1 < trimmed.length() && trimmed.charAt(i + 1) == '\'') {
					i++; 
				} else {
					lastQuoteIndex = i;
				}
			}
		}
		return lastQuoteIndex >= 0;
	}

	private static String unquote(final String str) {
		if (str.length() >= 2) {
			if ((str.startsWith("'") && str.endsWith("'"))
					|| (str.startsWith("\"") && str.endsWith("\""))) {
				return str.substring(1, str.length() - 1);
			}
		}
		return str;
	}

	private static String extractInlineComment(final String value) {
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (c == '\'' && !inDoubleQuote) {
				inSingleQuote = !inSingleQuote;
			} else if (c == '"' && !inSingleQuote) {
				inDoubleQuote = !inDoubleQuote;
			} else if (c == '#' && !inSingleQuote && !inDoubleQuote && i > 0 && value.charAt(i - 1) == ' ') {
				return value.substring(i);
			}
		}
		return null;
	}

	private static String formatScalar(final Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof Boolean || value instanceof Number) {
			return value.toString();
		}
		final var str = value.toString();
		if (needsQuoting(str)) {
			return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		}
		return str;
	}

	private static boolean needsQuoting(final String value) {
		if (value.isEmpty()) {
			return true;
		}
		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
				|| "null".equalsIgnoreCase(value) || "~".equals(value)) {
			return true;
		}
		for (final char c : value.toCharArray()) {
			if (c == ':' || c == '#' || c == '[' || c == ']' || c == '{' || c == '}'
					|| c == ',' || c == '&' || c == '*' || c == '!' || c == '|'
					|| c == '>' || c == '\'' || c == '"' || c == '%' || c == '@'
					|| c == '`') {
				return true;
			}
		}
		final char first = value.charAt(0);
        return first == '-' || first == '?' || first == ' ';
    }
}
