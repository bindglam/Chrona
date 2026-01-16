package org.wrd.chrona.panel;

import org.slf4j.Logger;
import org.wrd.chrona.panel.provider.*;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Timer;

public abstract class AbstractPanelLinker implements PanelLinker {
    private static final List<ApiProvider> PROVIDERS = List.of(new VersionProvider(), new ServerTimeProvider(),
            new TPSProvider(), new PlayerListProvider(), new MSPTProvider(), new EntitiesProvider(), new WorldsProvider(),
            new InventoriesProvider());

    private final Timer timer = new Timer("Chrona-Panel-Linker");

    private final Logger logger;
    private final URL apiUrl;
    private final PanelInfo info;

    public AbstractPanelLinker(Logger logger, URL apiUrl, PanelInfo info) {
        this.logger = logger;
        this.apiUrl = apiUrl;
        this.info = info;
    }

    public AbstractPanelLinker(Logger logger, URI uri, PanelInfo info) {
        this.logger = logger;
        try {
            this.apiUrl = uri.toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        this.info = info;
    }

    @Override
    public void run() {
        timer.scheduleAtFixedRate(new ProvisionTask(logger, this, PROVIDERS), 0L, PanelLinker.PROVISION_INTERVAL);
    }

    @Override
    public void close() {
        timer.cancel();
    }

    @Override
    public PanelInfo info() {
        return info;
    }

    @Override
    public URL apiURL() {
        return apiUrl;
    }
}
