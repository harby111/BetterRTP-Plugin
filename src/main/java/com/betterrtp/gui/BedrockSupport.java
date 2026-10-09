package com.betterrtp.gui;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Bedrock integration seam. Contains NO Floodgate/Cumulus types, so loading this interface (and the plugin)
 * never touches optional classes. The Floodgate-backed implementation lives in {@code gui.floodgate}.
 */
public interface BedrockSupport {
    record Button(String text, String imageType, String imageData) { }
    record Menu(String title, String content, List<Button> buttons) { }

    boolean isBedrock(UUID id);

    /** Sends the form. The callback may arrive on any thread. @return false if it could not be sent. */
    boolean sendMenu(UUID id, Menu menu, IntConsumer onSelect);

    BedrockSupport NONE = new BedrockSupport() {
        @Override public boolean isBedrock(UUID id) { return false; }
        @Override public boolean sendMenu(UUID id, Menu menu, IntConsumer onSelect) { return false; }
    };
}
