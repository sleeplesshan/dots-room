package dev.dots.room

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** One validated base for WS, images and every authenticated HTTP operation. */
data class ConnectionProfile(val mode:String="USB",val usbPort:Int=8788,val tailscaleAddress:String="") {
 init { require(mode in listOf("USB","Tailscale"));require(usbPort in 1024..65535&&usbPort !in listOf(8765,8787)) }
 fun base():HttpUrl {
  if(mode=="USB")return "http://127.0.0.1:$usbPort/".toHttpUrl()
  val url=tailscaleAddress.trim().toHttpUrl()
  require(url.scheme=="https"&&url.username.isEmpty()&&url.password.isEmpty())
  require(url.host.endsWith(".ts.net")&&!url.host.startsWith(".")&&url.host.length>7)
  require(url.encodedPath=="/"&&url.query==null&&url.fragment==null)
  return url
 }
 fun http(path:String):String {require(Regex("/(healthz|v1/(events|weather|desk-state|capabilities|preset-messages|messages|sync|media/[a-f0-9]{64}))").matches(path));return requireNotNull(base().resolve(path)).toString()}
 fun events():String=http("/v1/events").replaceFirst(if(mode=="USB")"http:"else "https:",if(mode=="USB")"ws:"else "wss:")
}
