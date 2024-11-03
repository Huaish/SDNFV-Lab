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
package nycu.winlab.groupmeter;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
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
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.group.DefaultGroupBucket;
import org.onosproject.net.group.DefaultGroupDescription;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.meter.Band;
import org.onosproject.net.meter.DefaultBand;
import org.onosproject.net.meter.DefaultMeterRequest;
import org.onosproject.net.meter.Meter;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterRequest;
import org.onosproject.net.meter.MeterService;
import org.onosproject.net.packet.InboundPacket;
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

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final HostConfigListener cfgListener = new HostConfigListener();

    private final ConfigFactory<ApplicationId, NameConfig> factory = new ConfigFactory<ApplicationId, NameConfig>(
            APP_SUBJECT_FACTORY, NameConfig.class, "informations") {
        @Override
        public NameConfig createConfig() {
            return new NameConfig();
        }
    };

    private ApplicationId appId;
    private PacketIntentProcessor processor = new PacketIntentProcessor();
    NameConfig config = null;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MeterService meterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.winlab.groupmeter");

        // Register the packet processor
        packetService.addProcessor(processor, PacketProcessor.director(2));

        // Request packets
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selectorBuilder.build(), PacketPriority.REACTIVE, appId);

        // Register the configuration listener and factory
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);
    }

    @Deactivate
    protected void deactivate() {
        // Unregister the packet processor
        packetService.removeProcessor(processor);
        processor = null;

        // Cancel the packet request
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        // Unregister the configuration listener and factory
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);

        // Remove all flows installed by this application
        flowRuleService.removeFlowRulesById(appId);

        // Remove any groups installed by this application on s1
        removeGroupEntry();

        // Remove any meters installed by this application
        removeMeterEntry();

        // Cancel all intents submitted by this application
        intentService.getIntents().forEach(
                intent -> {
                    if (intent.appId().equals(appId)) {
                        intentService.withdraw(intent);
                    }
                });
    }

    private class PacketIntentProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled() || config == null) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            DeviceId deviceId = pkt.receivedFrom().deviceId();
            MacAddress srcMac = ethPkt.getSourceMAC();
            PortNumber inPort = pkt.receivedFrom().port();

            // Repky the first arp packet
            if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
                ARP arp = (ARP) ethPkt.getPayload();

                if (arp == null || arp.getProtocolType() != ARP.PROTO_TYPE_IP) {
                    return;
                }

                if (arp.getOpCode() == ARP.OP_REQUEST) {
                    Ip4Address targetIp = Ip4Address.valueOf(arp.getTargetProtocolAddress());
                    if (targetIp.equals(Ip4Address.valueOf(config.getIp1()))) {
                        MacAddress targetMac = MacAddress.valueOf(config.getMac1());
                        Ethernet ethReply = ARP.buildArpReply(targetIp, targetMac, ethPkt);
                        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                .setOutput(inPort).build();
                        context.block();
                        packetService.emit(
                                new DefaultOutboundPacket(deviceId, treatment, ByteBuffer.wrap(ethReply.serialize())));
                    } else if (targetIp.equals(Ip4Address.valueOf(config.getIp2()))) {
                        MacAddress targetMac = MacAddress.valueOf(config.getMac2());
                        Ethernet ethReply = ARP.buildArpReply(targetIp, targetMac, ethPkt);
                        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                .setOutput(inPort).build();
                        context.block();
                        packetService.emit(
                                new DefaultOutboundPacket(deviceId, treatment, ByteBuffer.wrap(ethReply.serialize())));
                    }
                }

            } else {
                // Flow Ruls Installation with IntentService
                if (srcMac.equals(MacAddress.valueOf(config.getMac1()))) {
                    // Define ingress and egress ConnectPoint for s2/s5 to h2
                    ConnectPoint ingressPoint = pkt.receivedFrom();
                    ConnectPoint egressPoint = ConnectPoint.deviceConnectPoint(config.getHost2());
                    TrafficSelector trafficSelector = DefaultTrafficSelector.builder()
                            .matchEthDst(MacAddress.valueOf(config.getMac2()))
                            .build();
                    PointToPointIntent intent = PointToPointIntent.builder()
                            .appId(appId)
                            .filteredIngressPoint(new FilteredConnectPoint(ingressPoint))
                            .filteredEgressPoint(new FilteredConnectPoint(egressPoint))
                            .selector(trafficSelector)
                            .build();
                    intentService.submit(intent);

                    log.info(
                            "Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                            ingressPoint.deviceId(), ingressPoint.port(), egressPoint.deviceId(), egressPoint.port());
                } else if (srcMac.equals(MacAddress.valueOf(config.getMac2()))) {
                    // Define ingress and egress ConnectPoint for h2 to h1
                    ConnectPoint ingressPoint = pkt.receivedFrom();
                    ConnectPoint egressPoint = ConnectPoint.deviceConnectPoint(config.getHost1());
                    TrafficSelector trafficSelector = DefaultTrafficSelector.builder()
                            .matchEthDst(MacAddress.valueOf(config.getMac1()))
                            .build();
                    PointToPointIntent intent = PointToPointIntent.builder()
                            .appId(appId)
                            .filteredIngressPoint(new FilteredConnectPoint(ingressPoint))
                            .filteredEgressPoint(new FilteredConnectPoint(egressPoint))
                            .selector(trafficSelector)
                            .build();
                    intentService.submit(intent);

                    log.info(
                            "Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                            ingressPoint.deviceId(), ingressPoint.port(), egressPoint.deviceId(), egressPoint.port());
                }

            }
        }
    }

    private class HostConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED) &&
                    event.configClass().equals(NameConfig.class)) {

                NameConfig newconfig = cfgService.getConfig(appId, NameConfig.class);

                if (newconfig != null) {
                    // config != null, remove all flows installed by this application
                    if (config != null) {
                        flowRuleService.removeFlowRulesById(appId);
                        removeGroupEntry();
                        removeMeterEntry();
                    }

                    // Update the configuration
                    config = newconfig;

                    // Log
                    log.info("ConnectPoint_h1: {}, ConnectPoint_h2: {}", config.getHost1(), config.getHost2());
                    log.info("MacAddress_h1: {}, MacAddress_h2: {}", config.getMac1(), config.getMac2());
                    log.info("IpAddress_h1: {}, IpAddress_h2: {}", config.getIp1(), config.getIp2());

                    // ========================= Install Group Entry on s1 =========================
                    DeviceId deviceId1 = DeviceId.deviceId("of:0000000000000001");
                    createFailoverGroupEntry(deviceId1);

                    // Install Flow Rule on s1
                    GroupId groupId = groupService.getGroup(deviceId1, null).id();
                    TrafficSelector trafficSelector1 = DefaultTrafficSelector.builder()
                            .matchInPort(PortNumber.portNumber(1))
                            .matchEthType(Ethernet.TYPE_IPV4)
                            .build();
                    TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                            .group(groupId).build();
                    installRule(deviceId1, trafficSelector1, treatment);

                    // ========================= Install Meter Entry on s4 =========================
                    DeviceId deviceId4 = DeviceId.deviceId("of:0000000000000004");
                    MeterId meterId = createMeterEntry(deviceId4);

                    // Install Flow Rule on s4
                    TrafficSelector trafficSelector4 = DefaultTrafficSelector.builder()
                            .matchEthSrc(MacAddress.valueOf(config.getMac1()))
                            .build();
                    TrafficTreatment treatment4 = DefaultTrafficTreatment.builder()
                            .setOutput(PortNumber.portNumber(2))
                            .meter(meterId)
                            .build();

                    installRule(deviceId4, trafficSelector4, treatment4);
                }
            }
        }
    }

    private void createFailoverGroupEntry(DeviceId deviceId) {
        // Define two buckets
        PortNumber port2 = PortNumber.portNumber(2);
        TrafficTreatment treatment1 = DefaultTrafficTreatment.builder().setOutput(port2).build();
        GroupBucket bucket1 = DefaultGroupBucket.createFailoverGroupBucket(treatment1, port2, GroupId.valueOf(0));

        PortNumber port3 = PortNumber.portNumber(3);
        TrafficTreatment treatment2 = DefaultTrafficTreatment.builder().setOutput(port3).build();
        GroupBucket bucket2 = DefaultGroupBucket.createFailoverGroupBucket(treatment2, port3, GroupId.valueOf(0));

        GroupBuckets buckets = new GroupBuckets(Arrays.asList(bucket1, bucket2));

        // Create the failover group description
        GroupDescription groupDescription = new DefaultGroupDescription(deviceId, GroupDescription.Type.FAILOVER,
                buckets, null, Integer.valueOf(1), appId);

        groupService.addGroup(groupDescription);
    }

    private MeterId createMeterEntry(DeviceId deviceId) {
        Band band = DefaultBand.builder()
                .ofType(Band.Type.DROP)
                .burstSize(1024)
                .withRate(512)
                .build();

        MeterRequest meterRequest = DefaultMeterRequest.builder()
                .fromApp(appId)
                .forDevice(deviceId)
                .burst()
                .withUnit(Meter.Unit.KB_PER_SEC)
                .withBands(Arrays.asList(band))
                .add();

        Meter meter = meterService.submit(meterRequest);
        return (MeterId) meter.meterCellId();
    }

    private void installRule(DeviceId deviceId, TrafficSelector trafficSelector, TrafficTreatment treatment) {
        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId) // Application ID
                .forDevice(deviceId) // Device ID
                .withSelector(trafficSelector) // Traffic selector
                .withTreatment(treatment) // Action to perform
                .makePermanent() // Permanent rule
                .withPriority(10) // Priority level
                .build();

        flowRuleService.applyFlowRules(flowRule);
    }

    private void removeGroupEntry() {
        DeviceId deviceId = DeviceId.deviceId("of:0000000000000001");
        groupService.getGroups(deviceId, appId).forEach(group -> {
            groupService.removeGroup(deviceId, group.appCookie(), appId);
        });
    }

    private void removeMeterEntry() {
        meterService.getAllMeters().forEach(meter -> {
            if (meter.appId().equals(appId)) {
                MeterRequest meterRequest = DefaultMeterRequest.builder()
                        .fromApp(appId)
                        .forDevice(meter.deviceId())
                        .remove();
                meterService.withdraw(meterRequest, meter.meterCellId());
            }
        });
    }
}