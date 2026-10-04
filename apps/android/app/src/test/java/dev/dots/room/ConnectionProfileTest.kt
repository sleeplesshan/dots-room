package dev.dots.room
import org.junit.Test
import org.junit.Assert.*
class ConnectionProfileTest {
 @Test fun everyRouteUsesOneBaseAndEventsUseWss(){val p=ConnectionProfile("Tailscale",8788,"https://fixture.tailnet.ts.net:8788/");for(route in listOf("/healthz","/v1/events","/v1/capabilities","/v1/preset-messages","/v1/sync","/v1/media/"+"a".repeat(64)))assertEquals("https://fixture.tailnet.ts.net:8788$route",p.http(route));assertEquals("wss://fixture.tailnet.ts.net:8788/v1/events",p.events())}
 @Test fun usbIsOnlyLoopback(){assertEquals("http://127.0.0.1:8788/v1/sync",ConnectionProfile().http("/v1/sync"));assertEquals("ws://127.0.0.1:18788/v1/events",ConnectionProfile(usbPort=18788).events())}
 @Test fun rejectsUnsafeAddressesAndRoutes(){for(address in listOf("http://example-host.tailnet.ts.net:8788","https://100.64.0.10:8788","https://evil.example","https://example-host.tailnet.ts.net/path","https://user:pass@example-host.tailnet.ts.net","https://example-host.tailnet.ts.net/?token=x","https://example-host.tailnet.ts.net/#x")){try{ConnectionProfile("Tailscale",8788,address).base();fail(address)}catch(_:IllegalArgumentException){}};for(path in listOf("//evil.ts.net","/v1/../../healthz","/v1/events?token=x","/v1/sync#x")){try{ConnectionProfile().http(path);fail(path)}catch(_:IllegalArgumentException){}}}
 @Test fun rejectsInvalidModesAndPorts(){for(port in listOf(0,80,65536))try{ConnectionProfile(usbPort=port);fail()}catch(_:IllegalArgumentException){};try{ConnectionProfile("Other");fail()}catch(_:IllegalArgumentException){}}
}
