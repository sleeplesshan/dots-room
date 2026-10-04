package dev.dots.room

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** Only default-network identity, transports and reachability; no SSIDs or credentials. */
class DefaultNetworkObserver(context:Context,changed:()->Unit) {
 private val manager=context.getSystemService(ConnectivityManager::class.java)
 private var current:Network?=null
 private var signature:String?=null
 private val callback=object:ConnectivityManager.NetworkCallback(){
  private fun publish(network:Network,value:String){val different=synchronized(this@DefaultNetworkObserver){
   if(current==network&&signature==value)false else {current=network;signature=value;true}
  };if(different)changed()}
  override fun onAvailable(network:Network)=publish(network,"available")
  override fun onLost(network:Network){val lost=synchronized(this@DefaultNetworkObserver){if(current!=network)false else{current=null;signature=null;true}};if(lost)changed()}
  override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities){
   val transports=(0..6).filter{capabilities.hasTransport(it)}.joinToString(",")
   publish(network,"$transports:${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}:${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
  }
 }
 init {manager.registerDefaultNetworkCallback(callback)}
 fun close(){manager.unregisterNetworkCallback(callback)}
}
