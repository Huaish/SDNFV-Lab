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
package nycu.winlab.ProxyArp;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;

import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP6;
import org.onlab.packet.IPv6;
import org.onlab.packet.MacAddress;
import org.onlab.packet.ndp.NeighborAdvertisement;
import org.onlab.packet.ARP;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip6Address;
import org.onosproject.net.ConnectPoint;

import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;

import java.nio.ByteBuffer;

import org.onosproject.net.edge.EdgePortService;

/**
 * Proxy ARP component for ONOS.
 */

@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    private ProxyArpProcessor processor = new ProxyArpProcessor();
    private ApplicationId appId;
    private Map<Ip4Address, MacAddress> arpTable = new HashMap<>();
    private Map<Ip6Address, MacAddress> ndpTable = new HashMap<>();
    private Map<MacAddress, ConnectPoint> cpTable = new HashMap<>();
    private Ip4Prefix taDomainIp4 = Ip4Prefix.valueOf("192.168.70.0/24");

    @Activate
    protected void activate() {

        // register your app
        appId = coreService.registerApplication("nycu.winlab.ProxyArp");

        // add a packet processor to packetService
        packetService.addProcessor(processor, PacketProcessor.director(2));

        // install a flowrule for packet-in
        TrafficSelector.Builder arpselector = DefaultTrafficSelector.builder();
        arpselector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(arpselector.build(), PacketPriority.REACTIVE, appId);
        TrafficSelector.Builder ndpselector = DefaultTrafficSelector
                .builder()
                .matchEthType(Ethernet.TYPE_IPV6)
                .matchIPProtocol(IPv6.PROTOCOL_ICMP6)
                .matchIcmpv6Type(ICMP6.NEIGHBOR_SOLICITATION);
        packetService.requestPackets(ndpselector.build(), PacketPriority.CONTROL, appId);
        TrafficSelector.Builder buildNdpReplySelector = DefaultTrafficSelector
                .builder()
                .matchEthType(Ethernet.TYPE_IPV6)
                .matchIPProtocol(IPv6.PROTOCOL_ICMP6)
                .matchIcmpv6Type(ICMP6.NEIGHBOR_ADVERTISEMENT);
        packetService.requestPackets(buildNdpReplySelector.build(), PacketPriority.CONTROL, appId);

        log.info("Proxy ARP AppComponent started");
    }

    @Deactivate
    protected void deactivate() {
        // remove your packet processor
        packetService.removeProcessor(processor);
        processor = null;

        // remove flowrule you installed for packet-in
        TrafficSelector.Builder arpselector = DefaultTrafficSelector.builder();
        arpselector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(arpselector.build(), PacketPriority.REACTIVE, appId);
        TrafficSelector.Builder ndpselector = DefaultTrafficSelector
                .builder()
                .matchEthType(Ethernet.TYPE_IPV6)
                .matchIPProtocol(IPv6.PROTOCOL_ICMP6)
                .matchIcmpv6Type(ICMP6.NEIGHBOR_SOLICITATION);
        packetService.cancelPackets(ndpselector.build(), PacketPriority.CONTROL, appId);
        TrafficSelector.Builder buildNdpReplySelector = DefaultTrafficSelector
                .builder()
                .matchEthType(Ethernet.TYPE_IPV6)
                .matchIPProtocol(IPv6.PROTOCOL_ICMP6)
                .matchIcmpv6Type(ICMP6.NEIGHBOR_ADVERTISEMENT);
        packetService.cancelPackets(buildNdpReplySelector.build(), PacketPriority.CONTROL, appId);

        log.info("Proxy ARP AppComponent stopped");
    }

    /**
     * Custom packet processor that handles ARP requests and replies.
     */
    private class ProxyArpProcessor implements PacketProcessor {

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

            // Check if the packet is an ARP packet
            if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
                ARP arpPacket = (ARP) ethPkt.getPayload();

                // Get source and destination IP/MAC addresses
                Ip4Address srcIP = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());
                MacAddress srcMac = ethPkt.getSourceMAC();
                Ip4Address dstIP = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());
                MacAddress dstMac = ethPkt.getDestinationMAC();
                ConnectPoint inport = pkt.receivedFrom();

                if (taDomainIp4.contains(dstIP) &&
                        !dstIP.equals(Ip4Address.valueOf("192.168.70.80")) &&
                        !dstIP.equals(Ip4Address.valueOf("192.168.70.253"))) {
                    context.block();
                    return;
                }

                // Learn the source IP/MAC mapping and where the packet came from
                learnArpMapping(srcIP, srcMac, inport);

                // Check if we know the target MAC address
                MacAddress targetMac = arpTable.get(dstIP);
                ConnectPoint outport = cpTable.get(targetMac);

                // Handle ARP requests
                if (arpPacket.getOpCode() == ARP.OP_REQUEST) {
                    if (targetMac != null) {
                        // If target MAC is known, generate ARP reply and send it back
                        // log.info("TABLE HIT. Requested MAC = {}", targetMac.toString());
                        Ethernet ethArpReply = ARP.buildArpReply(dstIP, targetMac, ethPkt);
                        packetOut(ethArpReply, inport);
                    } else {
                        // If target MAC is unknown, flood the ARP request to all edge ports
                        // log.info("TABLE MISS. Send request to edge ports");
                        flood(ethPkt, inport);
                    }
                    // Handle ARP replies
                } else if (arpPacket.getOpCode() == ARP.OP_REPLY) {
                    // log.info("RECV ARP REPLY. Requested MAC = {}", srcMac.toString());
                    packetOut(ethPkt, outport); // Forward ARP reply to the request sender
                }
            } else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                IPv6 ipv6Packet = (IPv6) ethPkt.getPayload();
                ICMP6 ndpPkt = (ICMP6) ipv6Packet.getPayload();
                MacAddress srcMac = ethPkt.getSourceMAC();
                Ip6Address srcIP = Ip6Address.valueOf(ipv6Packet.getSourceAddress());
                Ip6Address dstIP = Ip6Address.valueOf(ipv6Packet.getDestinationAddress());

                learnNdpMapping(srcIP, srcMac, pkt.receivedFrom());

                MacAddress targetMac = ndpTable.get(dstIP);
                ConnectPoint outport = cpTable.get(targetMac);

                if (ndpPkt.getIcmpType() == ICMP6.NEIGHBOR_SOLICITATION) { // request
                    // log.info("RECV NDP. Requested MAC = {}", srcMac.toString());

                    if (targetMac != null) {
                        // log.info("TABLE HIT. Requested MAC = {}", targetMac.toString());
                        Ethernet ethNdpReply = NeighborAdvertisement.buildNdpAdv(dstIP, targetMac, ethPkt);
                        IPv6 ipv6 = (IPv6) ethNdpReply.getPayload();
                        ipv6.setHopLimit((byte) 255);
                        ethNdpReply.setPayload(ipv6);
                        packetOut(ethNdpReply, pkt.receivedFrom());
                    } else {
                        // log.info("TABLE MISS. Send request to edge ports");
                        flood(ethPkt, pkt.receivedFrom());
                    }

                } else if (ndpPkt.getIcmpType() == ICMP6.NEIGHBOR_ADVERTISEMENT) { // reply
                    // log.info("RECV NDP REPLY. Requested MAC = {}", srcMac.toString());
                    packetOut(ethPkt, outport);
                }

                // Get source and destination IP/MAC addresses
            }
        }
    }

    /**
     * Learn the mapping between an IP address and its corresponding MAC address,
     * and map the MAC address to the ConnectPoint where the packet was received.
     */
    private void learnArpMapping(Ip4Address srcIP, MacAddress srcMac, ConnectPoint inport) {
        arpTable.putIfAbsent(srcIP, srcMac);
        cpTable.putIfAbsent(srcMac, inport);
    }

    private void learnNdpMapping(Ip6Address srcIP, MacAddress srcMac, ConnectPoint inport) {
        ndpTable.putIfAbsent(srcIP, srcMac);
        cpTable.putIfAbsent(srcMac, inport);
    }

    /**
     * Flood the ARP request to all edge ports except the one it came from.
     */
    private void flood(Ethernet ethPkt, ConnectPoint inport) {
        for (ConnectPoint cp : edgePortService.getEdgePoints()) {
            if (!cp.equals(inport)) {
                packetOut(ethPkt, cp);
            }
        }
    }

    /**
     * Send a packet out to a specific ConnectPoint.
     */
    private void packetOut(Ethernet ethPkt, ConnectPoint cp) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(cp.port())
                .build();
        OutboundPacket outPacket = new DefaultOutboundPacket(
                cp.deviceId(), treatment, ByteBuffer.wrap(ethPkt.serialize()));
        packetService.emit(outPacket);
    }
}