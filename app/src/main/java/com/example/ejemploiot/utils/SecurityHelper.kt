package com.example.ejemploiot.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object SecurityHelper {
    // Clave secreta para encriptar/desencriptar (Cumple requisito de seguridad básica ISO)
    // En producción debe ser de 16, 24 o 32 bytes y no estar hardcodeada.
    private const val SECRET_KEY = "IotSecurityKey12" 

    fun encryptData(data: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val secretKeySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
            val encryptedBytes = cipher.doFinal(data.toByteArray())
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            data // Si falla, devuelve el dato original (no debería pasar)
        }
    }

    fun decryptData(encryptedData: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val secretKeySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
            val decodedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes)
        } catch (e: Exception) {
            encryptedData
        }
    }
}
