# CacheOnlyKeyWrapper

## Overview

In Summer 18, Salesforce's Shield Platform Encryption is introducing a new pilot feature called Cache-Only Keys.  This capability enhances the existing Bring Your Own Key (BYOK) capability by allowing customers to host their key material in a wrapped format which Salesforce will fetch as required.  While this will be cached in an encrypted form, Salesforce will not retain or persist the key material in any system of record or backups. 

When a key is needed by Salesforce for crypto operations, a callout is made directly to the customer's registered service to request the key.  The key exchange protocol and format requires that Keys are wrapped in a specific JSON format.  This project provides a sample utility to perform that wrapping, allowing customers to wrap their keys for hosting or provide the basis for understanding of how to build their own key service if desired.


## Expected Response

Salesforce is expecting a 256bit AES key to be returned in a JSON response, and wrapped using JSON Web Encryption ([JWE](https://tools.ietf.org/html/rfc7516)).   The following specifies the overall response format, as well as the expected JWE format

### The Response Format 

Keys are wrapped in a JSON format.  For example here is a valid response:

```javascript
{
  "kid": "982c375b-f46b-4423-8c2d-4d1a69152a0b",
  "jwe": "eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00iLCJraWQiOiI5ODJjMzc1Yi1mNDZiLTQ0MjMtOGMyZC00ZDFhNjkxNTJhMGIifQ.NsXFnbM6uis-XB4CnRQLZH7odoUucTD9bTKHh4YiEN__KqNZCSfXsic1kYf6HRiM3gtZJQkN_xcVcUQtkXP9Yo0qC9FCNyA0mg3yuNVnD2Qhjh7J1Waox3xQoVLQz-Zn4L0-kYJqNL_BWgZAp-KCGW1oO-W2BYdxmFuv5lE3wQj-ESJRLoMtujhrvPnMPOaq9pRixYdQnlZiOqvKNGdC6QyadINeKO4ZnuxIkjDM7XBq_RVxZZDcs0KX7tePhMqbg9GbsETdzTfypalUykSs_5bUxXE271lD-EInt5_K1SUC6etVukLb3Xr-dLUcz2cygnIOdhcVFhUkQiLP14ofzrJcLsTxcnghV4dtfu4Cwgb4gW73eP2akcxWC36jqGnUoYezSafen5Px7ow0vMVsnYmhmaANaORfHW6sP_03t6kcrry0-qOBoSi53AOBWPNxLp15ZGtdNOAJGv_lMXD9j3J0ipMTkjS7mH_8pWOn7Zxiamn2VxwdAj_4t6KrrE2Wvi3y84il6vgWUfdFAP2N62FqsePkOrIVOZ9dF6ZrsD7pN6Zk7g0sCOI2gFGve_2bTOXe880U_Saj2vw8TgdmgQ2Pera6_vaYJ5Xq4uXWVFbdVM9sNrtkB_Pz1y7uKFSUFVJQ2OKMSHdTyWbqjW_jFcQ7wet504fEvUT8ObePB6k.UActtRs7Vgs8nJ41.vRCeIECmk5FOcq9kUxCbxC_d6e9msaWRRruBsrVQGMg.4yAG_BeaHCIyLpv22hZLTA"
}
```

**Response Claims**

* **"kid"** The unique ID of the key as defined by the customer.  This will be registered with Salesforce, and will be the resource requested by Salesforce when the key is required.  Allowed characters are  "a-z A-Z 0-9 . - _"   Valid examples might be a number "10", a string "2018_data_key", a UUID "982c375b-f46b-4423-8c2d-4d1a69152a0b".  
* **"jwe"** The AES key wrapped in a JWE as specified below 


### The JWE Format 

If you're not familiar with JWE, it stands for JSON Web Encryption and represents encrypted content using JSON-based data structures.  You can read more about it in [RFC7516](https://tools.ietf.org/html/rfc7516)

While JWE allows for a variety of cryptographic algorithms and respresentations, Shield Platform Encryption only allows for a specific use of JWE, ensuring clear usage guidlines and appropriate protections for customer keys.  Opinionated crypto is a good thing (assuming you like our opinion)   Specificaly we are using a JWE with RSAES-OAEP and AES GCM as illustrated in [Appendix A1 of the RFC](https://tools.ietf.org/html/rfc7516#appendix-A.1)                                  

#### Prerequisites

**Generate a Data Encryption Key**

As the entire point of Cache-Only Keys is to allow customers to bring their own data encryption key, the JWE format starts with an 256bit AES key as the payload which will be protected.  We will refer to this as the Data Encruuption Key or DEK.  It is the customer's responsbility to generate this DEK, and do so in a cryptographically secure fashion.    

**Generate a Content Encryption Key**

To wrap the DEK appropriately we'll first generate a content encryption key.  This is a 256 bit AES key, and it will be used to wrap the DEK.  It is the customer's responsbility to generate this CEK, and do so in a cryptographically secure fashion.   

**Generate a BYOK Certificate**

With the DEK and CEK in place, we'll first protect the CEK so it can be passed to Salesforce.  Since AES encryption uses a symmetric key, we'll be wrapping this using RSA with the BYOK Wrapping Key which is downloaded from Salesforce.  Generate and download your BYOK Certificate here:  https://help.salesforce.com/articleView?id=security_pe_byok_generate_cert.htm&type=5

#### Construct your JWE

Next, you'll actually generate your JWE.  This involves creating the header, wrapping the CEK with your BYOK Certificate, wrapping your DEK with your CEK wrap your CEK, and assembling it all into the JWE format.   

The JWE Compact Serialization is represented as follows:

BASE64URL(UTF8(JWE Protected Header)) || '.' ||
BASE64URL(RSA-OAEP Encrypted CEK using the public key of the BYOK Cert) || '.' ||
BASE64URL(Initialization Vector) || '.' ||
BASE64URL(DEK Encrypted with CEK using AES GCM) || '.' ||
BASE64URL( Authentication Tag)


**First, Create the JWE Protected Header**

The header is a JSON document that has 3 claims

* **"alg"** The algorithm used to encrypt the CEK.   We only support the value RSA-OAEP
* **"enc"** The algorithm used to encrypt the DEK.   We only support the value A256GCM
* **"kid"** The unique ID of the key as defined by the customer.  This MUST match the resource that was requested and the "kid" claim in the overall JSON response from the key service

For example, for a Key ID of "982c375b-f46b-4423-8c2d-4d1a69152a0b" the resulting header would be:

```javascript
{"alg":"RSA-OAEP","enc":"A256GCM","kid":"982c375b-f46b-4423-8c2d-4d1a69152a0b"}
```

Enoding this JWE Protected Header as BASE64URL(UTF8(JWE Protected Header)) gives this value:

```
eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00iLCJraWQiOiI5ODJjMzc1Yi1mNDZiLTQ0MjMtOGMyZC00ZDFhNjkxNTJhMGIifQ
```

**Then, Wrap the Content Encryption Key**

The Content Encryption Key is then wrapped for transmission to Salesforce in the JWE.  Encrypt the CEK with the public key from the BYOK Certificate using the RSAES-OAEP algorithm.  Then this encrypted CEK as BASE64URL(Encrypted CEK)

The result might look like this:

```
l92QA-R7b6Gtjo0tG4GlylJti1-Pf-519YpStYOp28YToMxgUxPmx4NR_myvfT24oBCWkh6hy_dqAL7JlVO449EglAB_i9GRdyVbTKnJQ1OiVKwWUQaZ9jVNxFFUYTWWZ-sVK4pUw0B3lHwWBfpMsl4jf0exP5-5amiTZ5oP0rkW99ugLWJ_7XlyTuMIA6VTLSpL0YqChH1wQjo12TQaWG_tiTwL1SgRd3YohuMVlmCdEmR2TfwTvryLPx4KbFK3Pv5ZSpSIyreFTh12DPpmhLEAVhCBZxR4-HMnZySSs4QorWagOaT8XPjPv46m8mUATZSD4hab8v3Mq4H33CmwngZCJXX-sDHuax2JUejxNC8HT5p6sa_I2gQFMlBC2Sd4yBKyjlDQKcSslCVav4buG8hkOJXY69iW_zhztV3DoJJ90l-EvkMoHpw1llU9lFhJMUQRvvocfghs2kzy5QC8QQt4t4Wu3p7IvzeneL5I81QjQlDJmZhbLLorFHgcAs9_FMwnFYFrgsHP1_v3Iqy7zJJc60fCfDaxAF8Txj_LOeOMkCFl-9PwrULWyRTLMI7CdZIm7jb8v9ALxCmDgqUi1yvEeBJhgMLezAWtxvGGkejc0BdsbWaPFXlI3Uj7C-Mw8LcmpSLKZyEnhj2x-3Vfv5hIVauC6ja1B6Z_UcqXKOc
```

**Next, Generate an Initialization Vector**

Generate a random Initialization Vector to be used as input to the AES wrapping of the DEK.  Encode this Initialization Vector as BASE64URL(Initialization Vector) 

The result might look like this:


```
N2WVMbpAxipAtG9O
```


**Now, Encrypt your Data Encryption Key**

Your DEK will be encrypted with your CEK.  This uses AES GCM, which has the concept of additional integrity protection, or Additional Authenticated Data.  In this case the JWE header.

So, prepare your Additional Authenticated Data by encoding the header as ASCII(BASE64URL(UTF8(JWE Protected Header)))

The, erform authenticated encryption on the DEK with the AES GCM algorithm using the CEK as the encryption key, the Initialization Vector, and the Additional Authenticated Data value above, requesting a 128-bit Authentication Tag output.

Encode the resulting ciphertext as BASE64URL(Ciphertext) and encode the Authentication Tag as BASE64URL(Authentication Tag).   This might result as:

```
63wRVVKX0ZOxu8cKqN1kqN-7EDa_mnmk32DinS_zFo4
```

and 

```
HC7Ev5lmsbTgwyGpeGH5Rw
```

**Finally, assemble your JWE**

Assemble the final representation as a Compact Serialization of all of these values.   This is done be concating each of the values together seperated by "."  Using the examples above you'd arrive at:

```
eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00iLCJraWQiOiI5ODJjMzc1Yi1mNDZiLTQ0MjMtOGMyZC00ZDFhNjkxNTJhMGIifQ.l92QA-R7b6Gtjo0tG4GlylJti1-Pf-519YpStYOp28YToMxgUxPmx4NR_myvfT24oBCWkh6hy_dqAL7JlVO449EglAB_i9GRdyVbTKnJQ1OiVKwWUQaZ9jVNxFFUYTWWZ-sVK4pUw0B3lHwWBfpMsl4jf0exP5-5amiTZ5oP0rkW99ugLWJ_7XlyTuMIA6VTLSpL0YqChH1wQjo12TQaWG_tiTwL1SgRd3YohuMVlmCdEmR2TfwTvryLPx4KbFK3Pv5ZSpSIyreFTh12DPpmhLEAVhCBZxR4-HMnZySSs4QorWagOaT8XPjPv46m8mUATZSD4hab8v3Mq4H33CmwngZCJXX-sDHuax2JUejxNC8HT5p6sa_I2gQFMlBC2Sd4yBKyjlDQKcSslCVav4buG8hkOJXY69iW_zhztV3DoJJ90l-EvkMoHpw1llU9lFhJMUQRvvocfghs2kzy5QC8QQt4t4Wu3p7IvzeneL5I81QjQlDJmZhbLLorFHgcAs9_FMwnFYFrgsHP1_v3Iqy7zJJc60fCfDaxAF8Txj_LOeOMkCFl-9PwrULWyRTLMI7CdZIm7jb8v9ALxCmDgqUi1yvEeBJhgMLezAWtxvGGkejc0BdsbWaPFXlI3Uj7C-Mw8LcmpSLKZyEnhj2x-3Vfv5hIVauC6ja1B6Z_UcqXKOc.N2WVMbpAxipAtG9O.63wRVVKX0ZOxu8cKqN1kqN-7EDa_mnmk32DinS_zFo4.HC7Ev5lmsbTgwyGpeGH5Rw
```


### Putting it all together

You'll now host this wrapped key inside of the key reponse at a location Salesforce can request.  To secure this, Cache-Only Keys leverage a feature called [Named Credentials](https://developer.salesforce.com/docs/atlas.en-us.apexcode.meta/apexcode/apex_callouts_named_credentials.htm) 

Named Credentials provide customers a way to make secure web service calls that leverage popular authentication formats.  All calls that go through the Named Credential are wrapped transparently in the authentication and security layers that are declared in the connection.  

Register a named credential with the URL of your key service or the service that will be hosting the wrapped file.  Provide this file at the path that is relative to your registered URL and matches the ID of your key.  

For example, if you registered https://byok.customer.com/keys/ as your named credential, using Basic Authentication with a username and password of  Aladdin:OpenSesame, and a Key Identifier of 982c375b-f46b-4423-8c2d-4d1a69152a0b, then Salesforce would make a web service request to 

```
https://byok.customer.com/keys/982c375b-f46b-4423-8c2d-4d1a69152a0b
```

and expect the following response (using the examples above...your JWE would of course be different):

```
{
  "kid" : "982c375b-f46b-4423-8c2d-4d1a69152a0b",
  "jwe" : "eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00iLCJraWQiOiI5ODJjMzc1Yi1mNDZiLTQ0MjMtOGMyZC00ZDFhNjkxNTJhMGIifQ.l92QA-R7b6Gtjo0tG4GlylJti1-Pf-519YpStYOp28YToMxgUxPmx4NR_myvfT24oBCWkh6hy_dqAL7JlVO449EglAB_i9GRdyVbTKnJQ1OiVKwWUQaZ9jVNxFFUYTWWZ-sVK4pUw0B3lHwWBfpMsl4jf0exP5-5amiTZ5oP0rkW99ugLWJ_7XlyTuMIA6VTLSpL0YqChH1wQjo12TQaWG_tiTwL1SgRd3YohuMVlmCdEmR2TfwTvryLPx4KbFK3Pv5ZSpSIyreFTh12DPpmhLEAVhCBZxR4-HMnZySSs4QorWagOaT8XPjPv46m8mUATZSD4hab8v3Mq4H33CmwngZCJXX-sDHuax2JUejxNC8HT5p6sa_I2gQFMlBC2Sd4yBKyjlDQKcSslCVav4buG8hkOJXY69iW_zhztV3DoJJ90l-EvkMoHpw1llU9lFhJMUQRvvocfghs2kzy5QC8QQt4t4Wu3p7IvzeneL5I81QjQlDJmZhbLLorFHgcAs9_FMwnFYFrgsHP1_v3Iqy7zJJc60fCfDaxAF8Txj_LOeOMkCFl-9PwrULWyRTLMI7CdZIm7jb8v9ALxCmDgqUi1yvEeBJhgMLezAWtxvGGkejc0BdsbWaPFXlI3Uj7C-Mw8LcmpSLKZyEnhj2x-3Vfv5hIVauC6ja1B6Z_UcqXKOc.N2WVMbpAxipAtG9O.63wRVVKX0ZOxu8cKqN1kqN-7EDa_mnmk32DinS_zFo4.HC7Ev5lmsbTgwyGpeGH5Rw"
}
```

More specifically, here's a sample HTTP request / response

```
GET /keys/982c375b-f46b-4423-8c2d-4d1a69152a0b HTTP/1.1
Host: byok.customer.com
Authorization: Basic QWxhZGRpbjpPcGVuU2VzYW1l
```

```
HTTP/1.1 200 OK
{
  "kid" : "982c375b-f46b-4423-8c2d-4d1a69152a0b",
  "jwe" : "eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00iLCJraWQiOiI5ODJjMzc1Yi1mNDZiLTQ0MjMtOGMyZC00ZDFhNjkxNTJhMGIifQ.l92QA-R7b6Gtjo0tG4GlylJti1-Pf-519YpStYOp28YToMxgUxPmx4NR_myvfT24oBCWkh6hy_dqAL7JlVO449EglAB_i9GRdyVbTKnJQ1OiVKwWUQaZ9jVNxFFUYTWWZ-sVK4pUw0B3lHwWBfpMsl4jf0exP5-5amiTZ5oP0rkW99ugLWJ_7XlyTuMIA6VTLSpL0YqChH1wQjo12TQaWG_tiTwL1SgRd3YohuMVlmCdEmR2TfwTvryLPx4KbFK3Pv5ZSpSIyreFTh12DPpmhLEAVhCBZxR4-HMnZySSs4QorWagOaT8XPjPv46m8mUATZSD4hab8v3Mq4H33CmwngZCJXX-sDHuax2JUejxNC8HT5p6sa_I2gQFMlBC2Sd4yBKyjlDQKcSslCVav4buG8hkOJXY69iW_zhztV3DoJJ90l-EvkMoHpw1llU9lFhJMUQRvvocfghs2kzy5QC8QQt4t4Wu3p7IvzeneL5I81QjQlDJmZhbLLorFHgcAs9_FMwnFYFrgsHP1_v3Iqy7zJJc60fCfDaxAF8Txj_LOeOMkCFl-9PwrULWyRTLMI7CdZIm7jb8v9ALxCmDgqUi1yvEeBJhgMLezAWtxvGGkejc0BdsbWaPFXlI3Uj7C-Mw8LcmpSLKZyEnhj2x-3Vfv5hIVauC6ja1B6Z_UcqXKOc.N2WVMbpAxipAtG9O.63wRVVKX0ZOxu8cKqN1kqN-7EDa_mnmk32DinS_zFo4.HC7Ev5lmsbTgwyGpeGH5Rw"
}
```

### Using this Utility 

---

Run `mvn package` to build the application

Run the WrapEncryptionKey utility in the ./bin directory.   It will give you description of it's options as follows 

```
$ ./WrapEncryptionKey 
usage: WrapEncryptionKey
 -b,--bytes <arg>   Path to Hex Encoded BYOK AES KEY (optional)
 -c,--cert <arg>    Path to Certificate File (required)
 -h,--help          Help for WrapEncryptionKey
 -i,--kid <arg>     Key Identifier (optional)
 -k,--know <arg>    Knowledge of K pieces for Shamir's Secret Sharing
                    (optional)
 -n,--num <arg>     Number of N parts for Shamir's Secret Sharing
                    (optional)
 -s,--split         Protect Encryption key with Shamir's Secret Sharing
                    (optional)

```


Or, run the WrapAWSEncryptionKey utility which allows for you to generate keys using Amazon KMS instead of providing or generating one locally.
```
$ ./WrapAWSEncryptionKey 
usage: WrapAWSEncryptionKey
  -a,--alias <arg>        AWS CMK Alias (required)
  -ak,--accesskey <arg>   AWS Access Key (required)
  -c,--cert <arg>         Path to Certificate File (required)
  -h,--help               Help for WrapAWSEncryptionKey
  -i,--kid <arg>          Key Identifier (optional)
  -r,--region <arg>       AWS Region (required)
  -sk,--secretkey <arg>   AWS Secret Key (required)
```

