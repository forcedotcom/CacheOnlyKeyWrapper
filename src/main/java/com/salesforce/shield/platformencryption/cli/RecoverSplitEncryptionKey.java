/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.shield.platformencryption.cli;

import com.salesforce.shield.platformencryption.util.SSSUtils;
import org.apache.commons.cli.*;
import org.apache.commons.codec.DecoderException;

import java.nio.charset.StandardCharsets;

public class RecoverSplitEncryptionKey {


    public static void main(String[] args) {

        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption( "h",false, "Help for SplitSecret" );
        options.addOption( "n", true, "Number of N parts" );
        options.addOption( "k", true, "Knowledge of K pieces" );
        Option option = new Option("p", true, "List of K Parts" );
        option.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(option);

        try {
            CommandLine line = parser.parse( options, args );
            if(line.hasOption( "help" ) || line.getOptions().length == 0  || !line.hasOption( "n" ) || !line.hasOption( "k" )|| !line.hasOption( "p" ) ) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "RecoverSplitEncryptionKey", options );
            } else {

                int n = Integer.parseInt(line.getOptionValue("n"));
                int k = Integer.parseInt(line.getOptionValue("k"));
                String[] parts = line.getOptionValues("p");
                SSSUtils sssUtil = new SSSUtils(n, k);
                final byte[] recovered = sssUtil.recover(parts);
                System.out.println("Hex encoded encryption key: "  + new String(recovered, StandardCharsets.UTF_8));

            }


        }
        catch( ParseException e ) {
            System.out.println( "Error: " + e.getMessage() );
        } catch (DecoderException e) {
            e.printStackTrace();
        }


    }

}
