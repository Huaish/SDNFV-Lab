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
package nycu.winlab.bridge;

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
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
// import org.onosproject.net.flowobjective.DefaultForwardingObjective;
// import org.onosproject.net.flowobjective.FlowObjectiveService;
// import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;

import org.onosproject.net.PortNumber;
import org.onosproject.net.DeviceId;

import org.onosproject.net.flow.FlowRuleService;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger("LearningBridge");

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    // @Reference(cardinality = ReferenceCardinality.MANDATORY)
    // protected FlowObjectiveService flowObjectiveService;

    private LearningBridgeProcessor processor = new LearningBridgeProcessor();
    private ApplicationId appId;
    private Map<DeviceId, Map<MacAddress, PortNumber>> bridgeTable = new HashMap<>();

    @Activate
    protected void activate() {
        // cfgService.registerProperties(getClass());

        // Register app
        appId = coreService.registerApplication("nycu.winlab.bridge");

        // Add a packet processor to packetService
        packetService.addProcessor(processor, PacketProcessor.director(2));

        // Install a flow rule for packet-in
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selectorBuilder.build(), PacketPriority.REACTIVE, appId);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        // cfgService.unregisterProperties(getClass(), false);

        // remove flowrule installed by your app
        flowRuleService.removeFlowRulesById(appId);

        // remove your packet processor
        packetService.removeProcessor(processor);
        processor = null;

        // remove flowrule you installed for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        log.info("Stopped");
    }

    private class LearningBridgeProcessor implements PacketProcessor {

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
            PortNumber recPort = pkt.receivedFrom().port();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            // rec packet-in from new device, create new table for it
            if (bridgeTable.get(recDevId) == null) {
                bridgeTable.put(recDevId, new HashMap<>());
            }

            // TODO1: implement the learning bridge algorithm
            if (bridgeTable.get(recDevId).get(srcMac) == null) {

                // the mapping of pkt's src mac and receivedfrom port wasn't store in the table
                // of the rec device
                bridgeTable.get(recDevId).put(srcMac, recPort);
                log.info("Add an entry to the port table of `{}`. MAC address: `{}` => Port: `{}`",
                        recDevId, srcMac, recPort);

            }

            // miss or FLOOD
            if (bridgeTable.get(recDevId).get(dstMac) == null) {
                // the mapping of dst mac and forwarding port wasn't store in the table of the
                // rec device
                flood(context);
                log.info("MAC address `{}` is missed on `{}`. Flood the packet.", dstMac, recDevId);

            } else if (bridgeTable.get(recDevId).get(dstMac) != null) {
                // there is a entry store the mapping of dst mac and forwarding port
                installRule(context, bridgeTable.get(recDevId).get(dstMac));
                log.info("MAC address `{}` is matched on `{}`. Install a flow rule.", dstMac, recDevId);
            }
        }
    }

    // TODO2: Floods the packet out to all ports except the incoming port
    private void flood(PacketContext context) {
        packetOut(context, PortNumber.FLOOD);
    }

    // TODO3: Sends a packet out to the specified port
    private void packetOut(PacketContext context, PortNumber port) {
        context.treatmentBuilder().setOutput(port);
        context.send();
    }

    // TODO4: Install a rule forwarding the packet to the specified port
    // (Use flow package)
    private void installRule(PacketContext context, PortNumber port) {
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

        selectorBuilder.matchEthDst(inPkt.getDestinationMAC()).matchEthSrc(inPkt.getSourceMAC());

        TrafficTreatment treatment = context.treatmentBuilder().setOutput(port).build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .withSelector(selectorBuilder.build()) // Traffic selector
                .withTreatment(treatment) // Action to perform
                .withPriority(30) // Set a priority for the rule
                .makeTemporary(30) // Timeout for the rule
                .forDevice(context.inPacket().receivedFrom().deviceId()) // Device where the
                // rule applies
                .fromApp(appId) // Application ID
                .build();

        flowRuleService.applyFlowRules(flowRule);

        context.send();
    }

    // (Use flowobjective package)
    // private void installRule(PacketContext context, PortNumber port) {
    // Ethernet inPkt = context.inPacket().parsed();
    // TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

    // selectorBuilder.matchEthDst(inPkt.getDestinationMAC()).matchEthSrc(inPkt.getSourceMAC());

    // TrafficTreatment treatment =
    // context.treatmentBuilder().setOutput(port).build();

    // ForwardingObjective forwardingObjective =
    // DefaultForwardingObjective.builder()
    // .withSelector(selectorBuilder.build()) // Traffic selector
    // .withTreatment(treatment) // Action to perform
    // .withPriority(30) // Set a priority for the rule
    // .makeTemporary(30) // Timeout for the rule
    // .withFlag(ForwardingObjective.Flag.VERSATILE) // Flag for the rule
    // .fromApp(appId) // Application ID
    // .add();

    // flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(),
    // forwardingObjective);

    // context.send();
    // }
}
