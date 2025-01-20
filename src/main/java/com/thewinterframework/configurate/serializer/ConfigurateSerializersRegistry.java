package com.thewinterframework.configurate.serializer;

import com.thewinterframework.configurate.feedback.Feedback;
import com.thewinterframework.configurate.feedback.media.FeedbackMedia;
import com.thewinterframework.configurate.feedback.serializer.FeedbackMediaSpongeSerializer;
import com.thewinterframework.configurate.feedback.serializer.FeedbackSpongeSerializer;
import com.thewinterframework.configurate.serializer.common.ComponentSerializer;
import net.kyori.adventure.text.Component;
import org.spongepowered.configurate.serialize.TypeSerializer;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * A registry for {@link TypeSerializer}s.
 */
public class ConfigurateSerializersRegistry {

	private final Map<Type, TypeSerializer<?>> serializers = new HashMap<>();

	public ConfigurateSerializersRegistry() {
		serializers.put(Feedback.class, new FeedbackSpongeSerializer());
		serializers.put(FeedbackMedia.class, new FeedbackMediaSpongeSerializer());
		serializers.put(Component.class, new ComponentSerializer());
	}

	/**
	 * Registers a {@link TypeSerializer} for the specified class.
	 *
	 * @param clazz the class to register
	 * @param serializer the serializer to register
	 */
	public void registerSerializer(Type clazz, TypeSerializer<?> serializer) {
		serializers.put(clazz, serializer);
	}

	/**
	 * Gets the {@link TypeSerializerCollection} containing all registered serializers.
	 *
	 * @return the collection of serializers
	 */
	@SuppressWarnings("unchecked, unused")
	public TypeSerializerCollection getSerializers() {
		final var collection = TypeSerializerCollection.defaults().childBuilder();
		for (final var entry : serializers.entrySet()) {
			collection.register((Class<Object>) entry.getKey(), (TypeSerializer<Object>) entry.getValue());
		}

		return collection.build();
	}

}
