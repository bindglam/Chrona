package org.wrd.chrona.panel;

import java.util.function.Function;

public enum PanelType {
    WRDHUB(WrdHubPanelLinker::new),
    WEIRDHOST(WrdHubPanelLinker::new); // TODO

    private final Function<PanelInfo, PanelLinker> factory;

    PanelType(Function<PanelInfo, PanelLinker> factory) {
        this.factory = factory;
    }

    public Function<PanelInfo, PanelLinker> getFactory() {
        return factory;
    }
}
