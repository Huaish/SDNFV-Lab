from mininet.topo import Topo

class Topo_313551097( Topo ):
    def __init__( self ):
        Topo.__init__( self )

        # Add switches
        s1 = self.addSwitch( 'S1' )
        s2 = self.addSwitch( 'S2' )
        s3 = self.addSwitch( 'S3' )

        # Add hosts
        h1 = self.addHost('h1', ip='10.0.0.1/24')
        h2 = self.addHost('h2', ip='10.0.0.2/24')

        
        # Add links
        self.addLink( h1, s1 )
        self.addLink( h2, s2 )
        self.addLink( s1, s2 )
        self.addLink( s2, s3 )
        self.addLink( s3, s1 )

topos = { 'topo_313551097': Topo_313551097 }