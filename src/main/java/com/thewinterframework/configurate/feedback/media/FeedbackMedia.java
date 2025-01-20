package com.thewinterframework.configurate.feedback.media;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Represents a media type that can be sent to an {@link Audience}.
 */
@ConfigSerializable
public interface FeedbackMedia {

    /**
     * Sends the media to the specified {@link Audience}.
     *
     * @param audience the audience to send the media to
     * @param resolvers the tag resolvers to use
     */
    void sendMedia(final Audience audience, final TagResolver... resolvers);

    /**
     * Gets the type of media.
     *
     * @return the type of media
     */
    MediaType type();

}
