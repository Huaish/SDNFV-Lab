# Software Defined Networks and Network Function Virtualization

> NYCU 2024 Fall SDN Lab  
> [113 Autumn] 535606 Software Defined Networks and Network Function Virtualization  
> Instructor: 曾建超

<!-- - [Lab1: ONOS and Mininet Installation](lab1/README.md)
- [OpenFlow Protocol Observation and Flow Rule Installation](lab2/README.md)
- [ONOS Application Development: SDN-enabled Learning Bridge and Proxy ARP](lab3/README.md)
- [Group / Meter / Intent](lab4/README.md) -->

## [Lab1: ONOS and Mininet Installation](lab1/2024-LAB1.pdf)

**Report**

- [report](lab1/README.md)

**Code**

- [part2](lab1/lab1_part2_313551097.py)
- [part3](lab1/lab1_part3_313551097.py)

---

## [Lab2: OpenFlow Protocol Observation and Flow Rule Installation](lab2/2024-Lab2.pdf)

**Report**

- [report](lab2/README.md)

**Code**

- [part2](lab2/part2)
- [part3](lab2/part3)

**Demo**

- [Lab2_Demo.pdf](lab2/demo/Lab2_Demo.pdf)
- [code](lab2/demo)

---

## [Lab3: ONOS APP: SDN-enabled Learning Bridge and Proxy ARP](lab3/2024_SDNFV_LAB3.pdf)

**Report**

- [report](lab3/README.md)

**Code**

- [bridge-app](lab3/bridge-app/src/main/java/nycu/winlab/bridge/AppComponent.java)
- [ProxyArp](lab3/ProxyArp/src/main/java/nycu/winlab/ProxyArp/AppComponent.java)

**Demo**

- [Lab 3 questions.pdf](<lab3/demo/Lab 3 questions.pdf>)

---

## [Lab4: Group / Meter / Intent](lab4/2024-Lab4.pdf)

**Report**

- [report](lab4/README.md)

**Code**

- [groupmeter](lab4/groupmeter/src/main/java/nycu/winlab/groupmeter/AppComponent.java)
- [hostconfig.json](lab4/hostconfig.json)
- [ring_topo.py](lab4/ring_topo.py)

**Demo**

- [Lab 4 Demo Questions.pdf](<lab4/demo/Lab 4 Demo Questions.pdf>)
- [no-pack-in](lab4/demo/groupmeter/src/main/java/nycu/winlab/groupmeter/AppComponent.java)
- [hostconfig.json](lab4/demo/hostconfig.json)
- [ring_topo.py](lab4/demo/ring_topo.py)

---

## [Lab5: Network Function Virtualization: Software Router and Containerization](lab5/2024_SDNFV_Lab5.pdf)

**Code**

- [frr config](lab5/config)
- [docker-compose](lab5/docker-compose.yml)

**Demo**

- [frr config](lab5/demo/config)
- [docker-compose](lab5/demo/docker-compose.yml)

---

## [Final Project: SDN Network as Virtual Router](final/SDNNFVProject.pdf)

**Code**

- [ProxyArp](final/ProxyArp/src/main/java/nycu/winlab/ProxyArp/AppComponent.java)
  > There is still a bug in the code, the ARP reply packet is not sent out correctly.
- [vrouter](final/vrouter/src/main/java/nycu/winlab/vrouter/AppComponent.java)
- [frr config](final/config)
- [docker-compose](final/docker-compose.yml)
- [makefile](final/makefile)
- [cfg.json](final/cfg.json)

**How to use**

1. Run the following command to clean up any existing configurations and build the necessary components:

   ```bash
   make clean && make
   ```

   This command will:

   - Stop and remove any running containers.
   - Build and start new Docker containers using docker-compose.
   - Create and configure OVS (Open vSwitch) bridges.
   - Establish links between containers and OVS bridges.
     > The links to peer networks are commented out in the makefile, since there are some issues with `vrouter` app. If you want to test the peer network, you can uncomment the corresponding lines in the makefile. However, the `vrouter` may install too many flow rules and cause the OVS to crash.
   - Deploy the Proxy ARP and vRouter ONOS applications.

2. Use the ONOS CLI by running:

   ```bash
   make onos-cli
   ```

   This will open an SSH session to the ONOS CLI, where you can check network configurations and application status.

3. Use the upload target to upload updated configurations:

   ```bash
   make upload
   ```

4. To uninstall ONOS applications, run:
   ```bash
    make uninstall
   ```
