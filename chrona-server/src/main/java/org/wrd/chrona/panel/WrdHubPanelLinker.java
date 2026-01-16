package org.wrd.chrona.panel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class WrdHubPanelLinker extends AbstractPanelLinker {
    private static final Logger LOGGER = LoggerFactory.getLogger(WrdHubPanelLinker.class);
    private static final URI URL = URI.create("https://gateway.weirdhub.xyz/auth/chrona/snapshot");

    public WrdHubPanelLinker(PanelInfo info) {
        super(LOGGER, URL, info);
    }

    @Override
    public void run() {
        LOGGER.info("Starting WrdHub Panel Linker");

        super.run();
    }

    @Override
    public void close() {
        LOGGER.info("Stopping WrdHub Panel Linker");

        super.close();
    }
}
