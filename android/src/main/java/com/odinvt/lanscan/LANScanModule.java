package com.odinvt.lanscan;

import com.facebook.react.bridge.GuardedAsyncTask;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.odinvt.lanscan.impl.ManagedThreadPoolExecutor;
import com.odinvt.lanscan.utils.IPv4;

import android.content.Context;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import javax.annotation.Nullable;

public class LANScanModule extends ReactContextBaseJavaModule {
    private static final int MIN_PORT_NUMBER = 0;
    private static final int MAX_PORT_NUMBER = 0xFFFF;

    private static final String KEY_WIFISTATE_DNS1 = "dns1";
    private static final String KEY_WIFISTATE_DNS2 = "dns2";
    private static final String KEY_WIFISTATE_GATEWAY = "gateway";
    private static final String KEY_WIFISTATE_IPADDRESS = "ipAddress";
    private static final String KEY_WIFISTATE_LEASEDURATION = "leaseDuration";
    private static final String KEY_WIFISTATE_NETMASK = "netmask";
    private static final String KEY_WIFISTATE_SERVERADDRESS = "serverAddress";
    private static final String KEY_IPv4_HOSTSNUMBER = "hostsNumber";


    private static final String EVENT_START = "RNLANScanStart";
    private static final String EVENT_STOP = "RNLANScanStop";
    private static final String EVENT_STARTFETCH = "RNLANScanStartFetch";
    private static final String EVENT_INFOFETCHED = "RNLANScanInfoFetched";
    private static final String EVENT_FETCHERROR = "RNLANScanFetchError";
    private static final String EVENT_STARTPINGS = "RNLANScanStartPings";
    private static final String EVENT_HOSTFOUNDPING = "RNLANScanHostFoundPing";
    private static final String EVENT_ENDPINGS = "RNLANScanEndPings";
    private static final String EVENT_PORTOUTOFRANGEERROR = "RNLANScanPortOutOfRangeError";
    private static final String EVENT_STARTBROADCAST = "RNLANScanStartBroadcast";
    private static final String EVENT_HOSTFOUND = "RNLANScanHostFound";
    private static final String EVENT_ENDBROADCAST = "RNLANScanEndBroadcast";
    private static final String EVENT_END = "RNLANScanEnd";
    private static final String EVENT_ERROR = "RNLANScanError";

    private DhcpInfo dhcp_info;
    private IPv4 ipv4_wifi;
    private ArrayList<String> hosts_list;
    private HashMap<String, ArrayList<Integer>> available_hosts;

    public LANScanModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "RNLANScan";
    }

    @ReactMethod
    public void fetchInfo(boolean force) {
        if(this.dhcp_info == null)
            this.getInfo();
        else if(force)
            this.getInfo();
    }

    @ReactMethod
    public void stop() {
        new GuardedAsyncTask<Void, Void>(getReactApplicationContext()) {
            @Override
            protected void doInBackgroundGuarded(Void... params) {
                ((ThreadPoolExecutor)ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_PINGS).shutdownNow();
                ((ThreadPoolExecutor)ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_BROADCAST).shutdownNow();

                long startTime = System.currentTimeMillis();
                long endTime = 0L;
                long timeout = 1000;
                boolean isTerminated_broadcast = false;
                boolean isTerminated_pings = false;

                // wait until all the threads are terminated
                // or grace timeout finishes
                while(!isTerminated_broadcast || !isTerminated_pings || endTime < timeout) {
                    isTerminated_broadcast = ((ThreadPoolExecutor)ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_BROADCAST).isTerminated();
                    isTerminated_pings = ((ThreadPoolExecutor)ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_BROADCAST).isTerminated();
                    endTime = (new Date()).getTime() - startTime;
                }

                // successfully stopped the tasks... send top event
                sendEvent(getReactApplicationContext(), EVENT_STOP, null);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @ReactMethod
    public void scan(final int min_port, final int max_port, final int broadcast_timeout, final boolean fallback, final int ping_ms, final int port_ms) {

        // start event should be sent before probable errors
        sendEvent(getReactApplicationContext(), EVENT_START, null);

        if(min_port < MIN_PORT_NUMBER || min_port > MAX_PORT_NUMBER || max_port < MIN_PORT_NUMBER || max_port > MAX_PORT_NUMBER) {
            String err = "Port out of range";
            sendEvent(getReactApplicationContext(), EVENT_PORTOUTOFRANGEERROR, err);
            sendEvent(getReactApplicationContext(), EVENT_ERROR, err);

            return ;
        }

        if(this.getInfo()) {
            final String broadcastAddr = ipv4_wifi.getBroadcastAddress();

            this.available_hosts = new HashMap<>();

            // trigger the start broadcast event if it is a broadcast address
            sendEvent(getReactApplicationContext(), EVENT_STARTBROADCAST, null);

            for(int i = min_port; i <= max_port; i++) {
                sendDatagram(broadcastAddr, true, i,broadcast_timeout, ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_BROADCAST);
            }

            ((ThreadPoolExecutor) ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_BROADCAST).shutdown();
            final long timeout = broadcast_timeout + 500;


                //awaitTermination of the sendDatagram tasks after locking them with shutdown inside an AsyncTask (no ui framedrops)
                new GuardedAsyncTask<Void, Void>(getReactApplicationContext()) {
                    @Override
                    protected void doInBackgroundGuarded(Void... params) {

                        // TODO: better to use ThreadPoolExecutor.awaitTerminated (for some reason doesn't interrupt)
                        long completed_tasks;
                        long task_count = ((ThreadPoolExecutor) ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_BROADCAST).getTaskCount();

                        long startTime = System.currentTimeMillis();
                        long endTime = 0L;
                        // infinite loop that stops on 1 of the 2 conditions:
                        // tasks are completed or timeout ran out
                        while (true) {
                            completed_tasks = ((ThreadPoolExecutor) ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_BROADCAST).getCompletedTaskCount();
                            if (completed_tasks < task_count || endTime < timeout) {
                                endTime = (new Date()).getTime() - startTime;
                                continue;
                            }

                            // at this point all the tasks should be closed. send end broadcast event
                            sendEvent(getReactApplicationContext(), EVENT_ENDBROADCAST, null);


                            if(fallback) {

                                // if no device are found and user wants to fallback to host to host port scan
                                if (available_hosts.size() == 0) {

                                    sendEvent(getReactApplicationContext(), EVENT_STARTPINGS, null);

                                    ArrayList<String> connected = new ArrayList<>();
                                    Log.wtf("NETWORK", "PINGING " + hosts_list.size() + " Hosts....");
                                    String device_ip = "";
                                    try {
                                        device_ip = intToIp(dhcp_info.ipAddress);
                                    } catch (UnknownHostException e) {
                                        e.printStackTrace();
                                    }
                                    for (String host : hosts_list) {
                                        try {
                                            if (host.equals(device_ip))
                                                continue;
                                            if (InetAddress.getByName(host).isReachable(ping_ms)) {
                                                connected.add(host);

                                                Log.wtf("HOST FOUND !!!", host + " RESPONDED");
                                                sendEvent(getReactApplicationContext(), EVENT_HOSTFOUNDPING, host);

                                                for (int i = min_port; i <= max_port; i++) {
                                                    sendDatagram(host, false, i, port_ms, ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_PINGS);
                                                }
                                            } else {
                                                Log.wtf("HOST NOT RESPONSIVE", host + " is not responding");
                                            }
                                        } catch (IOException ioe) {
                                    /* do nothing just continue to the next host */
                                            ioe.printStackTrace();
                                        }
                                    }
                                    sendEvent(getReactApplicationContext(), EVENT_ENDPINGS, connected.size());

                                }

                            }

                            // start waiting for ping UDP tasks to finish to send the end event
                            ((ThreadPoolExecutor) ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_PINGS).shutdown();
                            long timeout_pings = port_ms + 500;

                            // TODO: better to use ThreadPoolExecutor.awaitTerminated (for some reason doesn't interrupt)
                            long completed_tasks_pings;
                            long task_count_pings = ((ThreadPoolExecutor) ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_PINGS).getTaskCount();

                            long startTime_pings = System.currentTimeMillis();
                            long endTime_pings = 0L;
                            // wait for ping tasks
                            // infinite loop that stops on 1 of the 2 conditions:
                            // ping udp tasks are completed or timeout ran out
                            while(true) {
                                completed_tasks_pings = ((ThreadPoolExecutor) ManagedThreadPoolExecutor.THREAD_POOL_EXECUTOR_PINGS).getCompletedTaskCount();
                                if (completed_tasks_pings < task_count_pings || endTime_pings < timeout_pings) {
                                    endTime_pings = (new Date()).getTime() - startTime_pings;
                                    continue;
                                }

                                // at this point all the tasks (pings or broadcast) should be closed. send end event
                                sendEvent(getReactApplicationContext(), EVENT_END, null);

                                break;
                            }



                            break;
                        }
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);


        }
    }

    public void sendDatagram(final String broadcastAddr,
                             final boolean broadcast,
                             final int port,
                             final long timeout_ms,
                             Executor thread_pool) {


        try {
            final DatagramSocket serverSocket = new DatagramSocket();
            serverSocket.setBroadcast(broadcast);
            serverSocket.setReuseAddress(true);
            InetAddress IPAddress = InetAddress.getByName(broadcastAddr);
            Log.wtf("Info", "Sending Discovery message to " + IPAddress.getHostAddress() + " Via UDP port " + port);

            // we're sending "RNLS" message so if you need to check on the other devices on local network
            // you need to open an udp listener on port 'port' and wait for the message "RNLS" which is a byte[4]
            byte[] sendData = new byte[4];
            sendData[0] = 'R';
            sendData[1] = 'N';
            sendData[2] = 'L';
            sendData[3] = 'S';

            final DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,IPAddress,port);

            Log.wtf("STARTING TASK : ", "STARTING RECEIVER TASK FOR " + broadcastAddr);
            // Execute a receiver task in the background to start waiting for LAN replies before sending packets
            final AsyncTask guarded_receive_task = new GuardedAsyncTask<Void, Void>(getReactApplicationContext()) {
                @Override
                protected void doInBackgroundGuarded(Void... params) {
                    byte[] receiveData = new byte[6]; // waiting for the message "RNLSOK"
                    DatagramPacket receivePacket = new DatagramPacket(receiveData,
                            receiveData.length);

                    // skip if it is the current device
                    String device_ip = "";
                    try {
                        device_ip = intToIp(dhcp_info.ipAddress);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }

                    //noinspection InfiniteLoopStatement
                    while(!isCancelled()) {

                        try {
                            Log.wtf("NETWORK : ", "WAITING FOR DATAGRAM RESPONSE...");
                            serverSocket.receive(receivePacket);

                            String sentence = new String( receivePacket.getData(), 0,
                                    receivePacket.getLength() );
                            Log.wtf("RECEIVED PACKET : " , sentence + " FROM " + receivePacket.getAddress() + ":" + receivePacket.getPort());

                            InetAddress address = receivePacket.getAddress();
                            String addr = address.getHostAddress();
                            int port = receivePacket.getPort();


                            // skip if packet received from current device
                            if(addr.equals(device_ip)) {
                                Log.wtf("SKIPING : ", "SKIPPING CURRENT DEVICE RESPONDED ....");
                                continue;
                            }

                            if(available_hosts.containsKey(addr)) {
                                if(!available_hosts.get(addr).contains(port)) {
                                    available_hosts.get(addr).add(port);
                                }
                            } else {
                                ArrayList<Integer> port_arr = new ArrayList<>();
                                port_arr.add(port);
                                available_hosts.put(addr, port_arr);
                            }

                            WritableMap available_host = new WritableNativeMap();
                            available_host.putString("host", addr);
                            available_host.putInt("port", port);
                            sendEvent(getReactApplicationContext(), EVENT_HOSTFOUND, available_host);
                            Log.wtf("NETWORK : ", "LOOPING BACK FOR THE NEXT DATAGRAM RECEIVE...");
                        } catch (IOException e) {
                            Log.e("IOE", e.getMessage());
                            /* cancel current task if can't listen for packets */
                            this.cancel(true);
                        }
                    }
                }

                @Override
                protected void onCancelled() {
                    Log.wtf("CLOSING TASK : ", "CLOSING RECEIVER TASK FOR " + broadcastAddr);

                    // at this point the socket is not used anymore so we can go ahead and close it
                    serverSocket.disconnect();
                    serverSocket.close();
                }
            }.executeOnExecutor(thread_pool);

            Log.wtf("STARTING TASK : ", "STARTING SENDER TASK FOR " + broadcastAddr);
            // start sending packets on a background task until it is cancelled then trigger end broadcast event
            // if it is a broadcast address
            final AsyncTask guarded_send_task = new GuardedAsyncTask<Void, Void>(getReactApplicationContext()) {
                @Override
                protected void doInBackgroundGuarded(Void... params) {


                    while(!isCancelled()) {
                        try {
                            //Log.wtf("SENDING PACKET : " , sendPacket.getData().toString() + " TO " + broadcastAddr + ":" + port);
                            serverSocket.send(sendPacket);
                        } catch (IOException e) {
                            /* do nothing just continue the loop to send the next packet */
                            Log.e("ERROR SENDING PACKETS " , e.getMessage());
                        }
                    }
                    // when we exit the loop it means that the task has been cancelled and we're not sending packets anymore

                    Log.wtf("CLOSING TASK : ", "TRYING TO CLOSE RECEIVER TASK FROM SENDER FOR " + broadcastAddr);
                    // shutdown the receiver task since we're not gonna be needing it anymore
                    if(guarded_receive_task != null)
                        guarded_receive_task.cancel(true);
                }

                @Override
                protected void onCancelled() {
                    Log.wtf("CLOSING TASK : ", "CLOSING SENDER TASK FOR " + broadcastAddr);

                }
            }.executeOnExecutor(thread_pool);

            // run a sleep task on the background to wait for the timeout
            new GuardedAsyncTask<Void, Void>(getReactApplicationContext()) {
                @Override
                protected void doInBackgroundGuarded(Void... params) {
                    SystemClock.sleep(timeout_ms);
                    Log.wtf("WAITED TIMEOUT : ", "WAIT FOR TIMEOUT FINISHED TRYING TO CLOSE SENDER TASK FOR " + broadcastAddr + "...");
                    if(guarded_send_task != null)
                        guarded_send_task.cancel(true);
                }
            }.executeOnExecutor(thread_pool);



        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
        finally {
            /* should not be closing datagram socket because it's still used asynchronously by threads */
        }


    }

    private boolean getInfo() {
        sendEvent(getReactApplicationContext(), EVENT_STARTFETCH, null);

        WifiManager wifi_manager= (WifiManager) getReactApplicationContext().getSystemService(Context.WIFI_SERVICE);
        dhcp_info=wifi_manager.getDhcpInfo();

        try {
            String s_dns1 = intToIp(dhcp_info.dns1);
            String s_dns2 = intToIp(dhcp_info.dns2);
            String s_gateway = intToIp(dhcp_info.gateway);
            String s_ipAddress = intToIp(dhcp_info.ipAddress);
            int s_leaseDuration = dhcp_info.leaseDuration;
            String s_netmask = intToIp(dhcp_info.netmask);
            String s_serverAddress = intToIp(dhcp_info.serverAddress);

            WritableMap device_info = new WritableNativeMap();
            device_info.putString(KEY_WIFISTATE_DNS1, s_dns1);
            device_info.putString(KEY_WIFISTATE_DNS2, s_dns2);
            device_info.putString(KEY_WIFISTATE_GATEWAY, s_gateway);
            device_info.putString(KEY_WIFISTATE_IPADDRESS, s_ipAddress);
            device_info.putInt(KEY_WIFISTATE_LEASEDURATION, s_leaseDuration);
            device_info.putString(KEY_WIFISTATE_NETMASK, s_netmask);
            device_info.putString(KEY_WIFISTATE_SERVERADDRESS, s_serverAddress);

            ipv4_wifi = new IPv4(s_ipAddress, s_netmask);
            hosts_list = ipv4_wifi.getHostAddressList();

            device_info.putInt(KEY_IPv4_HOSTSNUMBER, ipv4_wifi.getNumberOfHosts().intValue());

            sendEvent(getReactApplicationContext(), EVENT_INFOFETCHED, device_info);

            return true;
        } catch (UnknownHostException e) {

            sendEvent(getReactApplicationContext(), EVENT_FETCHERROR, e.getMessage());
            sendEvent(getReactApplicationContext(), EVENT_ERROR, e.getMessage());
        }

        return false;

    }

    public String intToIp(int i) throws UnknownHostException {

        byte[] addressBytes = { (byte)(0xff & i),
                (byte)(0xff & (i >> 8)),
                (byte)(0xff & (i >> 16)),
                (byte)(0xff & (i >> 24)) };

        InetAddress addr = InetAddress.getByAddress(addressBytes);

        return addr.getHostAddress();
    }

    protected void sendEvent(ReactContext reactContext,
                             String eventName,
                             @Nullable Object params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        Log.wtf("EVENT : ", "TRIGGERED EVENT " + eventName);
    }

}
