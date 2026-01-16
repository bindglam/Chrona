package org.wrd.chrona.panel;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public record PanelInfo(String serverId, String secret, PanelType panelType) {
    public static @Nullable PanelInfo loadFromFile(File file) {
        if(!file.exists()) return null;

        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String serverId = reader.readLine();
            String secret = reader.readLine();
            PanelType panelType = PanelType.valueOf(reader.readLine());

            return new PanelInfo(serverId, secret, panelType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PanelLinker createLinker() {
        return panelType().getFactory().apply(this);
    }
}
