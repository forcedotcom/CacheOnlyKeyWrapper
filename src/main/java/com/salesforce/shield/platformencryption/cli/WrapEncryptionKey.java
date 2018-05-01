/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.shield.platformencryption.cli;

import com.salesforce.shield.platformencryption.util.CryptoException;
import com.salesforce.shield.platformencryption.util.CryptoUtils;
import com.salesforce.shield.platformencryption.util.KeyRepresentation;
import com.salesforce.shield.platformencryption.util.SSSUtils;
import org.apache.commons.cli.*;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;

public class WrapEncryptionKey {

    public static void main(String[] args) {

        PublicKey publicWrappingKey = null;
        String kid = null;
        boolean scriptGeneratedKey = true;
        byte[] byokKey = new byte[0];
        CryptoUtils cryptoUtils = new CryptoUtils();

        //Shamir Secret Spliting defaults
        int n = 3;
        int k = 2;

        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption( "h","help", false, "Help for WrapEncryptionKey" );
        options.addOption( "c", "cert", true, "Path to Certificate File (required)" );
        options.addOption( "b", "bytes", true, "Hex Encoded BYOK AES KEY (optional)" );
        options.addOption( "i", "kid", true, "Key Identifier (optional)" );
        options.addOption( "s", "split",false, "Protect Encryption key with Shamir's Secret Sharing (optional)" );
        options.addOption( "n", "num",true, "Number of N parts for Shamir's Secret Sharing (optional)" );
        options.addOption( "k", "know",true, "Knowledge of K pieces for Shamir's Secret Sharing (optional)" );

        try {

            CommandLine line = parser.parse( options, args );

            if(line.hasOption( "help" ) || line.getOptions().length == 0 ) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "WrapEncryptionKey", options );
            } else {
                if( line.hasOption( "c" ) ) {
                    //Parse the public key used for RSA-OAEP wrapping
                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                    FileInputStream inputStream = new FileInputStream(line.getOptionValue( "c" ));
                    X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(inputStream);
                    publicWrappingKey = certificate.getPublicKey();
                } else throw new ParseException("Please specifiy a BYOK wrapping certificate in PEM forat with the -c option.");

                if( line.hasOption( "i" ) ) {
                    //User specified kid
                    kid = line.getOptionValue( "i" );
                } else {
                    //or generate a random UUID for a kid
                    kid = UUID.randomUUID().toString();
                }

                if( line.hasOption( "b" ) ) {
                    //User specified a hex encoded AES key
                    scriptGeneratedKey = false;
                    String hexEncodedKey = line.getOptionValue( "b" );
                    try {
                        byokKey = Hex.decodeHex(hexEncodedKey);
                        if (byokKey.length != 32) throw new CryptoException("You must specify a Hex encoded 256 bit AES key");
                        System.out.println("Wrapping user specfied AES key");
                    } catch (DecoderException e) {
                        throw new CryptoException("Unable to hex decode AES key", e);
                    }

                } else {
                    //or we generate an AES key for them
                    System.out.println("Generating 256 bit AES Key");
                    byokKey = cryptoUtils.generateAESKey();

                    if( line.hasOption( "s" ) ) {
                        if(!line.hasOption("n") || !line.hasOption("k")) throw new ParseException("If using secret splitting, please specify valued for -n and -k.");

                        //Check to see if we're using secret splitting and if so get the parts
                        n = Integer.parseInt(line.getOptionValue("n"));
                        if ((n > 10) || (n < 2))
                            throw new ParseException("Lets keep things simple.  Please choose a value of n between 2-10");

                        k = Integer.parseInt(line.getOptionValue("k"));
                        if (k <= 1)
                            throw new ParseException("There's no point in secret splitting if you don't require knowledge of more than 1 parts.  Choose a better value for k please.");
                        if (k > n)
                            throw new ParseException("You can't have more parts than pieces! Choose a better value for k that's <= n.");

                    }

                }

                //Write the wrapped key to a file
                KeyRepresentation keyRepresentation = new KeyRepresentation(kid, byokKey, publicWrappingKey);
                FileOutputStream keyRepresentationFile = new FileOutputStream(kid);
                keyRepresentationFile.write(keyRepresentation.toString().getBytes(StandardCharsets.UTF_8));
                keyRepresentationFile.close();

                System.out.println("");
                System.out.println("Cache-Only Key representation written to file: " + kid);
                System.out.println("");

                if (scriptGeneratedKey) {
                    //we generated the key, so need to tell the user about the key
                    String kexEncodedKey = Hex.encodeHexString(byokKey);

                    if( line.hasOption( "s" ) ) {
                        //User wants the key split using Shamir's
                        SSSUtils sssUtils = new SSSUtils(n,k);
                        String[] parts = sssUtils.split(kexEncodedKey.getBytes(StandardCharsets.UTF_8));
                        System.out.println("Encryption Key can be recovered with " + k + " of the following " + n + " parts:");
                        for(String value : parts) {
                            System.out.println(value);
                        }

                        System.out.println("");
                        System.out.println("For example, you can use the following command: ");
                        System.out.println("./RecoverSplitEncryptionKey -n " + n + " -k " + k + " -p " + parts[2] + " " + parts[0]);
                        System.out.println("");

                    } else {
                        //user just wants the hex encoded key
                        System.out.println("Hex encoded encryption key: " + kexEncodedKey);

                    }

                }

            }

        } catch( ParseException e ) {
            System.out.println( "ERROR: " + e.getMessage() );
        } catch (CryptoException e) {
            System.out.println( "ERROR: " + e.getMessage() );
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
