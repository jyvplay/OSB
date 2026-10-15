package dev.fusion.vault

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Bounded authenticated binary envelope. Caller owns a random32-byte key and its protection.
 * No password KDF, Keystore binding, key persistence, rollback defense, secure deletion or Vault unlock.
 */
object VaultEnvelope {
    const val MAX_PLAINTEXT=1024*1024
    private const val MAGIC=0x46564531
    private val random=SecureRandom()
    fun seal(plaintext:ByteArray,key:ByteArray,context:ByteArray=byteArrayOf()):ByteArray {
        require(plaintext.size<=MAX_PLAINTEXT && key.size==32 && context.size<=1024)
        val nonce=ByteArray(12).also(random::nextBytes)
        val header=ByteBuffer.allocate(20).putInt(MAGIC).putInt(plaintext.size).put(nonce).array()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key.copyOf(),"AES"),GCMParameterSpec(128,nonce))
        cipher.updateAAD(header);cipher.updateAAD(context.copyOf())
        return header+cipher.doFinal(plaintext)
    }
    fun open(frame:ByteArray,key:ByteArray,context:ByteArray=byteArrayOf()):ByteArray {
        require(frame.size in 36..MAX_PLAINTEXT+36 && key.size==32 && context.size<=1024)
        val buffer=ByteBuffer.wrap(frame);require(buffer.int==MAGIC)
        val size=buffer.int;require(size in 0..MAX_PLAINTEXT && frame.size==size+36)
        val nonce=ByteArray(12).also(buffer::get)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key.copyOf(),"AES"),GCMParameterSpec(128,nonce))
        cipher.updateAAD(frame,0,20);cipher.updateAAD(context.copyOf())
        // doFinal returns nothing to the caller until tag verification succeeds.
        return cipher.doFinal(frame,20,frame.size-20).also { require(it.size==size) }
    }
}
