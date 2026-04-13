package network.limewire.sdk;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LimeWireNetworkVersion {
    private static final String VERSION;

    static {
        String version = "unknown";
        try (InputStream is = LimeWireNetworkVersion.class.getResourceAsStream("version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                version = props.getProperty("version", "unknown");
            }
        } catch (IOException e) {
            // ignore
        }
        VERSION = version;
    }

    public static String getVersion() {
        return VERSION;
    }

    public static String getUserAgent() {
        return "LmwrNtwrkJavaSdk/" + getVersion();
    }
}
