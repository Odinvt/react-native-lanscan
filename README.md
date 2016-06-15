# react-native-lanscan
> A React Native library for scanning LAN IPs and ports to find services via device Wi-Fi

###### Support
This package is only supported on **android** devices, since I'm not much of an iOS Developer. Feel free to submit a PR of an iOS port I'll be happy to merge ! :)

###### Permissions
On **android**, this package adds another request for the `android.permission.ACCESS_WIFI_STATE` permission for your app in the package manifest. So no need to add the permission to your app module AndroidManifest.

###### General Information
This package scans the LAN network of the device via Wi-Fi and searches for hosts with services on a port range of your choosing.

Please take your precautions and check if the device is connected to the network with Wi-Fi first as this package doesn't do that for you. Use the React Native official [NetInfo](https://facebook.github.io/react-native/docs/netinfo.html) API.

Please keep in mind that this is a fallback method. There are more suitable ways of finding services on the local network like mDNS of the Zero-configuration standard (implementations like Avahi, Bonjour or NSD). Take a look at the awesome [react-native-zeroconf](https://github.com/Apercu/react-native-zeroconf) package by [Balthazar Gronon](https://github.com/Apercu/). But there are switches and routers that tend to block mDNS Registry and Discovery. And this is where **react-native-lanscan** comes in handy.

Here is how it works :
 - Fetches the device dhcp wifi Info.
 - Calculates the list of the possible IP Addresses on the network using the fetched device IP address and netmask.
 - Calculates the subnet broadcast IP address (e.g For the CIDR `192.168.1.100/24` that would be `192.168.1.255`).
 - Keeps sending datagram UDP packets to the subnet broadcast IP address and the port range of your choosing, and keeps waiting for hosts on the subnet to respond for a specified timeout.
 - If it does not find any hosts, either there aren't any or the switch is blocking subnet broadcast packets. So if you choose to do so, the package falls back to a **DeepScan**.
 - The **DeepScan** consists of sending ICMP ECHO requests to the list of the possible network IPs that was calculated before. (This only works if the switch is not blocking ICMP requests. If it is, well... there isn't much we can do, is there ? :D)
 - Keeps sending datagram UDP packets to each connected host on the same port range and keeps waiting for replies.
 - Once the services are found, you can then initialize a socket connection to those services using the awesome [react-native-udp](https://github.com/tradle/react-native-udp) package or its fork the [react-native-tcp](https://github.com/PeelTechnologies/react-native-tcp) package by [Andy Prock](https://github.com/aprock) depending on your needs.

### Install
    npm i -S react-native-lanscan

#### Android


 - Add the following line to the bottom of your project's `settings.gradle` file.

    `project(':react-native-lanscan').projectDir = new File(settingsDir, '../node_modules/react-native-lanscan/android')`

 - Change the `include` line of your project's `settings.gradle` to include the `:react-native-lanscan` project.

    `include ':react-native-lanscan', ':app'`

 - Open your app's `build.gradle` file and add the following line to the `dependencies` block.

    `compile project(":react-native-lanscan")`

 - In your app's `MainActivity.java` file, add `new LANScanReactModule()` to the return statement of the `getPackages()` function.

```
...
    new MainReactPackage(),
    new LANScanReactModule()
...
```

 - Then in the same file add the import statement :
 `import com.odinvt.lanscan.LANScanReactModule;`


### Usage

    import { LANScan } from 'react-native-lanscan';
    let lanscan = new LANScan();

#### API

All the found hosts are returned via event emitter. You need to bind your listeners to the proper events first depending on your logic, then call the API methods.

##### Methods

###### `scan(min_port, max_port, broadcast_timeout = 500, fallback = true, icmp_timeout = 50, packet_timeout = 500)` Starts the scan for the hosts that have services in the min_port-max_port port range


 - This method does exactly the steps detailed in the **General Information** section.
 - `min_port` and `max_port` are included in the port range.
 - `min_port` and `max_port` must be within the port range 0-65535.
 - The `broadcast_timeout` **(optional)** parameter `default : 500` specifies how much time in `milliseconds` the UDP broadcast messages should wait for replies.
 - The `fallback` **(optional)** parameter `default : true` tells the method to fall back to DEEPSCAN if no host replied to the UDP broadcast messages.
 - The `icmp_timeout` **(optional)** parameter `default : 50` specifies how much time in `milliseconds` each ICMP ECHO request should wait for a reply from the host. Keep in mind that the hosts are on the local network so this shouldn't take more than 10 ms.
 - If a host replies to an ICMP ECHO request, the method starts sending UDP packets to the hosts on the min_port-max_port port range. The `packet_timeout` **(optional)** paramter `default : 500` tells it how much time in `milliseconds` the UDP packets should wait for replies.

> The Datagram UDP Sockets send a **byte[4]** "RNLS" message with the packets and expect a **byte[6]** response message with the packets like "RNLSOK". (Check out the java service example at the last section of this README).

Example :

```javascript

lanscan.scan(48500, 48503, 100, true, 20, 100);

/*
starts the scan for all the hosts in the network
that have ready UDP services on the ports : 48500, 48501, 48502 or 48503
*/

```

###### `fetchInfo()` Fetches the device wifi dhcp info

  - Use this method if you need the system to only fetch the dhcp info without starting a scan because the `scan` method already calls it.
  - The info are returned in the `info_fetched` event too.

###### `getConnectedHosts()` Returns the hosts that successfully replied to an ICMP ECHO request

 - This will return an `array` of the connected hosts.
 - Be sure to **only use this method if** you've set the `fallback` parameter of the `scan` call to `true`. Otherwise, it will always return an empty array as it would've never fallen back to the deepscan.
 - This should be called inside `end_pings` or `end` event handlers. As it is only then when you're sure that all the connected hosts list will be available.

 Example :

 ```javascript

 lanscan.on('end_pings', () => {
   let connected_hosts = lanscan.getConnectedHosts();
   // connected_hosts = ["192.168.1.10", "192.168.1.15"]
   // if 192.168.1.10 & 192.168.1.15 responded to the pings.
 });
 lanscan.scan(48500, 48503, 100, true, 20, 100);

 ```

###### `getAvailableHosts()` Returns the hosts that are available on the LAN and the ports that services are listening on on those hosts

 - This will return an `object` of the available hosts. The keys of the object are the `IP addresses` and the values are `array`s of the ports that services are listening on, on those hosts.
 - This should be called inside an `end` event handler. As it is only then when you're sure that all the available hosts list will be available.
 - You can call this inside an `end_broadcast` event handler too **but only if** you set the `fallback` parameter of the `scan` call to `false`

Example :

```javascript

lanscan.on('end', () => {
  let available_hosts = lanscan.getAvailableHosts();
})
lanscan.scan(48500, 48503, 100, true, 20, 100);

```
`available_hosts` is equal to :

    {
      "192.168.1.10" : [48500, 48502, 48503],
      "192.168.1.15" : [48501],
      "192.168.1.18" : [48502, 48503],
    }

if those are the hosts that have services listening on respective UDP ports. Keep in mind that those would be UDP sockets that responded to either broadcast Datagram UDP packets or host-specific Datagram UDP packets with a byte[6] message (check out the **Java Service Example** section at the end of this README).


###### `stop()` Stops all the running scan threads and cleans up the used resources on the native side

 - This does not clear the connectedHosts and availableHosts cached lists. If you want such behavior, you can overwrite your object with a new instance `lanscan = new LANScan()` (don't forget to bind your event listeners to the new object as those would've been destroyed).

##### Events

```javascript
lanscan.on('eventName', (params...) => {
    ...
})
```

###### `start` Triggered on scan start
###### `stop` Triggered after stop() successfully shuts down device scan threads
###### `start_fetch` Triggered when it starts fetching device dhcp info
###### `info_fetched` Triggered when the device info are fetched

 - Broadcasts an `object` that contains the device Wi-Fi dhcp info

```javascript

lanscan.on('info_fetched', (info) => {
    // do something with `info`
})

```

    {
        ipAddress : "192.168.1.2",
        netmask : "255.255.255.0",
        gateway : "192.168.1.1",
        serverAddress : "192.168.1.1",
        dns1 : "8.8.8.8",
        dns2 : "8.8.4.4",
        hostsNumber : "256"
    }

###### `start_broadcast` Triggered when the broadcast discovery starts before any packets are sent
###### `host_found` Triggered when a host is found with a service running on it

 - Broadcasts an `object`. This object has a `string` property named `host` which is the IP address of the found host, and an `int` property named `port` which is the port of the service that responded.
 - Broadcasts an `object` of the available hosts up until the current host was discovered. The keys of the object are the `IP addresses` and the values are `array`s of the ports that services are listening on, on those hosts.
 - Can trigger either when a host responds to a broadcast UDP packet on a port, or when a host responds to a host-specific UDP packet that was sent to it after it was discovered by an ICMP ECHO request.
 - You can safely use this event to update your UI and show the current found services.


```javascript

lanscan.on('host_found', (host, currentAvailableHosts) => {
   // update my UI
})
lanscan.scan(48500, 48503, 100, true, 20, 100);

```

`host` is equal to :

    {
      host : "192.168.1.15",
      port : 48501
    }

If the service on `192.168.1.15:48501` responded to a UDP packet.

`currentAvailableHosts` is equal to :

    {
      "192.168.1.10" : [48500, 48502, 48503],
      "192.168.1.15" : [48501]
    }

If those are the services that it found up until the event was triggered. If it finds another service on `192.168.1.15:48502`, then, in another `host_found` event trigger, it would just add that to the ports array :

    {
      "192.168.1.10" : [48500, 48502, 48503],
      "192.168.1.15" : [48501, 48502]
    }

###### `end_broadcast` Triggered when the broadcast discovery threads finish

 - You can use this to call `getAvailableHosts()` **but only if** you set the `fallback` parameter of the `scan` call to `false`.

###### `start_pings` Triggered when the package starts pinging the calculated list of possible subnet IPs

 - This only triggers when the `scan` call doesn't find any hosts and the `fallback` parameter is set to `true`.

###### `host_found_ping` Triggered when a host responds to an ICMP ECHO request within the specified timeout

 - Triggers only between `start_pings` event and `end_pings` event in time.
 - Broadcasts a `string` of the IP address of the host that was found.
 - Broadcasts an `array` of the hosts that responded to ping requests up until the moment the current host was found.

 ```javascript

 lanscan.on('host_found_ping', (host, currentConnectedHosts) => {
     // host = "192.168.1.15"
     /* currentConnectedHosts = ["192.168.1.11", "192.168.1.13", "192.168.1.15"]
        if "192.168.1.11" and "192.168.1.13" already responded */
 })

 ```

###### `end_pings` Triggered when all the pings are finished

 - Do not use this to call `getAvailableHosts()` as the threads that send and receive datagram packets may still be running when this event is triggered.

###### `end` Triggered when scan stops (when all threads finish)

 - It is always safe to use this to call `getAvailableHosts()` and `getConnectedHosts()`

###### `port_out_of_range_error` Triggers when the specified min_port and max_port ports are out of range

 - Broadcasts a `string` message of the error.
 - This error does break the execution of the whole process.

###### `fetch_error` Triggers when fetchInfo() of the device Wi-Fi State fails

 - Broadcasts a `string` message of the error.
 - This error does break the execution of the whole process.

###### `error` Triggers whenever an error occurs during the process

 - This includes `port_out_of_range_error` and `fetch_error` errors.
 - Broadcasts a `string` message of the error.
 - This error does break the execution of the whole process.

### Java Service Example

This is an example of a service implementation that will be discovered by the `react-native-lanscan` package.

```java

import java.io.IOException;
import java.net.*;

public class Receiver {

    public static void main(String[] args) {
        int port = 48500;
        new Receiver().run(port);
    }

    public void run(int port) {   
      DatagramSocket serverSocket = null;
      try {
        serverSocket = new DatagramSocket(port);
        byte[] receiveData = new byte[4];

        System.out.printf("Listening on udp:%s:%d%n",
                InetAddress.getLocalHost().getHostAddress(), port);     
        DatagramPacket receivePacket = new DatagramPacket(receiveData,
                           receiveData.length);

        while(true)
        {
              serverSocket.receive(receivePacket);
              String sentence = new String( receivePacket.getData(), 0,
                                 receivePacket.getLength() );
              System.out.println("RECEIVED: " + sentence);
              // now send acknowledgement packet back to sender     
              InetAddress IPAddress = receivePacket.getAddress();
              String sendString = "RNLSOK";
              byte[] sendData = sendString.getBytes("UTF-8");
              DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                   IPAddress, receivePacket.getPort());
              serverSocket.send(sendPacket);
        }
      } catch (IOException e) {
              System.out.println(e);
      } finally {
    	  serverSocket.close();
      }
    }
}

```

### Babel

This component uses ES6. So if you're using `Webpack` you should launch `babel` on `main.js` and output to `main-tf.js` if for some reason the `npm postinstall` script didn't execute.

    "postinstall": "babel main.js --out-file main-tf.js"
