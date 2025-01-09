# Description: Makefile for lab5
.PHONY: all clean container ovs link ip-link onos-cli

# Default target
# all: container ovs link

deploy: container ovs link
	sleep 3

	mvn clean install -f ./vrouter/pom.xml -DskipTests
	mvn clean install -f ./ProxyArp/pom.xml -DskipTests

	sleep 5

	onos-netcfg localhost ./cfg.json
	
	sleep 1

	onos-app localhost install! ./ProxyArp/target/ProxyArp-1.0-SNAPSHOT.oar
	onos-app localhost install! ./vrouter/target/vrouter-1.0-SNAPSHOT.oar


# Build containers
container:
	@echo "Building containers..."
	docker-compose up -d

# Create OVS Bridge
ovs:
	@echo "Creating OVS1 Bridge..."
	sudo ovs-vsctl add-br OVS1
	@echo "Configuring ONOS and OVS1..."
	sudo ovs-vsctl set-controller OVS1 tcp:127.0.0.1:6653
	sudo ovs-vsctl set bridge OVS1 protocols=OpenFlow14 other-config:datapath-id=0000000000000001
	@echo "Setting up OVS Bridge..."
	sudo ip link set OVS1 up

	@echo "Creating OVS2 Bridge..."
	sudo ovs-vsctl add-br OVS2
	@echo "Configuring ONOS and OVS2..."
	sudo ovs-vsctl set-controller OVS2 tcp:127.0.0.1:6653
	sudo ovs-vsctl set bridge OVS2 protocols=OpenFlow14 other-config:datapath-id=0000000000000002
	@echo "Setting up OVS Bridge..."
	sudo ip link set OVS2 up

# Create links between containers and OVS (Use ip link)
link:
	@echo "Setting up links...( Host1 <-> OVS2 )"
	sudo ip link add vetha-ovs2 type veth peer name vetha-host1
	sudo ovs-vsctl add-port OVS2 vetha-ovs2
	sudo ip link set vetha-ovs2 up
	sudo ip link set vetha-host1 netns $(shell docker inspect -f '{{.State.Pid}}' h1)
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' h1) -n ip link set vetha-host1 up
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' h1) -n ip addr add 172.16.55.2/24 dev vetha-host1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' h1) -n ip -6 addr add 2a0b:4e07:c4:55::2/64 dev vetha-host1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' h1) -n ip route add default via 172.16.55.1 dev vetha-host1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' h1) -n ip -6 route add default via 2a0b:4e07:c4:55::1 dev vetha-host1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' h1) -n ip addr add 192.168.63.4/24 dev vetha-host1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' h1) -n ip -6 addr add fd63::4/64 dev vetha-host1
# sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' h1) -n ip addr add 192.168.100.4/24 dev vetha-host1
	
	@echo "Setting up links...( OVS1 <-> OVS2 )"
	sudo ip link add vethb-ovs1 type veth peer name vethb-ovs2
	sudo ovs-vsctl add-port OVS1 vethb-ovs1 
	sudo ovs-vsctl add-port OVS2 vethb-ovs2
	sudo ip link set vethb-ovs1 up
	sudo ip link set vethb-ovs2 up

	@echo "Setting up links...( OVS1 <-> R1 )"
	sudo ip link add vethc-ovs1 type veth peer name vethc-r1
	sudo ovs-vsctl add-port OVS1 vethc-ovs1
	sudo ip link set vethc-ovs1 up
	sudo ip link set vethc-r1 address 00:00:00:00:01:01 netns $(shell docker inspect -f '{{.State.Pid}}' R1)
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip link set vethc-r1 up
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip addr add 192.168.63.1/24 dev vethc-r1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip addr add 192.168.70.55/24 dev vethc-r1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip addr add 172.16.55.69/24 dev vethc-r1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip addr add 192.168.100.3/24 dev vethc-r1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip addr add 192.168.64.1/24 dev vethc-r1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip -6 addr add fd63::1/64 dev vethc-r1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip -6 addr add fd70::55/64 dev vethc-r1
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip -6 addr add 2a0b:4e07:c4:55::69/64 dev vethc-r1

	@echo "Setting up links...( OVS1 <-> R2 )"
	sudo ip link add vethd-ovs1 type veth peer name vethd-r2
	sudo ovs-vsctl add-port OVS1 vethd-ovs1
	sudo ip link set vethd-ovs1 up
	sudo ip link set vethd-r2 address 00:00:00:00:01:02 netns $(shell docker inspect -f '{{.State.Pid}}' R2)
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R2) -n ip link set vethd-r2 up
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R2) -n ip addr add 192.168.63.2/24 dev vethd-r2
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R2) -n ip -6 addr add fd63::2/64 dev vethd-r2
	
	@echo "Setting up links...( OVS1 <-> ONOS )"
	sudo ip link add vethe-ovs1 type veth peer name vethe-onos
	sudo ovs-vsctl add-port OVS1 vethe-ovs1
	sudo ip link set vethe-ovs1 up
	sudo ip link set vethe-onos netns $(shell docker inspect -f '{{.State.Pid}}' onos)
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' onos) -n ip link set vethe-onos up
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' onos) -n ip addr add 192.168.100.1/24 dev vethe-onos
	
	@echo "Setting up links...( OVS2 <-> OVS3 )"
	sudo ovs-vsctl add-port OVS2 TO_TA_VXLAN -- set interface TO_TA_VXLAN type=vxlan options:remote_ip=192.168.60.55
	sudo ovs-vsctl set int TO_TA_VXLAN options:mtu_request=1370
	sudo ip l set mtu 1370 vxlan_sys_4789

# @echo "Setting up links...( OVS2 <-> OVS4 )"
# sudo ovs-vsctl add-port OVS2 TO_PEER_VXLAN -- set interface TO_PEER_VXLAN type=vxlan options:remote_ip=192.168.61.56
# sudo ovs-vsctl set int TO_PEER_VXLAN options:mtu_request=1370
# sudo ip l set mtu 1370 vxlan_sys_4789

build:
	mvn clean install -f ./vrouter/pom.xml -DskipTests
	mvn clean install -f ./ProxyArp/pom.xml -DskipTests


install:
	onos-app localhost install! ./ProxyArp/target/ProxyArp-1.0-SNAPSHOT.oar
	onos-app localhost install! ./vrouter/target/vrouter-1.0-SNAPSHOT.oar

uninstall:
	onos-app localhost deactivate nycu.winlab.ProxyArp
	onos-app localhost uninstall nycu.winlab.ProxyArp

upload: 
	onos-netcfg localhost ./cfg.json

onos-cli:
	@echo "Open ONOS CLI..."
	ssh -o "StrictHostKeyChecking=no" -o "GlobalKnownHostsFile=/dev/null" -o "UserKnownHostsFile=/dev/null" onos@localhost -p 8101

wg:
	sudo wg-quick up wg0

clean:
	@echo "Stopping and removing containers..."
	docker-compose down

	@echo "Cleaning up veth pairs...(create by ip link)"
	sudo ip link delete vetha-ovs2 || true
	sudo ip link delete vethb-ovs1 || true
	sudo ip link delete vethc-ovs1 || true
	sudo ip link delete vethd-ovs1 || true
	sudo ip link delete vethe-ovs1 || true
	sudo ip link delete vethf-ovs2 || true
	sudo ip link delete vethg-ovs2 || true

	@echo "Removing OVS Bridge..."
	sudo ovs-vsctl del-br OVS1 || true
	sudo ovs-vsctl del-br OVS2 || true