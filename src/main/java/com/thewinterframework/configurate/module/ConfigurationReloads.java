package com.thewinterframework.configurate.module;

import com.thewinterframework.component.annotation.External;
import com.thewinterframework.service.annotation.lifecycle.OnDisable;
import com.thewinterframework.service.annotation.lifecycle.OnReload;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Owns generated container and language reload callbacks for one plugin injector. */
@External
public final class ConfigurationReloads {
    private final CopyOnWriteArrayList<Runnable> callbacks = new CopyOnWriteArrayList<>();

    public void register(final Runnable callback) {
        callbacks.add(Objects.requireNonNull(callback, "callback"));
    }

    @OnReload
    public void reload() {
        callbacks.forEach(Runnable::run);
    }

    @OnDisable
    public void clear() {
        callbacks.clear();
    }
}
