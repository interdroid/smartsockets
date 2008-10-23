** SmartSockets 1.4 README **
-----------------------------

This distribution contains version 1.4 of the SmartSockets library. 


What is SmartSockets:
---------------------

Tightly coupled parallel applications are increasingly run in Grid 
environments. Unfortunately, on many Grid sites the ability of machines 
to create or accept network connections is severely limited by firewalls, 
network address translation (NAT) or non-routed networks. Multi homing 
further complicates connection setup and machine identification. Although 
ad-hoc solutions exist for some of these problems, it is usually up to 
the application's user to discover the cause of the connectivity problems 
and find a solution. 

SmartSockets is a open source communication library that lifts this burden 
by automatically discovering many of the connectivity problems and solving 
them with as little support from the user as possible. 

SmartSockets can be seen as wrapper around the standard Java sockets 
implementation. It is not a full drop-in replacement for sockets, since the 
interface of SmartSockets differs somewhat from the original Java Sockets 
interface (and because SmartSockets itself is implemented using Java Sockets).
Nevertheless, the API of SmartSockets is very similar to that of original 
sockets. As a result, converting existing applications to SmartSockets in 
usually quite straightforward.


Requirements:
-------------

SmartSockets in implemented in Java, and requires Java 1.5 or higher. 
To build SmartSockets from source, the ant build system of apache is 
used (see http://ant.apache.org/ for details).


Ideas behind smartsockets:
--------------------------

Simply put, SmartSockets attempts to solve the following connectivity 
problems which are often encoutered in distributed and/or Grid computing:

 Firewalls 
    - These prevent direct connections to a machine. Some may also 
      prevent all connections going out of a machine, with the exception 
      of certain 'trusted' traffic, such as SSH or HTTP.
 
 NAT 
    - Prevent direct connections to a machine and causes machines to
      have non-unqiue IP addresses (site-local addresses). 
 
 Multi homing
    - When machines have multiple IP addresses it is not always clear
      which address you should use when trying to connect to it. You 
      may even need to use different addresses depending on where the 
      connection originates.

 Non-routed networks 
   - In some sites (especially compute clusters) internal machines may 
     only be connected to a local network which does not route any data
     to/from the internet. These machines can only be accessed through a 
     special frontend machine which is connected to both the internet and 
     the local network.

 Incorrect host names
   - Many machine use incorrectly configured hostnames. Publishing the 
     hostname of a machine as its contact address will result in 
     connectivity problems, because the hostname can not be properly 
     resolved on other machines. 

SmartSockets solves these problems using the following mechanisms:

1) Use extended addresses:

Instead of assuming a single IP address is enough to contact a
machine, SmartSockets uses as much contact information as it can 
find. Every IP address a machine has to offer, (optionally) an
external IP address when the machine is behind a NAT, SSH contact 
information, etc. Hostnames are generally not used, since they are 
often incorrect. All this information put together will be the 
'address' of the machine. If this information is not guaranteed 
to be unique (for example, because the machine only has site-local
IP-addresses), a globally unique UUID will also be included in the 
address.   

As a result, whenever a connection setup is performed, SmartSockets 
knowns all the potential IP addresses at which the target machine can 
be reach. In addition, it can even use SSH tunneling. This greatly 
improves the chances of a correct connection setup in the face of 
multi homing, incorrect host names and NAT, without requiring user 
intervention. 

2) Use a support network of hubs:

To circumvent firewalls, NAT and non-routed networks, SmartSockets 
uses 'hubs'. These 'hubs' are support processes which are generally
started on machines which have 'more' connectivity. This could a 
a completely open machine that is connected to the internet, or the  
frontend machine of a cluster which internally uses a non-routed 
network. By starting one or more hubs in strategic places and 
connecting them together, a support network can be created that can 
be used by SmartSockets.

Generally, when a SmartSockets application is started, it will 
connect to a hub in its vicinity. The location of this hub is 
usually provided by the user, although SmartSockets also has some
limited support for hub discovery. 

The address of the hub to which the SmartSockets instance is 
connected will then be included in the addresses of the server 
sockets on this machine. As a result, whenever a remote SmartSocket 
instance is not capable of directly connecting to this server socket 
it can use the hub network as a fallback mechanism to forward 
requests, or even data to the server socket. 

Using this mechanism, SmartSockets can attempt to reverse the 
connection order. That is, when a client tries to connect to a 
server but fails because this server is behind a firewall or NAT, 
the hub network is used request a connection setup from the server 
to the client instead. This will approach will restore connectivity 
in situations where a single Firewall or NAT device is used. 

When both client and server are behind a firewall/NAT, SmartSockets
can attempt to use TCP splicing. In this approach, both sides 
simultaneously attempt a connection setup to each other, which in 
some cases may result in a connection. Note that this mechnisms is 
very sensitive to timing and has several other problems. Therefore it 
is switched off by default.
     
Finally, when it is not possible to create a direct connection between 
the two machines (e.g., because one or both are on a non-routed 
network), SmartSockets can route all user data over the network of 
hubs. Although the performance of such a 'virtual connection' is average 
at best, it does restore connectivity in a situation in which no 
communication was possible at all.   

Please see the paper at: 

 http://projects.gforge.cs.vu.nl/ibis/publications/maassen_hpdc_2007.ps

for more information on the ideas behind smartsockets.


Programming Model:
------------------

The programming model of SmartSockets is very similar to that of regular 
sockets; using a socket factory you can create server sockets (for 
receiving incoming connections) and client sockets (for creating outgoing 
connections). In SmartSocket this socketfactory is implemented by the 
folowing class:

   ibis.smartsockets.virtual.VirtualSocketFactory

This class contains several methods for creating socket factory instances. 
Once a VirtualSocketFactory is creates you van use the 

   VirtualSocketFactory.createServerSocket(...) 
   VirtualSocketFactory.createClientSocket(...)

methods to create server and client sockets. For convenience, several versions 
of these calls exist (with different signatures).

To establish a connection, you need to know the address of the server socket 
you want the client to connect to. This address is an VirtualSocketAddress object,  
an extension of the regular java.net.SocketAddress. You can retrieve the 
VirtualSocketAddress from the server socket, using the 'getLocalSocketAddres'
method. Like regular SocketAddress, this object is Serializable, so it can be 
transferred across the network or saved to disk. It can als be converted to and
from a String representation.
 
As explained above, a VirtualSocketAddress does not just contain a single IP 
address. Instead, it may contain several IP addresses, SSH contact information, 
clustering information, etc. Any information that may be needed to read the 
server socket is stored in its VirtualSocketAddress.


Creating a hub network:
-----------------------

Before runnning a SmartSockets application, a hub network needs to be created.
In many cases, is is sufficient to start a single hub in a location that is 
reachable from all the intended participants.

Starting a hub can be done using the following script in the smartsockets 
distribution:

   ./bin/hub

When started, the hub will print it's contact address. For example:

   "Hub running on: 130.37.193.15-17878~jason"

If multiple hubs are used, this address can be provided as a parameter to  
other hubs, like this: 

   ./bin/hub 130.37.193.15-17878~jason  

This second hub will then attempt to connect to it. Note that hubs can only 
use direct connections or SSH tunneling. Since they are part of the SmartSockets 
implementation, they cannot use the advanced connection setup schemes themselves.

When two or more hubs succeed in creating a connection, they will start gossiping 
about the contact addresses of the hubs they know. This way, a hub that cannot 
connect to another hub itself, may be able to pass its address on to another hub 
that is able to establish a connection.

Generally, a hub network should at least form a spanning tree to be useful. 
Unfortunately, setting up such a network is currently something that requires 
some experimentation by the user. Some simple guidelines apply however: 

 1) Generally, you need one hub per site. Preferably on a well connected machine 
    such as a cluster frontend. 

 2) It often helps to start a hub on a machine that is completely open. The 
    machine itself does not need to participate in the application (or even 
    be located anywhere near it). It just serves as a meeting point for other, 
    less connected hubs. 

 3) Starting the hubs in two phases may also be useful: first start each of 
    them seperately to find out the contact address that will be used on that 
    site. Then stop all of them and restart them, provinding a list of all other 
    hubs.  


Running applications:
---------------------

Once the hub network is running, the application using SmartSockets can be 
started. The exact way in which this is done is application dependant, but 
each of the application instances does need to be able to find their local 
hub. There are (at least) three ways of doing this:

  1) By providing a command line parameter when you start Java. 

       java ... -Dsmartsockets.hub.addresses=130.37.193.15-17878~jason ...

     This option will set a property which allows SmartSockets to find the 
     hub. Note that multiple (comma separated) hub addresses can be provided.
     SmartSockets will try to connect to them in the order provided, and use 
     the first one it can reach.

  2) By creating a 'smartsockets.properties' file that contains the line:
  
       smartsockets.hub.addresses=130.37.193.15-17878~jason,...

     Make sure that this file can be found by SmartSockets, either by putting 
     it in the local directry from which the application is run, or by adding 
     its location to your CLASSPATH.
 
  3) By creating a file that contains the line shown in 2) and then refering 
     to it when you start java:

       java ... -Dsmartsockets.file=<path/to/file> ...

Besides these (almost) mandatory options, SmartSockets has many other settings 
that can be tweaked. The 'smartsockets.properties.example' file shows most of 
them, and includes a resonably extensive explanation of what they do. 

Note that to use SmartSockets, you must include the file 'smartsockets-1.4.jar'
and all dependancies in the 'external' directory of the distribution into 
you classpath. The 'bin/app' script in the distribution illustrates how this can 
be done. An example is shown below. 


Example application:
--------------------

PLEASE NOTE: The example below works fine, but others may no longer work (or make 
             any sense). They need to be tested for the final release!!

The SmartSockets distribution contains several test applications and benchmarks 
in the 'test.*' packages. As an example, we will now describe how to run one of 
these applications, a simple latency test.

We start by creating a hub network as described above. In our case, a single hub 
is sufficient:
   
   ./bin/hub
  
Which prints: 

  130.37.193.15-17878~jason

We now start one of the test applications using a script in the distribution:

  ./bin/app test.virtual.simple.Latency \
       -Dsmartsockets.hub.addresses=130.37.193.15-17878~jason

Note that we provide the hub contact address to the application using the '-D'
option, as described above. This application now prints:

  Creating server
  Created server on 130.37.193.15-44672:3000@130.37.193.15-17878~jason#
  Server waiting for connections

The application has created a serversocket, and printed its content as a string
(the "130.37.193.15-44672:3000@130.37.193.15-17878~jason#"). As you can see, the 
hub address is also part of this data.

We now start the client side of the application on a different machine, a laptop
behind an ADSL modem which does NAT:

  ./bin/app test.virtual.simple.Latency \
        -Dsmartsockets.hub.addresses=130.37.193.15-17878~jason \
        -target 130.37.193.15-44672:3000@130.37.193.15-17878~jason# \
        -count 100

This commandline instructs the application to connect to the server, and then 
measure the time it takes to do 100 round trip messages. The output: 

  Created connection to 130.37.193.15-44672:3000@130.37.193.15-17878~jason#
  Configured socket: 
   sendbuffer     = 131071
   receiverbuffer = 131071
   no delay       = true
  Starting test
  Test took 808 ms. RTT = 8.08 ms.  

shows that the connection and test was succesfull.


Known bugs and limitations:
---------------------------

Currently, SmartSockets has the following bugs and limitations:

 - The code is in desperate need of decent documentation (JavaDoc!)

 - The TCP splicing mechanism is switched off by default, since it 
   cannot be trusted to provide us with a connection in a resonable time. 

 - Setting up the hub network requires a certain amount of black magic. 

 - When a single hub needs to serve many machines, this may become a bottleneck. 

 - Do not expect a very good performance of message routing over the hubs.

 - If a machine does not serve incoming connections fast enough, the backlog may  
   fill up, causing connections to fail completely. As a result, SmartSockets 
   will try alternative ways of connecting (such as reverse, or routed) even if 
   the machine can normally be contacted directly. It is unclear how we can 
   prevent this. 

 - Related to this: The SSH tunneling is switched off for the application side 
   of SmartSockets, since that would cause an additional (expensive) connection 
   setup attempt. Currently, only the hubs use SSH by default.

 - Machines can only connect to a single hub, and include the hub address in 
   their own addresses. As a result, if the hub crashes, the machine cannot 
   start using another hub without changing their addresses. In addition all 
   virtual connections running over this hub are lost. Therefore hubs reduce 
   the fault tolerance of the system.

The current version of SmartSockets has been use quite extensively in our 
research and seems to work well. However, as always, while some features 
are heavily used, other are hardly run at all. As a result, you may 
encounter problems, simply because you a running in an environment that 
it very different from our own. Whenever this happens, we are very, very 
interested in feedback. The goal of SmartSockets is to build a system that 
provides network connectivity in 'difficult' networks. Therefore, if you 
happen to run into a network problem we haven't thought of, we like to 
know! Contact information can be found below. 


Future work:
------------

There are plans to extend SmartSockets in the following ways:

 - Reduce the depenency of the applications on one single hub (see 
   bugs and limitations). By no longer incorperating the hub address 
   into the server socket addresses, we could use multiple hubs 
   simultaneously, or switch hubs dynamically. This would allow us 
   to 'search' for the most efficient hub. We could also improve the 
   reliability by automatically re-routing virtual connection data 
   when a hub crashes.

 - It may be interesting to add support for high-bandwidth 
   high-latency connections, such as optical wide area links. Using TCP 
   as a transport layer causes problems due to the limited buffer size 
   of TCP. By using multiple TCP streams, or completely different 
   protocols (such as BLAST) we could transparently offer fast 
   connections, while using the same socket-like interface.

 - It may be possible to dynamic improve the performance of a connection 
   while it is being used. For example, while the application is using 
   a connection, smartsockets may try to find better routes over the hubs, 
   or continue to try and create a direct connection if it is not available 
   yet. 


Contact:
--------

More information can be found on the Ibis project website:

  http://www.cs.vu.nl/ibis/

The latest SmartSockets source repository tree is accessible through SVN at
https://gforge.cs.vu.nl/svn/ibis/smartsockets/trunk. You need an account on
https://gforge.cs.vu.nl/ to access the repositories there. You can
create an account by clicking the 'New Account' button on the
https://gforge.cs.vu.nl/ page.

You can send bug reports, feature requests, cries for help, or descriptions of 
interesting way in which you have used SmartSockets to: jason at cs.vu.nl 


Legal stuff:
------------

SmartSockets has been developed as part of the Ibis project, a grid software 
project of the Computer Systems group of the Computer Science department of 
the Faculty of Sciences at the Vrije Universiteit, Amsterdam, The Netherlands. 
The main goal of the Ibis project is to create an efficient Java-based 
platform for grid computing.

SmartSockets is free software. See the file "LICENSE.txt" for copying permissions.

** Third party libraries included with SmartSockets **

This product includes software developed by the Apache Software
Foundation (http://www.apache.org/).

The Slf4j copyright notice lives in "notices/LICENSE.slf4j.txt".
The Log4J copyright notice lives in "notices/LICENSE.log4j.txt".  The
Commons copyright notice lives in notices/LICENSE.apache-2.0.txt".

This product includes jstun, which is distributed with a dual license,
one of which is version 2.0 of the Apache license. It lives in
"notices/LICENSE.apache-2.0.txt".

This product includes the UPNP library from SuperBonBon Industries. Its
license lives in "notices/LICENSE.apache-2.0.txt".

This product includes the Trilead SSH-2 library. Its license
lives in "notices/LICENSE.trilead.txt".

This product includes software developed by TouchGraph LLC
(http://www.touchgraph.com/). Its license lives in 
"notices/LICENSE.TG.txt".
