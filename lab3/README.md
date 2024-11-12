# Lab 3: Developing ONOS Applications - Learning Bridge and Proxy ARP

## Introduction

In this lab we create two ONOS applications for SDN environments: a **Learning Bridge Application** and a **Proxy ARP Application**. The goal is to improve packet handling by implementing a learning bridge mechanism and managing ARP requests through proxy handling.

## Learning Bridge Application

The Learning Bridge Application enables packet forwarding by dynamically learning and storing MAC-to-port mappings in a MAC address table.

### Features
- **MAC Address Table**: Maintains mappings of MAC addresses to corresponding ports, facilitating packet forwarding.
- **Packet Forwarding Process**:
  - When a packet arrives, the application:
    1. Logs the source MAC and port information in the MAC address table.
    2. Checks the destination MAC:
       - **If found**: Forwards the packet to the specified port and installs a flow rule.
       - **If not found**: Floods the packet to all ports.
- **Logging**: Logs every action related to the MAC table and packet handling for tracking and debugging purposes.

### Implementation Details
- **Application Setup**:
  - The application is registered with `appId = coreService.registerApplication("nycu.winlab.bridge")`.
  - Adds a packet processor (`processor`) for handling packets with priority set to 2.
  - Requests packet-in messages for IPv4 packets with reactive priority to handle the learning bridge logic.
- **MAC Table Management**:
  - Each device has an associated MAC address table (stored in `bridgeTable`), which maps each MAC address to its receiving port.
  - If a source MAC and port mapping is not found in the table for a device, the application updates the table with this new entry.
- **Flow Rule Management**:
  - Flow rules are installed dynamically upon matching a destination MAC address, optimizing future packet forwarding.
- **Logging**:
  - Logs an addition to the MAC address table, packet flooding when a MAC is missed, and the installation of flow rules when a match is found.

## Proxy ARP Application

The Proxy ARP Application acts as an ARP responder to handle ARP requests within the network, reducing broadcast traffic and improving ARP response efficiency.

### Features
- **ARP Table Management**: Maintains IP-to-MAC mappings to quickly respond to ARP requests.
- **ARP Handling Process**:
  1. Upon receiving an ARP request, the application checks the ARP table.
  2. If the requested MAC is found, an ARP reply is sent directly to the requester.
  3. If not found, the request is flooded to all ports to retrieve the mapping from the network.

- **Logging Requirements**:
  - Logs table hits and misses, along with each ARP reply sent to ensure full transparency.

### Implementation Details
- **Application Setup**:
  - Registers the application with `appId = coreService.registerApplication("nycu.winlab.ProxyArp")`.
  - Adds a packet processor (`processor`) with handling priority, setting up the environment to process ARP packets.
- **ARP Table Management**:
  - The `arpTable` stores IP-to-MAC mappings, which are updated upon receiving an ARP request or reply.
  - When an ARP request is received, if the target IP is known, the application responds with the associated MAC address.
- **Logging**:
  - Logs each ARP request hit or miss in the table, as well as the emission of ARP replies to ensure traceability.