package com.thewinterframework.configurate;

import com.thewinterframework.configurate.config.FillMissingKeys;
import com.thewinterframework.configurate.config.OptionalNode;
import io.leangen.geantyref.GenericTypeReflector;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class MissingDefaults {
	private final String original;
	private final String newline;
	private final ConfigurationOptions options;
	private final Map<Integer, String> inserts = new TreeMap<>();

	private MissingDefaults(String original, ConfigurationOptions options) {
		this.original = original;
		this.newline = original.contains("\r\n") ? "\r\n" : "\n";
		this.options = options;
	}

	static String complete(String original, String template, ObjectMapper<?> mapper, ConfigurationOptions options)
			throws SerializationException {
		var yaml = new Yaml();
		Node defaults = yaml.compose(new StringReader(template));
		if (!(defaults instanceof MappingNode mapping)) {
			return original;
		}
		var completion = new MissingDefaults(original, options);
		Node target = yaml.compose(new StringReader(original));
		if (target instanceof MappingNode current) {
			if (current.getValue().isEmpty() && canComplete(current)) {
				var missing = completion.collect(null, mapping, mapper);
				if (!missing.isEmpty()) {
					completion.insert(current, missing);
				}
			} else {
				completion.collect(current, mapping, mapper);
			}
		} else if (target == null) {
			var missing = completion.collect(null, mapping, mapper);
			if (!missing.isEmpty()) {
				String prefix = original.isEmpty() || original.endsWith("\n") ? "" : completion.newline;
				completion.inserts.put(original.length(), prefix
						+ render(missing, DumperOptions.FlowStyle.BLOCK).replace("\n", completion.newline));
			}
		}
		var output = new StringBuilder();
		int cursor = 0;
		for (var entry : completion.inserts.entrySet()) {
			output.append(original, cursor, entry.getKey()).append(entry.getValue());
			cursor = entry.getKey();
		}
		return output.append(original, cursor, original.length()).toString();
	}

	private List<NodeTuple> collect(MappingNode target, MappingNode defaults, ObjectMapper<?> mapper)
			throws SerializationException {
		if (target != null && (target.getValue().isEmpty() || !canComplete(target))) {
			return List.of();
		}
		var existing = entries(target);
		var template = entries(defaults);
		var missing = new ArrayList<NodeTuple>();
		for (var field : mapper.fields()) {
			var node = field.resolveNode(BasicConfigurationNode.root(this.options));
			if (node == null || node.path().size() != 1 || !(node.key() instanceof String key)) {
				continue;
			}
			var fallback = template.get(key);
			if (fallback == null) {
				continue;
			}
			var current = existing.get(key);
			var type = field.resolvedType();
			if (current == null && type.isAnnotationPresent(OptionalNode.class)) {
				continue;
			}
			var nested = GenericTypeReflector.erase(type.getType());
			var serializers = this.options.serializers();
			boolean mapped = nested.isAnnotationPresent(ConfigSerializable.class)
					&& serializers.get(type) == TypeSerializerCollection.defaults().get(type);
			Node value = fallback.getValueNode();
			if (mapped && value instanceof MappingNode defaultMap) {
				var nestedMapper = ObjectMapper.factory().get(type.getType());
				if (current == null && !defaultMap.getValue().isEmpty()) {
					var children = this.collect(null, defaultMap, nestedMapper);
					if (children.isEmpty()) {
						continue;
					}
					value = new MappingNode(Tag.MAP, children, defaultMap.getFlowStyle());
				} else if (current != null && current.getValueNode() instanceof MappingNode currentMap) {
					this.collect(currentMap, defaultMap, nestedMapper);
				}
			} else if (type.isAnnotationPresent(FillMissingKeys.class) && Map.class.isAssignableFrom(nested)
					&& value instanceof MappingNode defaultMap && current != null
					&& current.getValueNode() instanceof MappingNode currentMap && canComplete(currentMap)) {
				var keys = entries(currentMap);
				var missingKeys = defaultMap.getValue().stream()
						.filter(entry -> entry.getKeyNode() instanceof ScalarNode scalar
								&& Tag.STR.equals(scalar.getTag()) && !keys.containsKey(scalar.getValue()))
						.toList();
				if (!missingKeys.isEmpty()) {
					this.insert(currentMap, missingKeys);
				}
			}
			if (current == null) {
				missing.add(new NodeTuple(fallback.getKeyNode(), value));
			}
		}
		if (target != null && !missing.isEmpty()) {
			this.insert(target, missing);
		}
		return missing;
	}

	private void insert(MappingNode target, List<NodeTuple> missing) {
		if (target.getValue().isEmpty()) {
			int offset = this.original.offsetByCodePoints(0, target.getEndMark().getIndex() - 1);
			String pair = render(missing, DumperOptions.FlowStyle.FLOW).strip();
			this.inserts.put(offset, pair.substring(1, pair.length() - 1));
			return;
		}
		var firstKey = target.getValue().get(0).getKeyNode().getStartMark();
		int offset = this.original.offsetByCodePoints(0, firstKey.getIndex());
		boolean flow = target.getFlowStyle() == DumperOptions.FlowStyle.FLOW;
		String value = render(missing, target.getFlowStyle());
		if (flow) {
			String pair = value.strip();
			this.inserts.put(offset, pair.substring(1, pair.length() - 1) + ", ");
			return;
		}
		int lineStart = this.original.lastIndexOf('\n', offset - 1) + 1;
		if (!this.original.substring(lineStart, offset).isBlank()) {
			return;
		}
		int indent = firstKey.getColumn();
		while (lineStart > 0) {
			int previous = this.original.lastIndexOf('\n', lineStart - 2) + 1;
			String line = this.original.substring(previous, lineStart - 1);
			if (!line.stripLeading().startsWith("#") || line.length() - line.stripLeading().length() < indent) {
				break;
			}
			lineStart = previous;
		}
		var prefix = " ".repeat(indent);
		var text = new StringBuilder();
		value.lines().forEach(line -> text.append(prefix).append(line).append(this.newline));
		this.inserts.put(lineStart, text.toString());
	}

	private static boolean canComplete(MappingNode mapping) {
		return mapping.getAnchor() == null && Tag.MAP.equals(mapping.getTag())
				&& mapping.getValue().stream().noneMatch(entry -> Tag.MERGE.equals(entry.getKeyNode().getTag()));
	}

	private static String render(List<NodeTuple> entries, DumperOptions.FlowStyle style) {
		var writer = new StringWriter();
		new Yaml().serialize(new MappingNode(Tag.MAP, entries, style), writer);
		return writer.toString();
	}

	private static Map<String, NodeTuple> entries(MappingNode mapping) {
		var result = new HashMap<String, NodeTuple>();
		if (mapping != null) {
			for (var entry : mapping.getValue()) {
				if (entry.getKeyNode() instanceof ScalarNode scalar && Tag.STR.equals(scalar.getTag())) {
					result.put(scalar.getValue(), entry);
				}
			}
		}
		return result;
	}
}
