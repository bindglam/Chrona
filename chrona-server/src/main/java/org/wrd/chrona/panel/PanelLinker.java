package org.wrd.chrona.panel;

import java.io.File;
import java.net.URL;

public interface PanelLinker extends Runnable, AutoCloseable {
    int API_VERSION = 1;
    int PROVISION_INTERVAL = 5000;
    File INFO_FILE = new File(".info");

    PanelInfo info();

    URL apiURL();

    @Override
    void close();
}
