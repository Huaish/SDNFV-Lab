package nycu.winlab.groupmeter;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

public class NameConfig extends Config<ApplicationId> {
    public static final String HOST1 = "host-1";
    public static final String HOST2 = "host-2";
    public static final String HOST3 = "host-3";
    public static final String MAC1 = "mac-1";
    public static final String MAC2 = "mac-2";
    public static final String MAC3 = "mac-3";
    public static final String IP1 = "ip-1";
    public static final String IP2 = "ip-2";
    public static final String IP3 = "ip-3";

    @Override
    public boolean isValid() {
        return hasOnlyFields(HOST1, HOST2, HOST3, MAC1, MAC2, MAC3, IP1, IP2, IP3);
    }

    public String getHost1() {
        return get(HOST1, null);
    }

    public String getHost2() {
        return get(HOST2, null);
    }

    public String getHost3() {
        return get(HOST3, null);
    }

    public String getMac1() {
        return get(MAC1, null);
    }

    public String getMac2() {
        return get(MAC2, null);
    }

    public String getMac3() {
        return get(MAC3, null);
    }

    public String getIp1() {
        return get(IP1, null);
    }

    public String getIp2() {
        return get(IP2, null);
    }

    public String getIp3() {
        return get(IP3, null);
    }

}
