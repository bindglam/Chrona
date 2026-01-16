package org.wrd.chrona.panel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wrd.chrona.panel.provider.ApiProvider;
import org.wrd.chrona.panel.provider.ServerTimeProvider;
import org.wrd.chrona.panel.provider.TPSProvider;
import org.wrd.chrona.panel.provider.VersionProvider;

import java.net.URI;
import java.util.List;

public class WrdHubPanelLinker extends AbstractPanelLinker {
    private static final Logger LOGGER = LoggerFactory.getLogger(WrdHubPanelLinker.class);
    private static final URI URL = URI.create("https://wrdhub.weirdhost.xyz/auth/chrona/snapshot");
    private static final List<ApiProvider> PROVIDERS = List.of(new VersionProvider(), new ServerTimeProvider(),
            new TPSProvider());

    public WrdHubPanelLinker(PanelInfo info) {
        super(URL, PROVIDERS, info);
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
