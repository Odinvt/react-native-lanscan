import RN, { NativeModules, DeviceEventEmitter } from 'react-native';
import { EventEmitter } from 'events'
EventEmitter.defaultMaxListeners = Infinity;

let RNLANScan = NativeModules.RNLANScan;

export class LANScan extends EventEmitter {

    static listenerCount = 0;

    _connectedHosts;
    _availableHosts;

    constructor(props) {
        super(props);

        this._connectedHosts = [];
        this._availableHosts = {};

        if(LANScan.listenerCount && LANScan.listenerCount > 0) {
            DeviceEventEmitter.removeAllListeners('RNLANScanStart');
            DeviceEventEmitter.removeAllListeners('RNLANScanStop');
            DeviceEventEmitter.removeAllListeners('RNLANScanStartFetch');
            DeviceEventEmitter.removeAllListeners('RNLANScanInfoFetched');
            DeviceEventEmitter.removeAllListeners('RNLANScanFetchError');
            DeviceEventEmitter.removeAllListeners('RNLANScanStartPings');
            DeviceEventEmitter.removeAllListeners('RNLANScanHostFoundPing');
            DeviceEventEmitter.removeAllListeners('RNLANScanEndPings');
            DeviceEventEmitter.removeAllListeners('RNLANScanPortOutOfRangeError');
            DeviceEventEmitter.removeAllListeners('RNLANScanStartBroadcast');
            DeviceEventEmitter.removeAllListeners('RNLANScanHostFound');
            DeviceEventEmitter.removeAllListeners('RNLANScanEndBroadcast');
            DeviceEventEmitter.removeAllListeners('RNLANScanEnd');
            DeviceEventEmitter.removeAllListeners('RNLANScanError');

            LANScan.listenerCount = 0;
        }

        if(LANScan.listenerCount === 0) {

            DeviceEventEmitter.addListener('RNLANScanStart', () => this.emit('start'));
            DeviceEventEmitter.addListener('RNLANScanStop', () => this.emit('stop'));
            DeviceEventEmitter.addListener('RNLANScanStartFetch', () => this.emit('start_fetch'));
            DeviceEventEmitter.addListener('RNLANScanInfoFetched', (info) => {
                if(!info || !info.ipAddress || !info.netmask) {
                    this.emit('fetch_error', "No Info fetched from the device wifi state");
                    return false;
                }

                this.emit('info_fetched', info);
            });
            DeviceEventEmitter.addListener('RNLANScanFetchError', (msg) => this.emit('fetch_error', msg));
            DeviceEventEmitter.addListener('RNLANScanStartPings', () => this.emit('start_pings'));
            DeviceEventEmitter.addListener('RNLANScanHostFoundPing', (host) => {

                if(!host)
                    return false;

                this._connectedHosts = [...this._connectedHosts, host];

                this.emit('host_found_ping', host, this._connectedHosts);
            });
            DeviceEventEmitter.addListener('RNLANScanEndPings', (number) => {

                if(number === null)
                    return false;

                this.emit('end_pings', number);
            });
            DeviceEventEmitter.addListener('RNLANScanPortOutOfRangeError', (msg) => this.emit('port_out_of_range_error', msg));
            DeviceEventEmitter.addListener('RNLANScanStartBroadcast', () => this.emit('start_broadcast'));
            DeviceEventEmitter.addListener('RNLANScanHostFound', (host) => {

                if(!host || !host.host || typeof host.host != "string" || host.port === null)
                    return false;

                if(this._availableHosts.hasOwnProperty(host.host)) {
                    if(this._availableHosts[host.host].indexOf(host.port) == -1) {
                        this._availableHosts[host.host].push(host.port);
                    }
                } else {
                    this._availableHosts[host.host] = [host.port];
                }

                this.emit('host_found', host, this._availableHosts);
            });
            DeviceEventEmitter.addListener('RNLANScanEndBroadcast', () => this.emit('end_broadcast'));
            DeviceEventEmitter.addListener('RNLANScanEnd', () => this.emit('end'));
            DeviceEventEmitter.addListener('RNLANScanError', (msg) => this.emit('error', msg));

        }


    }

    scan(min_port, max_port, broadcast_timeout = 500, fallback = true, ping_ms = 50, port_ms = 500) {
        RNLANScan.scan(min_port, max_port, broadcast_timeout, fallback, ping_ms, port_ms);
    }

    stop() {
        RNLANScan.stop();
    }

    fetchInfo() {
        RNLANScan.fetchInfo();
    }

    getConnectedHosts() {
        return this._connectedHosts;
    }

    getAvailableHosts() {
        return this._availableHosts;
    }



}