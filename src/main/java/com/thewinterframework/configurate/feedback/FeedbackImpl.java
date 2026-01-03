package com.thewinterframework.configurate.feedback;

import com.thewinterframework.configurate.feedback.media.FeedbackMedia;
import com.thewinterframework.configurate.feedback.media.impl.ChatMedia;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record FeedbackImpl(List<FeedbackMedia> medias) implements Feedback, FeedbackMediaContainer {

    @Override
    public void send(Audience audience, TagResolver... resolver) {
        medias.forEach(media -> media.sendMedia(audience, resolver));
    }

    @Override
    public @NotNull List<Component> asComponents(TagResolver... resolver) throws IllegalStateException {
        final var components = new ArrayList<Component>();
        for (final var media : this.medias) {
            if (media instanceof ChatMedia chatMedia) {
                components.addAll(chatMedia.messages(resolver));
            }
        }
        if (components.isEmpty()) {
            throw new IllegalStateException("No ChatMedia found in Feedback medias to convert to Components.");
        }
        return components;
    }
}
