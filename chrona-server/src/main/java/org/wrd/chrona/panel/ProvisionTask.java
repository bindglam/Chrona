package org.wrd.chrona.panel;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.wrd.chrona.panel.provider.ApiProvider;
import org.wrd.chrona.util.StringUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.TimerTask;

public class ProvisionTask extends TimerTask {
    private final Logger logger;
    private final PanelLinker linker;
    private final List<ApiProvider> providers;

    public ProvisionTask(Logger logger, PanelLinker linker, List<ApiProvider> providers) {
        this.logger = logger;
        this.linker = linker;
        this.providers = providers;
    }

    @Override
    public void run() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String body = getBody();
        String signature = getSignature(timestamp, body);

        try {
            HttpURLConnection connection = (HttpURLConnection) linker.apiURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Server-Id", linker.info().serverId());
            connection.setRequestProperty("X-Timestamp", timestamp);
            connection.setRequestProperty("X-Signature", signature);
            //connection.setRequestProperty("X-Panel-Host", linker.info().panelHost());

            try(OutputStream os = connection.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if(responseCode == 200) return;

            try(BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                logger.warn("Failed to link panel ( Code : {} )", responseCode);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getBody() {
        JsonObject bodyJson = new JsonObject();

        providers.forEach((provider) -> bodyJson.add(provider.id(), provider.provide()));

        return bodyJson.toString();
    }

    private String getSignature(String timestamp, String body) {
        String signTarget = timestamp + "." + body;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(linker.info().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return StringUtil.bytesToHex(mac.doFinal(signTarget.getBytes(StandardCharsets.UTF_8)));
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
