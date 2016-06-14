Object.defineProperty(exports,"__esModule",{value:true});exports.LANScan=undefined;var _createClass=function(){function defineProperties(target,props){for(var i=0;i<props.length;i++){var descriptor=props[i];descriptor.enumerable=descriptor.enumerable||false;descriptor.configurable=true;if("value" in descriptor)descriptor.writable=true;Object.defineProperty(target,descriptor.key,descriptor);}}return function(Constructor,protoProps,staticProps){if(protoProps)defineProperties(Constructor.prototype,protoProps);if(staticProps)defineProperties(Constructor,staticProps);return Constructor;};}();var _reactNative=require('react-native');var _reactNative2=_interopRequireDefault(_reactNative);
var _events=require('events');function _interopRequireDefault(obj){return obj&&obj.__esModule?obj:{default:obj};}function _toConsumableArray(arr){if(Array.isArray(arr)){for(var i=0,arr2=Array(arr.length);i<arr.length;i++){arr2[i]=arr[i];}return arr2;}else {return Array.from(arr);}}function _classCallCheck(instance,Constructor){if(!(instance instanceof Constructor)){throw new TypeError("Cannot call a class as a function");}}function _possibleConstructorReturn(self,call){if(!self){throw new ReferenceError("this hasn't been initialised - super() hasn't been called");}return call&&(typeof call==="object"||typeof call==="function")?call:self;}function _inherits(subClass,superClass){if(typeof superClass!=="function"&&superClass!==null){throw new TypeError("Super expression must either be null or a function, not "+typeof superClass);}subClass.prototype=Object.create(superClass&&superClass.prototype,{constructor:{value:subClass,enumerable:false,writable:true,configurable:true}});if(superClass)Object.setPrototypeOf?Object.setPrototypeOf(subClass,superClass):subClass.__proto__=superClass;}
_events.EventEmitter.defaultMaxListeners=Infinity;

var RNLANScan=_reactNative.NativeModules.RNLANScan;var 

LANScan=exports.LANScan=function(_EventEmitter){_inherits(LANScan,_EventEmitter);






function LANScan(props){_classCallCheck(this,LANScan);var _this=_possibleConstructorReturn(this,Object.getPrototypeOf(LANScan).call(this,
props));

_this._connectedHosts=[];
_this._availableHosts={};

if(LANScan.listenerCount&&LANScan.listenerCount>0){
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanStart');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanStop');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanStartFetch');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanInfoFetched');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanFetchError');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanStartPings');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanHostFoundPing');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanEndPings');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanPortOutOfRangeError');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanStartBroadcast');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanHostFound');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanEndBroadcast');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanEnd');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanBroadcastError');
_reactNative.DeviceEventEmitter.removeAllListeners('RNLANScanError');

LANScan.listenerCount=0;}


if(LANScan.listenerCount===0){

_reactNative.DeviceEventEmitter.addListener('RNLANScanStart',function(){return _this.emit('start');});
_reactNative.DeviceEventEmitter.addListener('RNLANScanStop',function(){return _this.emit('stop');});
_reactNative.DeviceEventEmitter.addListener('RNLANScanStartFetch',function(){return _this.emit('start_fetch');});
_reactNative.DeviceEventEmitter.addListener('RNLANScanInfoFetched',function(info){
if(!info||!info.ipAddress||!info.netmask){
_this.emit('fetch_error',"No Info fetched from the device wifi state");
return false;}


_this.emit('info_fetched',info);});

_reactNative.DeviceEventEmitter.addListener('RNLANScanFetchError',function(msg){return _this.emit('fetch_error',msg);});
_reactNative.DeviceEventEmitter.addListener('RNLANScanStartPings',function(){return _this.emit('start_pings');});
_reactNative.DeviceEventEmitter.addListener('RNLANScanHostFoundPing',function(host){

if(!host)
return false;

_this._connectedHosts=[].concat(_toConsumableArray(_this._connectedHosts),[host]);

_this.emit('host_found_ping',host,_this._connectedHosts);});

_reactNative.DeviceEventEmitter.addListener('RNLANScanEndPings',function(number){

if(number===null)
return false;

_this.emit('end_pings',number);});

_reactNative.DeviceEventEmitter.addListener('RNLANScanPortOutOfRangeError',function(msg){return _this.emit('port_out_of_range_error',msg);});
_reactNative.DeviceEventEmitter.addListener('RNLANScanStartBroadcast',function(){return _this.emit('start_broadcast');});
_reactNative.DeviceEventEmitter.addListener('RNLANScanHostFound',function(host){

if(!host||!host.host||typeof host.host!="string"||host.port===null)
return false;

if(_this._availableHosts.hasOwnProperty(host.host)){
if(_this._availableHosts[host.host].indexOf(host.port)==-1){
_this._availableHosts[host.host].push(host.port);}}else 

{
_this._availableHosts[host.host]=[host.port];}


_this.emit('host_found',host,_this._availableHosts);});

_reactNative.DeviceEventEmitter.addListener('RNLANScanEndBroadcast',function(){return _this.emit('end_broadcast');});
_reactNative.DeviceEventEmitter.addListener('RNLANScanEnd',function(){return _this.emit('end');});
_reactNative.DeviceEventEmitter.addListener('RNLANScanBroadcastError',function(msg){return _this.emit('broadcast_error',msg);});
_reactNative.DeviceEventEmitter.addListener('RNLANScanError',function(msg){return _this.emit('error',msg);});}return _this;}_createClass(LANScan,[{key:'scan',value:function scan(






min_port,max_port){var broadcast_timeout=arguments.length<=2||arguments[2]===undefined?500:arguments[2];var fallback=arguments.length<=3||arguments[3]===undefined?true:arguments[3];var ping_ms=arguments.length<=4||arguments[4]===undefined?50:arguments[4];var port_ms=arguments.length<=5||arguments[5]===undefined?500:arguments[5];
RNLANScan.scan(min_port,max_port,broadcast_timeout,fallback,ping_ms,port_ms);}},{key:'stop',value:function stop()


{
RNLANScan.stop();}},{key:'fetchInfo',value:function fetchInfo()


{
RNLANScan.fetchInfo();}},{key:'getConnectedHosts',value:function getConnectedHosts()


{
return this._connectedHosts;}},{key:'getAvailableHosts',value:function getAvailableHosts()


{
return this._availableHosts;}}]);return LANScan;}(_events.EventEmitter);LANScan.listenerCount=0;
