package nycu.winlab.ProxyArp;

import org.onosproject.net.config.Config;
import org.onosproject.core.ApplicationId;

@SuppressWarnings("UnstableApiUsage")
public class InfoConfig extends Config<ApplicationId> {
    private static final String VIP4 = "virtual-ip4";
    private static final String VIP6 = "virtual-ip6";
    private static final String VMAC = "virtual-mac";

    @Override
    public boolean isValid() {
        return hasFields(VIP4, VIP6, VMAC);
    }

    public String vip4() {
        return get(VIP4, null);
    }

    public String vip6() {
        return get(VIP6, null);
    }

    public String vmac() {
        return get(VMAC, null);
    }
}
