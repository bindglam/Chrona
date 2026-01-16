package org.wrd.chrona.panel;

import org.slf4j.Logger;
import org.wrd.chrona.panel.provider.ApiProvider;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Timer;

public abstract class AbstractPanelLinker implements PanelLinker {
    private final Timer timer = new Timer("Chrona-Panel-Linker");

    private final Logger logger;
    private final URL apiUrl;
    private final List<ApiProvider> providers;
    private final PanelInfo info;

    public AbstractPanelLinker(Logger logger, URL apiUrl, List<ApiProvider> providers, PanelInfo info) {
        this.logger = logger;
        this.apiUrl = apiUrl;
        this.providers = providers;
        this.info = info;
    }

    public AbstractPanelLinker(Logger logger, URI uri, List<ApiProvider> providers, PanelInfo info) {
        this.logger = logger;
        try {
            this.apiUrl = uri.toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        this.providers = providers;
        this.info = info;
    }

    @Override
    public void run() {
        timer.scheduleAtFixedRate(new ProvisionTask(logger, this, providers), 0L, PanelLinker.PROVISION_INTERVAL);
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
