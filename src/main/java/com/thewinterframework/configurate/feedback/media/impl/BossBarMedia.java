package com.thewinterframework.configurate.feedback.media.impl;

import com.thewinterframework.component.ComponentUtils;
import com.thewinterframework.configurate.feedback.media.FeedbackMedia;
import com.thewinterframework.configurate.feedback.media.MediaType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.Objects;

public record BossBarMedia(String message, float progress, Color color, Overlay overlay) implements FeedbackMedia {
    @Override
    public void sendMedia(final Audience audience, final TagResolver... resolvers) {
        final var name = ComponentUtils.miniMessage(message, resolvers);
        final var bossBar = BossBar.bossBar(name, progress, color, overlay);
        audience.showBossBar(bossBar);
    }

    @Override
    public MediaType type() {
        return MediaType.BOSSBAR;
    }

    public static class BossBarMediaSerializer implements TypeSerializer<FeedbackMedia> {
        @Override
        public FeedbackMedia deserialize(final Type type, final ConfigurationNode node) {
            return new BossBarMedia(
                Objects.requireNonNull(node.node("message").getString()),
                node.node("progress").getFloat(1.0f),
                Color.valueOf(node.node("color").getString(Color.PURPLE.name()).toUpperCase()),
                Overlay.valueOf(node.node("overlay").getString(Overlay.PROGRESS.name()).toUpperCase())
            );
        }

        @Override
        public void serialize(final Type type, @Nullable final FeedbackMedia obj, final ConfigurationNode node) throws SerializationException {
            if (!(obj instanceof BossBarMedia(String message1, float progress1, Color color1, Overlay overlay1))) {
                throw new SerializationException("Invalid media type");
            }

            node.node("type").set(MediaType.BOSSBAR.name().toLowerCase());
            node.node("message").set(message1);
            node.node("progress").set(progress1);
            node.node("color").set(color1.name().toLowerCase());
            node.node("overlay").set(overlay1.name().toLowerCase());
        }
    }
}
