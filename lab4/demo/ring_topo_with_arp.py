# 2-by-2 leaf-spine topology
import os
import sys

from mininet.topo import Topo
from mininet.net import Mininet
from mininet.cli import CLI
from mininet.node import RemoteController, Host, OVSSwitch
from mininet.link import TCLink
from mininet.log import setLogLevel, info, warn

class MyTopo(Topo):

    switch = []
    host = []

    def __init__(self):

        # initialize topology
        Topo.__init__(self)

        for i in range(1, 6):
            self.switch.append(self.addSwitch("s"+str(i), dpid="000000000000000"+str(i)))

        self.host.append(self.addHost("h1", cls=IpHost, mac="00:00:00:00:00:01", ip="10.6.1.1/24", gateway="10.6.1.254"))
        self.host.append(self.addHost("h2", cls=IpHost, mac="00:00:00:00:00:02", ip="10.6.1.2/24", gateway="10.6.1.254"))
        self.host.append(self.addHost("h3", cls=IpHost, mac="00:00:00:00:00:03", ip="10.6.1.3/24", gateway="10.6.1.254"))

        # add links
	#for i in range(2):
        self.addLink(self.switch[0], self.host[0])
        self.addLink(self.switch[4], self.host[1])
        self.addLink(self.switch[2], self.host[2])

        self.addLink(self.switch[0], self.switch[1])
        self.addLink(self.switch[0], self.switch[3])
        self.addLink(self.switch[1], self.switch[2])
        self.addLink(self.switch[2], self.switch[4])
        self.addLink(self.switch[3], self.switch[4])

class IpHost(Host):
    def __init__(self, name, gateway, *args, **kwargs):
        super(IpHost, self).__init__(name,*args,**kwargs)
        self.gateway = gateway

    def config(self, **kwargs):
        Host.config(self,**kwargs)
        mtu = "ifconfig " + self.name + "-eth0 mtu 1490"
        self.cmd(mtu)
        self.cmd('ip route add default via %s' % self.gateway)
        self.set_static_arp()
        
    def set_static_arp(self):
        """Set static ARP entries for all hosts."""
        arp_entries = {
            "h1": ("10.6.1.1", "00:00:00:00:00:01"),
            "h2": ("10.6.1.2", "00:00:00:00:00:02"),
            "h3": ("10.6.1.3", "00:00:00:00:00:03")
        }
        for host, (ip, mac) in arp_entries.items():
            if host != self.name: # Avoid setting ARP entry for self
                self.cmd(f'arp -s {ip} {mac}')

topos = {'mytopo': (lambda: MyTopo())}

if __name__ == "__main__":
    setLogLevel('info')

    topo = MyTopo()
    net = Mininet(topo=topo, link=TCLink, controller=None)
    net.addController('c0', switch=OVSSwitch, controller=RemoteController, ip="127.0.0.1")

    net.start()
    CLI(net)
    net.stop()
