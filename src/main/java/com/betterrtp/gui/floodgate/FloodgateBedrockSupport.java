package com.betterrtp.gui.floodgate;

import com.betterrtp.gui.BedrockSupport;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;
import java.util.function.IntConsumer;

/** The only class that references Floodgate/Cumulus. Instantiated only when Floodgate is enabled. */
public final class FloodgateBedrockSupport implements BedrockSupport {
    public FloodgateBedrockSupport() {
        FloodgateApi.getInstance();   // fail fast if the API is unusable
    }

    @Override
    public boolean isBedrock(UUID id) {
        return FloodgateApi.getInstance().isFloodgatePlayer(id);
    }

    @Override
    public boolean sendMenu(UUID id, Menu menu, IntConsumer onSelect) {
        SimpleForm.Builder b = SimpleForm.builder().title(menu.title()).content(menu.content());
        for (Button btn : menu.buttons()) {
            FormImage.Type type = null;
            if (btn.imageType() != null && btn.imageData() != null && !btn.imageData().isBlank()) {
                try { type = FormImage.Type.valueOf(btn.imageType()); } catch (IllegalArgumentException ignored) { /* plain button */ }
            }
            if (type != null) b.button(btn.text(), type, btn.imageData());
            else b.button(btn.text());
        }
        b.validResultHandler(response -> onSelect.accept(response.clickedButtonId()));
        return FloodgateApi.getInstance().sendForm(id, b.build());
    }
}
