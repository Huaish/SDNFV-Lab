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
	@echo "Creating R1h1OVS Bridge..."
	sudo ovs-vsctl add-br R1h1OVS
	@echo "Configuring ONOS and R1h1OVS..."
	sudo ovs-vsctl set-controller R1h1OVS tcp:127.0.0.1:6653
	sudo ovs-vsctl set bridge R1h1OVS protocols=OpenFlow14
	@echo "Setting up R1h1OVS Bridge..."
	sudo ip link set R1h1OVS up

	@echo "Creating R2h2OVS Bridge..."
	sudo ovs-vsctl add-br R2h2OVS
	@echo "Configuring ONOS and R2h2OVS..."
	sudo ovs-vsctl set-controller R2h2OVS tcp:127.0.0.1:6653
	sudo ovs-vsctl set bridge R2h2OVS protocols=OpenFlow14
	@echo "Setting up R2h2OVS Bridge..."
	sudo ip link set R2h2OVS up

# Create links between containers and OVS (Use ovs-docker)
link:
	@echo "Setting up links...( R1 <-> R1h1OVS )"
	sudo ovs-docker add-port R1h1OVS veth-R1 R1 --ipaddress=172.18.0.2/16

	@echo "Setting up links...( h1 <-> R1h1OVS )"
	sudo ovs-docker add-port R1h1OVS veth-h1 h1 --ipaddress=172.18.0.3/16

	@echo "Setting up links...( R2 <-> R2h2OVS )"
	sudo ovs-docker add-port R2h2OVS veth-R2 R2 --ipaddress=172.19.0.2/16

	@echo "Setting up links...( h2 <-> R2h2OVS )"
	sudo ovs-docker add-port R2h2OVS veth-h2 h2 --ipaddress=172.19.0.3/16

	@echo "Setting up default routes..."
	docker exec h1 ip route add default via 172.18.0.2
	docker exec h2 ip route add default via 172.19.0.2

onos-cli:
	@echo "Open ONOS CLI..."
	ssh -o "StrictHostKeyChecking=no" -o "GlobalKnownHostsFile=/dev/null" -o "UserKnownHostsFile=/dev/null" onos@localhost -p 8101

clean:
	@echo "Stopping and removing containers..."
	docker-compose down

	@echo "Cleaning up veth pairs...(create by ovs-docker)"
	sudo ovs-docker del-ports OVS veth-R1 || true
	sudo ovs-docker del-ports OVS veth-h1 || true
	sudo ovs-docker del-ports OVS veth-R2 || true
	sudo ovs-docker del-ports OVS veth-h2 || true

	@echo "Removing OVS Bridge..."
	sudo ovs-vsctl del-br R1h1OVS || true
	sudo ovs-vsctl del-br R2h2OVS || true