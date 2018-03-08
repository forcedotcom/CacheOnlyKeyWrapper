/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.shield.platformencryption.util;

import org.apache.commons.codec.binary.Hex;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;


public class CryptoUtils {

    private static final int AES_BIT_LENGTH = 256;
    private static final String AES = "AES";
    private static final String CIPHER_SPEC = "AES/CBC/PKCS5Padding";



    public String encrypt(byte[]key, String cleartext) throws CryptoException {
        try {
            return encrypt(key,cleartext.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new CryptoException(e);
        }
    }

    public String encrypt(byte[]key, byte[] cleartext) throws CryptoException {


        try {
            // Generating IV.
            int ivSize = 16;
            byte[] iv = new byte[ivSize];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            // Encrypt.
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, AES);
            Cipher cipher = Cipher.getInstance(CIPHER_SPEC);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] encrypted = cipher.doFinal(cleartext);

            // Combine IV and encrypted part.
            byte[] encryptedIVAndText = new byte[ivSize + encrypted.length];
            System.arraycopy(iv, 0, encryptedIVAndText, 0, ivSize);
            System.arraycopy(encrypted, 0, encryptedIVAndText, ivSize, encrypted.length);
            return Hex.encodeHexString(encryptedIVAndText);

        } catch (Exception e) {
            throw new CryptoException(e);
        }

    }

    public String decrypt(byte[]key, String hexEncodedCiphertext) throws CryptoException {

        try {
            byte[] cipherTextBytes = Hex.decodeHex(hexEncodedCiphertext);

            int ivSize = 16;
            int keySize = 16;

            // Extract IV.
            byte[] iv = new byte[ivSize];
            System.arraycopy(cipherTextBytes, 0, iv, 0, iv.length);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            // Extract encrypted part.
            int encryptedSize = cipherTextBytes.length - ivSize;
            byte[] encryptedBytes = new byte[encryptedSize];
            System.arraycopy(cipherTextBytes, ivSize, encryptedBytes, 0, encryptedSize);

            // Decrypt.
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, AES);
            Cipher cipherDecrypt = Cipher.getInstance(CIPHER_SPEC);
            cipherDecrypt.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] decrypted = cipherDecrypt.doFinal(encryptedBytes);

            return new String(decrypted);

        } catch (Exception e) {
            throw new CryptoException(e);
        }

    }


    public byte[] generateAESKey() throws CryptoException {

        KeyGenerator keygen;
        try {
            keygen = KeyGenerator.getInstance(AES);
        } catch (NoSuchAlgorithmException e) {

            throw new CryptoException(e);
        }

        keygen.init(AES_BIT_LENGTH);
        SecretKey key = keygen.generateKey();
        return key.getEncoded();

    }



}
