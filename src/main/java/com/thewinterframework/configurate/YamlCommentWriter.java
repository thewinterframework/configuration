package com.thewinterframework.configurate;

import org.spongepowered.configurate.ConfigurationNode;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Utility to write YAML files while preserving the user's custom comments,
 * custom keys, and formatting.
 */
final class YamlCommentWriter {

	private YamlCommentWriter() {
	}

	static void writeWithComments(
			final Path configPath,
			final URL resourceUrl,
			final ConfigurationNode mergedNode
	) throws IOException {
		
		// 1. Read default lines
		List<String> defaultLines = new ArrayList<>();
		try (final var reader = new BufferedReader(new InputStreamReader(resourceUrl.openStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				defaultLines.add(line);
			}
		}
		YamlNode defaultTree = parse(defaultLines);

		// 2. Read user lines (if file exists)
		YamlNode userTree;
		if (Files.exists(configPath) && Files.size(configPath) > 0) {
			List<String> userLines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
			userTree = parse(userLines);
		} else {
			userTree = defaultTree;
			defaultTree = new YamlNode();
		}

		// 3. Merge missing keys from defaultTree into userTree
		mergeTrees(userTree, defaultTree, 0);

		// 4. Serialize back to lines, updating values from mergedNode
		List<String> outputLines = new ArrayList<>();
		serialize(userTree, mergedNode, outputLines);

		// 5. Write to disk
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

	private static YamlNode parse(List<String> lines) {
		YamlNode root = new YamlNode();
		YamlNode current = root;
		List<YamlNode> stack = new ArrayList<>();
		stack.add(root);

		List<String> pendingComments = new ArrayList<>();

		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				pendingComments.add(line);
				continue;
			}

			int colonIndex = findUnquotedColon(trimmed);
			if (colonIndex >= 0 && !trimmed.startsWith("-")) {

				int indent = countLeadingSpaces(line);

				while (stack.size() > 1 && stack.get(stack.size() - 1).indent >= indent) {
					stack.remove(stack.size() - 1);
				}
				YamlNode parent = stack.get(stack.size() - 1);

				YamlNode node = new YamlNode();
				node.key = unquote(trimmed.substring(0, colonIndex).trim());
				node.indent = indent;
				node.comments.addAll(pendingComments);
				pendingComments.clear();
				node.keyLine = line;

				parent.children.put(node.key, node);
				stack.add(node);
				current = node;
			} else {
				current.valueLines.addAll(pendingComments);
				pendingComments.clear();
				current.valueLines.add(line);
			}
		}
		root.valueLines.addAll(pendingComments);
		return root;
	}

	private static void mergeTrees(YamlNode userNode, YamlNode defaultNode, int indentDelta) {
		for (Map.Entry<String, YamlNode> entry : defaultNode.children.entrySet()) {
			String key = entry.getKey();
			YamlNode defChild = entry.getValue();

			if (!userNode.children.containsKey(key)) {
				userNode.children.put(key, cloneAndAdjustIndent(defChild, indentDelta));
			} else {
				YamlNode userChild = userNode.children.get(key);
				int newDelta = userChild.indent - defChild.indent;
				mergeTrees(userChild, defChild, newDelta);
			}
		}
	}

	private static YamlNode cloneAndAdjustIndent(YamlNode node, int indentDelta) {
		YamlNode clone = new YamlNode();
		clone.key = node.key;
		clone.indent = node.indent + indentDelta;

		for (String comment : node.comments) {
			clone.comments.add(adjustIndent(comment, indentDelta));
		}
		if (node.keyLine != null) {
			clone.keyLine = adjustIndent(node.keyLine, indentDelta);
		}
		for (String valLine : node.valueLines) {
			clone.valueLines.add(adjustIndent(valLine, indentDelta));
		}
		for (Map.Entry<String, YamlNode> entry : node.children.entrySet()) {
			clone.children.put(entry.getKey(), cloneAndAdjustIndent(entry.getValue(), indentDelta));
		}
		return clone;
	}

	private static String adjustIndent(String line, int delta) {
		if (delta == 0 || line.trim().isEmpty()) return line;
		if (delta > 0) {
			return " ".repeat(delta) + line;
		} else {
			int remove = Math.min(-delta, countLeadingSpaces(line));
			return line.substring(remove);
		}
	}

	private static void serialize(YamlNode node, ConfigurationNode configNode, List<String> out) {
		out.addAll(node.comments);

		if (node.keyLine != null) {
			String line = node.keyLine;

			if (configNode != null && !configNode.virtual() && configNode.raw() != null && !configNode.isMap() && !configNode.isList()) {
				int colonIndex = findUnquotedColon(line);
				if (colonIndex >= 0) {
					String prefix = line.substring(0, colonIndex + 1);
					String afterColon = line.substring(colonIndex + 1);
					String inlineComment = extractInlineComment(afterColon);
					String newValue = formatScalar(configNode.raw());
					
					if (inlineComment != null) {
						line = prefix + " " + newValue + " " + inlineComment;
					} else {
						line = prefix + " " + newValue;
					}
				}
			}
			out.add(line);
		}

		out.addAll(node.valueLines);

		for (Map.Entry<String, YamlNode> entry : node.children.entrySet()) {
			ConfigurationNode childConfig = configNode != null ? configNode.node(entry.getKey()) : null;
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

	private static int findUnquotedColon(final String str) {
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		for (int i = 0; i < str.length(); i++) {
			final char c = str.charAt(i);
			if (c == '\'' && !inDoubleQuote) {
				inSingleQuote = !inSingleQuote;
			} else if (c == '"' && !inSingleQuote) {
				inDoubleQuote = !inDoubleQuote;
			} else if (c == ':' && !inSingleQuote && !inDoubleQuote) {
				return i;
			}
		}
		return -1;
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
		final String str = value.toString();
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
		if (first == '-' || first == '?' || first == ' ') {
			return true;
		}
		return false;
	}
}
