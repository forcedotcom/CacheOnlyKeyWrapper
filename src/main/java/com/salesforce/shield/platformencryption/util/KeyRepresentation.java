/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.shield.platformencryption.util;

import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.lang.JoseException;

import java.security.PublicKey;

public class KeyRepresentation {

    private String kid;
    private byte[] key;
    private PublicKey wrappingKey;

    public KeyRepresentation(String kid, byte[] key, PublicKey wrappingKey) {
        this.kid = kid;
        this.key = key;
        this.wrappingKey = wrappingKey;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public void setWrappingKey(PublicKey wrappingKey) {
        this.wrappingKey = wrappingKey;
    }

    public String getKid() {
        return kid;
    }

    //Note: This is the critical piece of code that properly formats the JWE as expected by CacheOnlyKeys
    public String getJwe() {

        String compactSerialization = null;
        try {
            JsonWebEncryption jwe = new JsonWebEncryption();
            jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.RSA_OAEP);
            jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_256_GCM);
            jwe.setKeyIdHeaderValue(kid);
            jwe.setKey(wrappingKey);
            jwe.setPlaintext(key);
            compactSerialization = jwe.getCompactSerialization();
        } catch (JoseException e) {
            System.out.println(e);
        }
        return compactSerialization;
    }


    //Note: This is the critical piece of code that properly formats the JSON format that carries the kid and JWE
    public String toString() {
        StringBuffer jsonRepresentation = new StringBuffer("{");
        jsonRepresentation.append("\"kid\" : \"" + kid + "\",");
        jsonRepresentation.append("\"jwe\" : \"" + getJwe() + "\"}");
        return jsonRepresentation.toString();
    }


}
