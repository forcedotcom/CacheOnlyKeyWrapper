/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.shield.platformencryption.util;

import com.codahale.shamir.Scheme;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.util.HashMap;
import java.util.Map;

public class SSSUtils {

    private Scheme scheme;

    public SSSUtils(int n, int k) {
        this.scheme =  Scheme.of(n, k);
    }

    public String[] split(byte[] secret) {

        final Map<Integer, byte[]> parts = scheme.split(secret);
        String[] structuredParts = new String[scheme.n()];
        for (int part : parts.keySet()) {
            int index = part-1;
            String secretPart =  Hex.encodeHexString(parts.get(part));
            String structuredPart = index + secretPart;
            structuredParts[index] = structuredPart;
        }
        return structuredParts;

    }

    public byte[] recover(String[] parts) throws DecoderException {

        Map<Integer, byte[]> providedParts =  new HashMap<Integer, byte[]>();
        for(int i = 0; i < parts.length; i++) {
            String pos = parts[i].substring(0,1);
            String secretPart = parts[i].substring(1);
            providedParts.put(Integer.parseInt(pos) + 1,Hex.decodeHex(secretPart));
        }
        return scheme.join(providedParts);

    }

}
