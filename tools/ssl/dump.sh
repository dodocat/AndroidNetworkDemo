#!/bin/bash
# Author: jing@quanqi.org 
# Created Time: Tue Jan 27 17:31:24 2015
# Description:  
# ChangeLog:

echo | openssl s_client -connect kyfw.12306.cn:443 2>&1 |  sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > kyfw.12306.cn.pem

CERTSTORE=../../app/src/main/res/raw/kyfw.bks

if [ -a $CERTSTORE ]; then
    rm $CERTSTORE || exit 1
fi

keytool -importcert -v \
    -trustcacerts \
    -alias 0 \
    -file <(openssl x509 -in kyfw.12306.cn.pem) \
    -keystore $CERTSTORE -storetype BKS \
    -providerclass org.bouncycastle.jce.provider.BouncyCastleProvider \
    -providerpath ./bcprov-jdk16-1.46.jar \
    -storepass asdfqaz

