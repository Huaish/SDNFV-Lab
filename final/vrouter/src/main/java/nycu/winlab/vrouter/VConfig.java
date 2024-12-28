package nycu.winlab.vrouter;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import java.util.List;
import java.util.function.Function;

public class VConfig extends Config<ApplicationId> {
    public static final String frrCp = "frr";
    public static final String frrMac = "frr-mac";
    public static final String gatewayIp4 = "gateway-ip4";
    public static final String gatewayIp6 = "gateway-ip6";
    public static final String gatewayMac = "gateway-mac";
    public static final String v4Peers = "v4-peers";
    public static final String v6Peers = "v6-peers";
    private static final String TA_GATEWAY_IP4 = "ta-gateway-ip4";
    private static final String TA_GATEWAY_IP6 = "ta-gateway-ip6";
    private static final String TA_DOMAIN_IP4 = "ta-domain-ip4";
    private static final String TA_DOMAIN_IP6 = "ta-domain-ip6";

    public String frrCp() {
        return get(frrCp, null);
    }

    public String frrMac() {
        return get(frrMac, null);
    }

    public String gatewayIp4() {
        return get(gatewayIp4, null);
    }

    public String gatewayIp6() {
        return get(gatewayIp6, null);
    }

    public String gatewayMac() {
        return get(gatewayMac, null);
    }

    public List<String> v4Peers() {
        return getList(v4Peers, Function.identity());
    }

    public List<String> v6Peers() {
        return getList(v6Peers, Function.identity());
    }

    public String taGatewayIp4() {
        return get(TA_GATEWAY_IP4, null);
    }

    public String taGatewayIp6() {
        return get(TA_GATEWAY_IP6, null);
    }

    public String taDomainIp4() {
        return get(TA_DOMAIN_IP4, null);
    }

    public String taDomainIp6() {
        return get(TA_DOMAIN_IP6, null);
    }
}
