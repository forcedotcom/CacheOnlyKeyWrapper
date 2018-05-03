/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.shield.platformencryption.cli;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.GenerateDataKeyRequest;
import com.amazonaws.services.kms.model.GenerateDataKeyResult;
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

public class WrapAWSEncryptionKey {

    public static void main(String[] args) {

        PublicKey publicWrappingKey = null;
        String kid = null;
        CryptoUtils cryptoUtils = new CryptoUtils();

        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption( "h","help", false, "Help for WrapAWSEncryptionKey" );
        options.addOption( "c", "cert", true, "Path to Certificate File (required)" );
        options.addOption( "i", "kid", true, "Key Identifier (optional)" );
        options.addOption( "r", "region", true, "AWS Region (required)" );
        options.addOption( "ak", "accesskey", true, "AWS Access Key (required)" );
        options.addOption( "sk", "secretkey", true, "AWS Secret Key (required)" );
        options.addOption( "a", "alias", true, "AWS CMK Alias (required)" );

        try {

            CommandLine line = parser.parse( options, args );

            if(line.hasOption( "help" ) || line.getOptions().length == 0 || !line.hasOption( "c" ) || !line.hasOption( "r" ) || !line.hasOption( "ak" )|| !line.hasOption( "sk" )|| !line.hasOption( "a" )) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "WrapAWSEncryptionKey", options );
            } else {

                if( line.hasOption( "i" ) ) {
                    //User specified kid
                    kid = line.getOptionValue( "i" );
                } else {
                    //or generate a random UUID for a kid
                    kid = UUID.randomUUID().toString();
                }

                //Parse the public key used for RSA-OAEP wrapping
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                FileInputStream inputStream = new FileInputStream(line.getOptionValue( "c" ));
                X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(inputStream);
                publicWrappingKey = certificate.getPublicKey();


                //AWS Credentials
                String AWS_ACCESS_KEY = line.getOptionValue( "ak" );
                String AWS_SECRET_KEY = line.getOptionValue( "sk" );
                String AWS_REGION = line.getOptionValue( "r" );
                String AWS_ALIAS = line.getOptionValue( "a" );


                //or we generate an AES key for them
                System.out.println("Calling AWS KMS to generate a new 256bit AES Key with Customer Master Key: " + AWS_ALIAS);
                BasicAWSCredentials creds = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY);
                AWSKMS kmsClient = AWSKMSClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(creds)).withRegion(AWS_REGION).build();
                GenerateDataKeyRequest request = new GenerateDataKeyRequest().withKeyId("alias/" + AWS_ALIAS).withKeySpec("AES_256");
                GenerateDataKeyResult response = kmsClient.generateDataKey(request);
                String cipherText = Hex.encodeHexString(response.getCiphertextBlob());
                System.out.println("Generated KMS KeyId: " + response.getKeyId());

                //Write the wrapped key to a file
                KeyRepresentation keyRepresentation = new KeyRepresentation(kid, response.getPlaintext().array(), publicWrappingKey);
                FileOutputStream keyRepresentationFile = new FileOutputStream(kid);
                keyRepresentationFile.write(keyRepresentation.toString().getBytes(StandardCharsets.UTF_8));
                keyRepresentationFile.close();

                System.out.println("Cache-Only Key representation written to file: " + kid);

                FileOutputStream backupFile = new FileOutputStream(kid + ".backup");
                StringBuffer backup = new StringBuffer();
                backup.append("KeyId:");
                backup.append(response.getKeyId());
                backup.append("\nHex Encoded Encrypted Backup from KMS: ");
                backup.append(cipherText);
                backupFile.write(backup.toString().getBytes(StandardCharsets.UTF_8));
                backupFile.close();

                System.out.println("Encrypted backup of KMS generated key written to file: " + kid+ ".backup");

            }

        } catch( ParseException e ) {
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
