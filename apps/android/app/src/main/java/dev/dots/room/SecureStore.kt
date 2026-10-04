package dev.dots.room

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(private val context:Context,private val prefix:String="") {
 private val alias="dots-room-v1"+prefix
 private fun key():SecretKey {val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};return (ks.getKey(alias,null) as? SecretKey)?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()}
 @Synchronized fun save(name:String,text:String){require(text.toByteArray().size<=8*1024*1024);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());val bytes=byteArrayOf(c.iv.size.toByte())+c.iv+c.doFinal(text.toByteArray());val file=context.getFileStreamPath(prefix+name);val temp=java.io.File(file.path+".tmp");temp.outputStream().use{it.write(bytes)};check(temp.renameTo(file))}
 @Synchronized fun load(name:String):String?=try{val file=context.getFileStreamPath(prefix+name);if(!file.exists())null else {require(file.length()<=8*1024*1024+64);val b=file.readBytes();val n=b[0].toInt();require(n==12);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(1,n+1)));String(c.doFinal(b.copyOfRange(n+1,b.size)),Charsets.UTF_8)}}catch(_:Exception){context.deleteFile(prefix+name);null}
 fun clear(name:String){context.deleteFile(prefix+name)}
}
