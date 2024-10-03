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

import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import java.nio.ByteBuffer;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger("ProxyArp");

    /** Some configurable property. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    protected ProxyArpProcessor processor = new ProxyArpProcessor();
    private Map<Ip4Address, MacAddress> arpTable = new HashMap<>();
    private Map<DeviceId, Map<MacAddress, PortNumber>> bridgeTable = new HashMap<>();

    @Activate
    protected void activate() {
        // cfgService.registerProperties(getClass());

        // Register app
        coreService.registerApplication("nycu.winlab.ProxyArp");

        // Register packet processor
        packetService.addProcessor(processor, PacketProcessor.director(2));

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        // cfgService.unregisterProperties(getClass(), false);
        // Unregister packet processor
        packetService.removeProcessor(processor);

        log.info("Stopped");
    }

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

            // Learn the bridge table
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            MacAddress srcMac = ethPkt.getSourceMAC();
            PortNumber inPort = pkt.receivedFrom().port();
            bridgeTable.putIfAbsent(deviceId, new HashMap<>());
            bridgeTable.get(deviceId).put(srcMac, inPort);

            if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
                ARP arp = (ARP) ethPkt.getPayload();

                // Check if protocol type is IP
                if (arp.getProtocolType() != ARP.PROTO_TYPE_IP) {
                    return;
                }

                // Extract ARP request
                Ip4Address targetIp = Ip4Address.valueOf(arp.getTargetProtocolAddress());
                Ip4Address senderIp = Ip4Address.valueOf(arp.getSenderProtocolAddress());

                // Learn the ARP table
                arpTable.put(senderIp, srcMac);

                // Check if the target IP is in the ARP table
                if (arpTable.containsKey(targetIp)) {
                    MacAddress targetMac = arpTable.get(targetIp);
                    Ethernet ethReply = ARP.buildArpReply(targetIp, targetMac, ethPkt);
                    TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(inPort).build();
                    context.block();
                    packetService.emit(
                            new DefaultOutboundPacket(deviceId, treatment, ByteBuffer.wrap(ethReply.serialize())));
                    log.info("TABLE HIT. Requested MAC = {}", targetMac);
                } else {
                    flood(context);
                    log.info("TABLE MISS. Send request to edge ports");
                }

                if (arp.getOpCode() == ARP.OP_REPLY) {
                    log.info("RECV REPLY. Requested MAC = {}", srcMac);
                }
            }

        }

        private void flood(PacketContext context) {
            Ethernet ethPkt = context.inPacket().parsed();
            DeviceId deviceId = context.inPacket().receivedFrom().deviceId();
            TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.FLOOD).build();
            packetService
                    .emit(new DefaultOutboundPacket(deviceId, treatment, ByteBuffer.wrap(ethPkt.serialize())));
        }
    }

}
