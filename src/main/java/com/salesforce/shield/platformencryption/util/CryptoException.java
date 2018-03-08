/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.shield.platformencryption.util;

public class CryptoException extends Exception{

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    public CryptoException(String s){
        super(s);
    }

    public CryptoException(Exception e){
        super(e);
    }

}