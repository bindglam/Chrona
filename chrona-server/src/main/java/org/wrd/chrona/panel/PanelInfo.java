package org.wrd.chrona.panel;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Base64;

public record PanelInfo(String serverId, String secret, PanelType panelType) {
    public static @Nullable PanelInfo loadFromFile(File file) {
        if(!file.exists()) return null;

        Base64.Decoder decoder = Base64.getDecoder();

        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String serverId = new String(decoder.decode(reader.readLine()));
            String secret = new String(decoder.decode(reader.readLine()));
            PanelType panelType = PanelType.valueOf(new String(decoder.decode(reader.readLine())));

            return new PanelInfo(serverId, secret, panelType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PanelLinker createLinker() {
        return panelType().getFactory().apply(this);
    }
}
