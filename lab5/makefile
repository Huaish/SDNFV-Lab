# Description: Makefile for lab5
.PHONY: all clean container ovs link ip-link onos-cli

# Default target
all: container ovs link

# Build containers
container:
	@echo "Building containers..."
	docker-compose up -d

# Create OVS Bridge
ovs:
	@echo "Creating OVS Bridge..."
	sudo ovs-vsctl add-br OVS
	@echo "Configuring ONOS and OVS..."
	sudo ovs-vsctl set-controller OVS tcp:127.0.0.1:6653
	sudo ovs-vsctl set bridge OVS protocols=OpenFlow14
	@echo "Setting up OVS Bridge..."
	sudo ip link set OVS up

# Create links between containers and OVS (Use ovs-docker)
link:
	@echo "Setting up links...( R1 <-> OVS )"
	sudo ovs-docker add-port OVS veth-R1 R1 --ipaddress=172.20.0.2/16
	
	@echo "Setting up links...( R3 <-> OVS )"
	sudo ovs-docker add-port OVS veth-R3 R3 --ipaddress=172.20.0.3/16

	@echo "Setting up links...( R4 <-> OVS )"
	sudo ovs-docker add-port OVS veth-R4 R4 --ipaddress=172.20.0.4/16

# Create links between containers and OVS (Use ip link)
ip-link:
	@echo "Setting up links...( R1 <-> OVS )"
	sudo ip link add veth-R1 type veth peer name veth-R1-c
	sudo ovs-vsctl add-port OVS veth-R1 
	sudo ip link set veth-R1-c netns $(shell docker inspect -f '{{.State.Pid}}' R1)
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip link set veth-R1-c up
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R1) -n ip addr add 172.20.0.2/16 dev veth-R1-c
	sudo ip link set veth-R1 up

	@echo "Setting up links...( R3 <-> OVS )"
	sudo ip link add veth-R3 type veth peer name veth-R3-c
	sudo ovs-vsctl add-port OVS veth-R3
	sudo ip link set veth-R3-c netns $(shell docker inspect -f '{{.State.Pid}}' R3)
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R3) -n ip link set veth-R3-c up
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R3) -n ip addr add 172.20.0.3/16 dev veth-R3-c
	sudo ip link set veth-R3 up


	@echo "Setting up links...( R4 <-> OVS )"
	sudo ip link add veth-R4 type veth peer name veth-R4-c
	sudo ovs-vsctl add-port OVS veth-R4
	sudo ip link set veth-R4-c netns $(shell docker inspect -f '{{.State.Pid}}' R4)
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R4) -n ip link set veth-R4-c up
	sudo nsenter -t $(shell docker inspect -f '{{.State.Pid}}' R4) -n ip addr add 172.20.0.4/16 dev veth-R4-c
	sudo ip link set veth-R4 up


onos-cli:
	@echo "Open ONOS CLI..."
	ssh -o "StrictHostKeyChecking=no" -o "GlobalKnownHostsFile=/dev/null" -o "UserKnownHostsFile=/dev/null" onos@localhost -p 8101

clean:
	@echo "Stopping and removing containers..."
	docker-compose down

	@echo "Cleaning up veth pairs...(create by ovs-docker)"
	sudo ovs-docker del-ports OVS veth-R1 || true
	sudo ovs-docker del-ports OVS veth-R3 || true
	sudo ovs-docker del-ports OVS veth-R4 || true

	@echo "Cleaning up veth pairs...(create by ip link)"
	sudo ip link delete veth-R1 || true
	sudo ip link delete veth-R3 || true
	sudo ip link delete veth-R4 || true

	@echo "Removing OVS Bridge..."
	sudo ovs-vsctl del-br OVS || true