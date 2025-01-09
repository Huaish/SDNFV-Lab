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

import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP6;
import org.onlab.packet.IPv4;
import org.onlab.packet.IPv6;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.Ip6Prefix;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.ndp.NeighborDiscoveryOptions;
import org.onlab.packet.ndp.NeighborSolicitation;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.host.InterfaceIpAddress;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.routeservice.ResolvedRoute;
import org.onosproject.routeservice.RouteEvent;
import org.onosproject.routeservice.RouteInfo;
import org.onosproject.routeservice.RouteListener;
import org.onosproject.routeservice.RouteService;
import org.onosproject.routeservice.RouteTableId;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger("vrouter");
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

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    private ApplicationId appId;
    VConfig config = null;

    private IntraInterProcessor intraInterProcessor = new IntraInterProcessor();
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
        packetService.addProcessor(intraInterProcessor, PacketProcessor.director(2));
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
        packetService.removeProcessor(intraInterProcessor);
        intraInterProcessor = null;
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
                    log.info("R1 mac = {}", frrMac);
                    log.info("R1 connect point = {}", frrCp);
                    log.info("gateway ipv4 = {}, ipv6 = {}", gatewayIp4, gatewayIp6);
                    log.info("gateway mac = {}", gatewayMac);

                    for (String peer : v4Peers) {
                        log.info("v4Peer = {}", peer);
                        String[] ips = peer.split(", ");
                        floodArp(Ip4Address.valueOf(ips[1]));
                    }
                    for (String peer : v6Peers) {
                        log.info("v6Peer = {}", peer);
                        String[] ips = peer.split(", ");
                        floodNdp(Ip6Address.valueOf(ips[1]));
                    }

                    withdrawPointToPointIntent();
                    BGPConnection();

                }
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

                installIntent(frrCp, intf.connectPoint(), selector1, null, 100);
                installIntent(intf.connectPoint(), frrCp, selector2, null, 100);

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

                installIntent(frrCp, intf.connectPoint(), selector1, null, 100);
                installIntent(intf.connectPoint(), frrCp, selector2, null, 100);
            }
        }
    }

    // ===== Implement Intra-Domain and Inter-Domain Processor =====
    private class IntraInterProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            boolean externalIn = processExternalIn(context);
            boolean externalOut = processExternalOut(context);

            if (externalIn || externalOut) {
                return;
            }

            processIntraDomain(context);
        }

        private void flood(PacketContext context) {

            for (ConnectPoint cp : edgePortService.getEdgePoints()) {
                if (cp.equals(context.inPacket().receivedFrom())) {
                    continue;
                }
                packetOut(context.inPacket().parsed(), cp);

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

        private boolean processExternalIn(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
                Ip4Address srcIp = Ip4Address.valueOf(ipv4Packet.getSourceAddress());
                Ip4Address dstIp = Ip4Address.valueOf(ipv4Packet.getDestinationAddress());

                Optional<ResolvedRoute> route = routeService.longestPrefixLookup(srcIp);
                if (!route.isPresent()) {
                    log.warn("No Route srcIp!" + srcIp);
                    return false;
                }

                hostService.requestMac(dstIp);
                Host dstHost = getHost(dstIp);
                if (dstHost == null) {
                    log.warn("Dst host not found! Flood!");
                    flood(context);
                    context.block();
                    return false;
                }

                MacAddress dstMac = dstHost.mac();
                ConnectPoint egress = dstHost.location();
                ConnectPoint ingress = pkt.receivedFrom();

                TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
                selector.matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPDst(IpPrefix.valueOf(dstIp, 32))
                        .matchEthDst(frrMac);

                TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
                treatment.setEthDst(dstMac) // get from hostService
                        .setEthSrc(gatewayMac);

                installIntent(ingress, egress, selector.build(), treatment.build(), 24);
            } else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                IPv6 ipv6Packet = (IPv6) ethPkt.getPayload();
                Ip6Address srcIp = Ip6Address.valueOf(ipv6Packet.getSourceAddress());
                Ip6Address dstIp = Ip6Address.valueOf(ipv6Packet.getDestinationAddress());

                Optional<ResolvedRoute> route = routeService.longestPrefixLookup(srcIp);
                if (!route.isPresent()) {
                    log.warn("No Route srcIp!" + srcIp);
                    context.block();
                    return false;
                }

                hostService.requestMac(dstIp);
                Host dstHost = getHost(dstIp);
                if (dstHost == null) {
                    log.warn("Dst host not found! Flood!");
                    flood(context);
                    context.block();
                    return false;
                }

                MacAddress dstMac = dstHost.mac();
                ConnectPoint egress = dstHost.location();
                ConnectPoint ingress = pkt.receivedFrom();

                TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
                selector.matchEthType(Ethernet.TYPE_IPV6)
                        .matchIPv6Dst(IpPrefix.valueOf(dstIp, 128));

                TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
                treatment.setEthDst(dstMac) // get from hostService
                        .setEthSrc(frrMac);

                installIntent(ingress, egress, selector.build(), treatment.build(), 24);
            }

            return true;

        }

        private boolean processExternalOut(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
                Ip4Address dstIp = Ip4Address.valueOf(ipv4Packet.getDestinationAddress());

                ConnectPoint ingress = pkt.receivedFrom();

                Optional<ResolvedRoute> route = routeService.longestPrefixLookup(dstIp);
                if (!route.isPresent()) {
                    log.warn("No Route dstIp!" + dstIp);
                    return false;
                }

                IpAddress nextHopIp = route.get().nextHop();
                MacAddress nextHopMac = getHost(nextHopIp).mac();
                Interface intf = interfaceService.getMatchingInterface(nextHopIp);
                ConnectPoint egress = intf.connectPoint();
                TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
                selector.matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPDst(IpPrefix.valueOf(dstIp, 32));

                TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
                treatment.setEthDst(nextHopMac) // get from hostService
                        .setEthSrc(gatewayMac);

                installIntent(ingress, egress, selector.build(), treatment.build(), 25);
            } else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                IPv6 ipv6Packet = (IPv6) ethPkt.getPayload();
                Ip6Address dstIp = Ip6Address.valueOf(ipv6Packet.getDestinationAddress());

                ConnectPoint ingress = pkt.receivedFrom();

                Optional<ResolvedRoute> route = routeService.longestPrefixLookup(dstIp);
                if (!route.isPresent()) {
                    log.warn("No Route dstIp!" + dstIp);
                    return false;
                }

                IpAddress nextHopIp = route.get().nextHop();
                MacAddress nextHopMac = getHost(nextHopIp).mac();
                Interface intf = interfaceService.getMatchingInterface(nextHopIp);
                ConnectPoint egress = intf.connectPoint();
                TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
                selector.matchEthType(Ethernet.TYPE_IPV6)
                        .matchIPv6Dst(IpPrefix.valueOf(dstIp, 128));

                TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
                treatment.setEthDst(nextHopMac) // get from hostService
                        .setEthSrc(gatewayMac);

                installIntent(ingress, egress, selector.build(), treatment.build(), 25);
            }

            return true;
        }

        private void processIntraDomain(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            DeviceId recDevId = pkt.receivedFrom().deviceId();
            PortNumber inPort = pkt.receivedFrom().port();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            log.info("recDevId = {}, srcMac = {}, dstMac = {}, inPort = {}", recDevId, srcMac, dstMac, inPort);

            // rec packet-in from new device, create new table for it
            if (bridgeTable.get(recDevId) == null) {
                bridgeTable.put(recDevId, new HashMap<>());
            }

            if (bridgeTable.get(recDevId).get(srcMac) == null) {
                bridgeTable.get(recDevId).put(srcMac, inPort);
                log.info("Add an entry to the port table of `{}`. MAC address: `{}` => Port:`{}`",
                        recDevId, srcMac, inPort);
            }

            if (bridgeTable.get(recDevId).get(dstMac) == null) {
                flood(context);
            } else if (bridgeTable.get(recDevId).get(dstMac) != null) {
                ConnectPoint ingressPoint = new ConnectPoint(recDevId, inPort);
                ConnectPoint egressPoint = new ConnectPoint(recDevId,
                        bridgeTable.get(recDevId).get(dstMac));
                TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchEthDst(dstMac).matchEthSrc(srcMac).build();
                installIntent(ingressPoint, egressPoint, selector, null, 20);
                log.info("MAC address `{}` is matched on `{}`. Install a flow rule.", dstMac,
                        recDevId);
            }
        }
    }

    // ===== Implement Transit Processor using RouteService =====
    public class TransitProcessor implements RouteListener {
        @Override
        public void event(RouteEvent event) {
            switch (event.type()) {
                case ROUTE_ADDED:
                    // log.info("Route added: " + event.subject());
                    installTransitIntent();
                    break;
                case ROUTE_UPDATED:
                    // log.info("Route updated: " + event.subject());
                    // withdrawMultiPointToSinglePointIntent();
                    installTransitIntent();
                    break;
                case ROUTE_REMOVED:
                    // log.info("Route removed: " + event.subject());
                    break;
                default:
                    break;
            }
        }

        private void installTransitIntent() {

            Collection<RouteTableId> routeTables = routeService.getRouteTables();
            for (RouteTableId routeTable : routeTables) {
                Collection<RouteInfo> routes = routeService.getRoutes(routeTable);
                for (RouteInfo routeInfo : routes) {
                    Optional<ResolvedRoute> route = routeInfo.bestRoute();
                    if (route.isPresent()) {
                        log.info("-----------------");
                        ResolvedRoute bestRoute = route.get();

                        log.info("Best Route Prefix: " + bestRoute.prefix());
                        log.info("Next Hop: " + bestRoute.nextHop());

                        Iterable<Interface> interfaces = interfaceService.getInterfaces();
                        Set<FilteredConnectPoint> srcPoints = new HashSet<>();
                        int numOdCP = 0;
                        for (Interface intf : interfaces) {
                            boolean addSrcPoint = true;
                            try {
                                for (InterfaceIpAddress ip : intf.ipAddressesList()) {
                                    IpAddress ipAddress = ip.ipAddress();
                                    IpPrefix subnetPrefix = ip.subnetAddress();
                                    log.info("Interface IP Prefix: " + subnetPrefix.toString());

                                    if (ipAddress.isIp4() && bestRoute.nextHop().isIp4()) {
                                        if (subnetPrefix.contains(bestRoute.nextHop())) {
                                            log.info(bestRoute.nextHop() + " is in " + subnetPrefix);
                                            addSrcPoint = false;
                                        } else {
                                            log.info(bestRoute.nextHop() + " is not in " + subnetPrefix);
                                        }
                                    } else if (ipAddress.isIp6() && bestRoute.nextHop().isIp6()) {
                                        if (subnetPrefix.contains(bestRoute.nextHop())) {
                                            log.info(bestRoute.nextHop() + " is in " + subnetPrefix);
                                            addSrcPoint = false;
                                        } else {
                                            log.info(bestRoute.nextHop() + " is not in " + subnetPrefix);
                                        }
                                    }
                                }
                                if (addSrcPoint) {
                                    log.info("Add srcPoint: " + intf.connectPoint());
                                    ConnectPoint ingressPoint = intf.connectPoint();
                                    srcPoints.add(new FilteredConnectPoint(ingressPoint));
                                    numOdCP++;
                                }
                            } catch (Exception e) {
                                log.error("*****" + e.getMessage());
                            }

                            log.info("Number of ipAddresses: " + intf.ipAddressesList().size());

                        }

                        log.info("Number of srcPoints: " + srcPoints.size());
                        log.info("numOdCP: " + numOdCP);
                        Interface intf2 = interfaceService.getMatchingInterface(bestRoute.nextHop());
                        ConnectPoint egressPoint = intf2.connectPoint();
                        if (srcPoints.isEmpty() == false) {
                            log.info("Install MultiIntent!");
                            if (bestRoute.nextHop().isIp4()) {
                                TrafficSelector selector = DefaultTrafficSelector.builder()
                                        .matchEthType(Ethernet.TYPE_IPV4)
                                        .matchIPDst(bestRoute.prefix())
                                        .build();
                                installMultiIntent(srcPoints, egressPoint, selector, null);
                            } else if (bestRoute.nextHop().isIp6()) {
                                TrafficSelector selector = DefaultTrafficSelector.builder()
                                        .matchEthType(Ethernet.TYPE_IPV6)
                                        .matchIPv6Dst(bestRoute.prefix())
                                        .build();
                                installMultiIntent(srcPoints, egressPoint, selector, null);
                            }

                        } else {
                            log.info("No Install MultiIntent(empty srcPoints)");
                        }
                        log.info("-----------------");

                    } else {
                        log.info("No best route available.");
                    }

                }

            }
        }

    }

    // ===== Tool Functions =====
    protected void installIntent(ConnectPoint ingressPoint, ConnectPoint egressPoint, TrafficSelector selector,
            TrafficTreatment treatment, int priority) {

        Key intentKey = Key.of(
                String.join(":",
                        ingressPoint != null ? String.valueOf(ingressPoint.hashCode()) : "0",
                        egressPoint != null ? String.valueOf(egressPoint.hashCode()) : "0",
                        selector != null ? String.valueOf(selector.hashCode()) : "0",
                        treatment != null ? String.valueOf(treatment.hashCode()) : "0"),
                appId);
        Intent existIntent = intentService.getIntent(intentKey);
        if (existIntent == null) {
            PointToPointIntent.Builder intent = PointToPointIntent.builder()
                    .appId(appId)
                    .priority(priority)
                    .filteredIngressPoint(new FilteredConnectPoint(ingressPoint))
                    .filteredEgressPoint(new FilteredConnectPoint(egressPoint))
                    .key(intentKey)
                    .selector(selector);

            if (treatment != null)
                intent.treatment(treatment);

            intentService.submit(intent.build());
            log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                    ingressPoint.deviceId(), ingressPoint.port(),
                    egressPoint.deviceId(), egressPoint.port());
        } else {
            log.info("Intent `{}`, port `{}` => `{}`, port `{}` already exists.",
                    ingressPoint.deviceId(), ingressPoint.port(),
                    egressPoint.deviceId(), egressPoint.port());
        }
    }

    protected void withdrawPointToPointIntent() {
        for (Intent intent : intentService.getIntentsByAppId(appId)) {
            if (intent instanceof PointToPointIntent) {
                intentService.withdraw(intent);
            }
        }
    }

    protected void installMultiIntent(Set<FilteredConnectPoint> ingressPoints,
            ConnectPoint egressPoint,
            TrafficSelector selector,
            TrafficTreatment.Builder treatment) {
        Key intentKey = Key.of(
                String.join(":",
                        egressPoint != null ? String.valueOf(egressPoint.hashCode()) : "0",
                        selector != null ? String.valueOf(selector.hashCode()) : "0",
                        treatment != null ? String.valueOf(treatment.hashCode()) : "0"),
                appId);

        Intent existIntent = intentService.getIntent(intentKey);

        if (existIntent != null) {
            log.info("Intent `{}` => `{}` already exists.",
                    ingressPoints,
                    egressPoint);
            return;
        }

        MultiPointToSinglePointIntent.Builder intent = MultiPointToSinglePointIntent.builder()
                .appId(appId)
                .priority(15)
                .filteredIngressPoints(ingressPoints)
                .filteredEgressPoint(new FilteredConnectPoint(egressPoint))
                .key(intentKey)
                .selector(selector);

        if (treatment != null)
            intent.treatment(treatment.build());

        intentService.submit(intent.build());
        log.info("MultiIntent `{}` => `{}` is submitted.",
                ingressPoints,
                egressPoint);

    }

    protected void withdrawMultiPointToSinglePointIntent() {
        for (Intent intent : intentService.getIntentsByAppId(appId)) {
            if (intent instanceof MultiPointToSinglePointIntent) {
                intentService.withdraw(intent);
            }
        }
    }

    protected Host getHost(IpAddress ip) {
        Host returnHost = null;
        hostService.requestMac(ip);
        for (Host host : hostService.getHostsByIp(ip)) {
            returnHost = host;
        }
        return returnHost;
    }

    protected void floodNdp(Ip6Address targetIp) {
        NeighborSolicitation ns = new NeighborSolicitation()
                .setTargetAddress(targetIp.toOctets())
                .addOption(NeighborDiscoveryOptions.TYPE_SOURCE_LL_ADDRESS, gatewayMac.toBytes());

        Ethernet ethPkt = new Ethernet();
        ethPkt.setEtherType(Ethernet.TYPE_IPV6);
        ethPkt.setDestinationMACAddress(MacAddress.IPV6_MULTICAST);
        ethPkt.setSourceMACAddress(gatewayMac);
        ethPkt.setPayload(new IPv6()
                .setDestinationAddress(Ip6Address.valueOf("ff02::1").toOctets())
                .setSourceAddress(gatewayIp6.toOctets())
                .setNextHeader(IPv6.PROTOCOL_ICMP6)
                .setHopLimit((byte) 255)
                .setPayload(new ICMP6()
                        .setIcmpType(ICMP6.NEIGHBOR_SOLICITATION)
                        .setIcmpCode((byte) 0)
                        .setPayload(ns)));
        ByteBuffer bpacket = ByteBuffer.wrap(ethPkt.serialize());
        List<ConnectPoint> edgePoints = Lists.newArrayList(edgePortService.getEdgePoints());
        for (ConnectPoint point : edgePoints) {
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(point.port())
                    .setEthSrc(frrMac)
                    .build();
            OutboundPacket outpacket = new DefaultOutboundPacket(point.deviceId(),
                    treatment,
                    bpacket);
            packetService.emit(outpacket);
        }
    }

    protected void floodArp(Ip4Address targetIp) {
        ARP arpRequest = new ARP();
        arpRequest.setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REQUEST)
                .setSenderHardwareAddress(gatewayMac.toBytes())
                .setSenderProtocolAddress(gatewayIp4.toInt())
                .setTargetHardwareAddress(MacAddress.BROADCAST.toBytes())
                .setTargetProtocolAddress(targetIp.toInt());

        Ethernet ethPkt = new Ethernet();
        ethPkt.setEtherType(Ethernet.TYPE_ARP)
                .setSourceMACAddress(gatewayMac)
                .setDestinationMACAddress(MacAddress.BROADCAST)
                .setPayload(arpRequest);
        ByteBuffer bpacket = ByteBuffer.wrap(ethPkt.serialize());
        List<ConnectPoint> edgePoints = Lists.newArrayList(edgePortService.getEdgePoints());
        for (ConnectPoint point : edgePoints) {
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(point.port())
                    .setEthSrc(frrMac)
                    .build();
            OutboundPacket outpacket = new DefaultOutboundPacket(point.deviceId(),
                    treatment,
                    bpacket);
            packetService.emit(outpacket);
        }
    }

}
