package com.thewinterframework.configurate.feedback.media.impl;

import com.thewinterframework.component.ComponentUtils;
import com.thewinterframework.configurate.feedback.media.FeedbackMedia;
import com.thewinterframework.configurate.feedback.media.MediaType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.List;

public record ChatMedia(List<String> messages) implements FeedbackMedia {
    public ChatMedia(final String message) {
        this(List.of(message));
    }

    @Override
    public void sendMedia(final Audience audience, final TagResolver... resolvers) {
        messages.forEach(message -> audience.sendMessage(ComponentUtils.miniMessage(message, true, resolvers)));
    }

    public List<Component> messages(final TagResolver... resolvers) {
        return messages.stream()
            .map(msg -> ComponentUtils.miniMessage(msg, true, resolvers))
            .toList();
    }

    @Override
    public MediaType type() {
        return MediaType.CHAT;
    }

    public static class ChatMediaSerializer implements TypeSerializer<FeedbackMedia> {
        @Override
        public FeedbackMedia deserialize(@NotNull final Type type, final ConfigurationNode node) throws SerializationException {
            final var msgNode = node.node("message");
            if (msgNode.isList()) {
                return new ChatMedia(msgNode.getList(String.class));
            }

            return new ChatMedia(msgNode.getString(msgNode.path().toString()));
        }

        @Override
        public void serialize(final Type type, @Nullable final FeedbackMedia obj, final ConfigurationNode node) throws SerializationException {
            if (!(obj instanceof ChatMedia(List<String> messages1))) {
                throw new SerializationException("Invalid media type");
            }

            node.node("type").set(MediaType.CHAT.name().toLowerCase());

            if (messages1.size() == 1) {
                node.node("message").set(messages1.get(0));
                return;
            }

            node.node("message").setList(String.class, messages1);
        }
    }
}
