from mininet.topo import Topo

class Lab1_Topo_313551097( Topo ):
    def __init__( self ):
        Topo.__init__( self )

        # Add hosts
        h1 = self.addHost( 'h1' )
        h2 = self.addHost( 'h2' )
        h3 = self.addHost( 'h3' )
        h4 = self.addHost( 'h4' )
        h5 = self.addHost( 'h5' )

        # Add switches
        s1 = self.addSwitch( 'S1' )
        s2 = self.addSwitch( 'S2' )
        s3 = self.addSwitch( 'S3' )
        s4 = self.addSwitch( 'S4' )
        
        # Add links
        self.addLink( h1, s1 )
        self.addLink( h2, s2 )
        self.addLink( h3, s3 )
        
        self.addLink( s1, s2 )
        self.addLink( s2, s3 )
        
        self.addLink( h4, s4 )
        self.addLink( s2, s4 )
        self.addLink( h5, s4 )
        
        


topos = { 'topo_part2_313551097': Lab1_Topo_313551097 }