/*
 * Copyright 2024-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nycu.winlab.vrouter;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.Ip6Prefix;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.GroupId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.InterfaceIpAddress;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.routeservice.RouteEvent;
import org.onosproject.routeservice.RouteListener;
import org.onosproject.routeservice.RouteService;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.sound.sampled.Port;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final VConfigListener cfgListener = new VConfigListener();

    private final ConfigFactory<ApplicationId, VConfig> factory = new ConfigFactory<ApplicationId, VConfig>(
            APP_SUBJECT_FACTORY, VConfig.class, "router") {
        @Override
        public VConfig createConfig() {
            return new VConfig();
        }
    };

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected RouteService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    private ApplicationId appId;
    VConfig config = null;

    private IntraDomainProcessor intraDomainProcessor = new IntraDomainProcessor();
    private Map<DeviceId, Map<MacAddress, PortNumber>> bridgeTable = new HashMap<>();
    private TransitProcessor transitProcessor = new TransitProcessor();

    private ConnectPoint frrCp;
    private MacAddress frrMac;
    private Ip4Address gatewayIp4;
    private Ip6Address gatewayIp6;
    private MacAddress gatewayMac;
    private List<String> v4Peers;
    private List<String> v6Peers;
    private Ip4Address taGatewayIp4;
    private Ip6Address taGatewayIp6;
    private Ip4Prefix taDomainIp4;
    private Ip6Prefix taDomainIp6;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.winlab.vrouter");
        log.info("vrouter AppComponent started");

        // Register the configuration factory
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);

        // Register the intra-domain processor
        packetService.addProcessor(intraDomainProcessor, PacketProcessor.director(2));
        TrafficSelector selectorIpv4 = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4).build();
        packetService.requestPackets(selectorIpv4, PacketPriority.REACTIVE, appId);
        TrafficSelector selectorIpv6 = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV6).build();
        packetService.requestPackets(selectorIpv6, PacketPriority.REACTIVE, appId);

        // Register the transit processor
        routeService.addListener(transitProcessor);

    }

    @Deactivate
    protected void deactivate() {
        // Unregister the configuration factory
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);

        // Unregister the intra-domain processor
        packetService.removeProcessor(intraDomainProcessor);
        intraDomainProcessor = null;
        TrafficSelector selectorIpv4 = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4).build();
        packetService.cancelPackets(selectorIpv4, PacketPriority.REACTIVE, appId);
        TrafficSelector selectorIpv6 = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV6).build();
        packetService.cancelPackets(selectorIpv6, PacketPriority.REACTIVE, appId);

        // Unregister the transit processor
        routeService.removeListener(transitProcessor);

        log.info("vrouter AppComponent stopped");
    }

    private class VConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED) &&
                    event.configClass().equals(VConfig.class)) {
                VConfig newconfig = cfgService.getConfig(appId, VConfig.class);

                if (newconfig != null) {
                    config = newconfig;
                    // config != null, remove all flows installed by this application
                    // if (config != null) {
                    // TODO: remove all flows installed by this application
                    // for (Intent intent : intentService.getIntentsByAppId(appId)) {
                    // intentService.withdraw(intent);
                    // }
                    // }

                    frrMac = MacAddress.valueOf(config.frrMac());
                    frrCp = ConnectPoint.fromString(config.frrCp());
                    gatewayIp4 = Ip4Address.valueOf(config.gatewayIp4());
                    gatewayIp6 = Ip6Address.valueOf(config.gatewayIp6());
                    gatewayMac = MacAddress.valueOf(config.gatewayMac());
                    v4Peers = config.v4Peers();
                    v6Peers = config.v6Peers();
                    taGatewayIp4 = Ip4Address.valueOf(config.taGatewayIp4());
                    taGatewayIp6 = Ip6Address.valueOf(config.taGatewayIp6());
                    taDomainIp4 = Ip4Prefix.valueOf(config.taDomainIp4());
                    taDomainIp6 = Ip6Prefix.valueOf(config.taDomainIp6());
                    log.info("frr mac = {}", frrMac);
                    log.info("frr connect point = {}", frrCp);
                    log.info("gateway ipv4 = {}, ipv6 = {}", gatewayIp4, gatewayIp6);
                    log.info("gateway mac = {}", gatewayMac);

                    BGPConnection();

                }
            } else {
                log.info("event type = {}", event.type());
            }
        }

        protected void BGPConnection() {
            // Install flows for BGP connection
            for (String peer : v4Peers) {
                String[] ips = peer.split(", ");
                Ip4Address ip1 = Ip4Address.valueOf(ips[0]);
                Ip4Address ip2 = Ip4Address.valueOf(ips[1]);

                Interface intf = interfaceService.getMatchingInterface(ip1);

                TrafficSelector selector1 = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPDst(IpPrefix.valueOf(ip2, 32))
                        .build();
                TrafficSelector selector2 = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPDst(IpPrefix.valueOf(ip1, 32))
                        .build();

                installIntent(frrCp, intf.connectPoint(), selector1, null, 30);
                installIntent(intf.connectPoint(), frrCp, selector2, null, 30);

            }

            for (String peer : v6Peers) {
                String[] ips = peer.split(", ");
                Ip6Address ip1 = Ip6Address.valueOf(ips[0]);
                Ip6Address ip2 = Ip6Address.valueOf(ips[1]);

                Interface intf = interfaceService.getMatchingInterface(ip1);

                TrafficSelector selector1 = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV6)
                        .matchIPv6Dst(Ip6Prefix.valueOf(ip2, 128))
                        .build();
                TrafficSelector selector2 = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV6)
                        .matchIPv6Dst(Ip6Prefix.valueOf(ip1, 128))
                        .build();

                installIntent(frrCp, intf.connectPoint(), selector1, null, 30);
                installIntent(intf.connectPoint(), frrCp, selector2, null, 30);
            }
        }
    }

    // ===== Implement Intra-Domain Processor using learning bridge =====
    private class IntraDomainProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            DeviceId recDevId = pkt.receivedFrom().deviceId();
            PortNumber inPort = pkt.receivedFrom().port();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            // if type ipv4
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                Ip4Address dstIp = Ip4Address
                        .valueOf(((org.onlab.packet.IPv4) ethPkt.getPayload()).getDestinationAddress());
                // i.e. 192.168.70.253, 192.168.63.2
                for (String peer : v4Peers) {
                    String[] ips = peer.split(", ");
                    Ip4Address ip2 = Ip4Address.valueOf(ips[1]);
                    if (dstIp.equals(ip2)) {
                        return;
                    }
                }
            } else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                Ip6Address dstIp = Ip6Address
                        .valueOf(((org.onlab.packet.IPv6) ethPkt.getPayload()).getDestinationAddress());
                for (String peer : v6Peers) {
                    String[] ips = peer.split(", ");
                    Ip6Address ip2 = Ip6Address.valueOf(ips[1]);
                    if (dstIp.equals(ip2)) {
                        return;
                    }
                }
            }

            // rec packet-in from new device, create new table for it
            if (bridgeTable.get(recDevId) == null) {
                bridgeTable.put(recDevId, new HashMap<>());
            }

            if (bridgeTable.get(recDevId).get(srcMac) == null) {
                bridgeTable.get(recDevId).put(srcMac, inPort);
                log.info("Add an entry to the port table of `{}`. MAC address: `{}` => Port: `{}`",
                        recDevId, srcMac, inPort);

            }

            if (bridgeTable.get(recDevId).get(dstMac) == null) {
                // FLOOD
                flood(context);
                log.info("MAC address `{}` is missed on `{}`. Flood the packet.", dstMac, recDevId);

            } else if (bridgeTable.get(recDevId).get(dstMac) != null) {
                // there is a entry store the mapping of dst mac and forwarding port
                // installFlowRule(context, bridgeTable.get(recDevId).get(dstMac));
                ConnectPoint ingressPoint = new ConnectPoint(recDevId, inPort);
                ConnectPoint egressPoint = new ConnectPoint(recDevId, bridgeTable.get(recDevId).get(dstMac));
                TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchEthDst(dstMac).matchEthSrc(srcMac).build();
                Interface intf = interfaceService.getInterfacesByPort(egressPoint).stream().findFirst().orElse(null);
                if (intf == null) {
                    installIntent(ingressPoint, egressPoint, selector, null, 20);
                    log.info("MAC address `{}` is matched on `{}`. Install a flow rule.", dstMac, recDevId);
                }
            }
        }

        private void flood(PacketContext context) {
            for (ConnectPoint cp : edgePortService.getEdgePoints()) {
                if (cp.equals(context.inPacket().receivedFrom())) {
                    continue;
                }

                boolean isInterface = false;
                Set<Interface> intfsets = interfaceService.getInterfaces();
                for (Interface intf : intfsets) {
                    if (intf.connectPoint().equals(cp)) {
                        isInterface = true;
                        break;
                    }
                }
                if (!isInterface) {
                    packetOut(context.inPacket().parsed(), cp);
                    log.info("Flood packet to {}", cp);
                }

            }
        }

        private void packetOut(Ethernet ethPkt, ConnectPoint cp) {
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(cp.port())
                    .build();
            OutboundPacket outPacket = new DefaultOutboundPacket(
                    cp.deviceId(), treatment, ByteBuffer.wrap(ethPkt.serialize()));
            packetService.emit(outPacket);
        }
    }

    // ===== Implement Transit Processor using RouteService =====
    public class TransitProcessor implements RouteListener {
        @Override
        public void event(RouteEvent event) {
            switch (event.type()) {
                case ROUTE_ADDED:
                    System.out.println("Route added: " + event.subject());
                    break;
                case ROUTE_UPDATED:
                    System.out.println("Route updated: " + event.subject());
                    break;
                case ROUTE_REMOVED:
                    System.out.println("Route removed: " + event.subject());
                    break;
                default:
                    break;
            }
        }

        private void installTransitIntent() {

        }
    }

    // ===== Tool Functions =====
    protected void installIntent(ConnectPoint ingressPoint, ConnectPoint egressPoint, TrafficSelector selector,
            TrafficTreatment treatment, int priority) {
        PointToPointIntent.Builder intent = PointToPointIntent.builder()
                .appId(appId)
                .priority(priority)
                .filteredIngressPoint(new FilteredConnectPoint(ingressPoint))
                .filteredEgressPoint(new FilteredConnectPoint(egressPoint))
                .selector(selector);

        if (treatment != null)
            intent.treatment(treatment);

        intentService.submit(intent.build());

        log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                ingressPoint.deviceId(), ingressPoint.port(),
                egressPoint.deviceId(), egressPoint.port());
    }

    protected void installMultiIntent(Set<FilteredConnectPoint> srcPoint,
            ConnectPoint dstPoint,
            TrafficTreatment.Builder treatment) {

        MultiPointToSinglePointIntent.Builder p2pIntent = MultiPointToSinglePointIntent.builder()
                .appId(appId)
                .priority(30)
                .treatment(treatment.build())
                .filteredIngressPoints(srcPoint)
                .filteredEgressPoint(new FilteredConnectPoint(dstPoint));

        intentService.submit(p2pIntent.build());
        log.info("Intent `{}` => `{}` is submitted.",
                srcPoint,
                dstPoint);

    }

}
