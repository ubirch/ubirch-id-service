package com.ubirch.cert;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

public class BCCertGen {
    public static String _country = "Germany",
            _organisation = "ubirch Test GmbH",
            _location = "Berlin",
            _state = "Berlin",
            _issuer = "ubirch GmbH Test CA";

    public BCCertGen(String country, String organisation, String location, String state, String issuer) {
        _country = country;
        _organisation = organisation;
        _location = location;
        _state = state;
        _issuer = issuer;
    }

    public static X509Certificate generate(PrivateKey privKey, PublicKey pubKey, int duration, String signAlg, boolean isSelfSigned, String commonName) throws Exception {
        Provider BC = new BouncyCastleProvider();

        // distinguished name table.
        X500NameBuilder builder = createStdBuilder(commonName);

        // create the certificate
        ContentSigner sigGen = new JcaContentSignerBuilder(signAlg).build(privKey);
        X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(
                new X500Name("cn=" + _issuer),    //Issuer
                BigInteger.valueOf(1),      //Serial
                new Date(System.currentTimeMillis() - 50000),   //Valid from
                new Date((long) (System.currentTimeMillis() + duration * 8.65 * Math.pow(10, 7))),    //Valid to
                builder.build(),    //Subject
                pubKey              //Publickey to be associated with the certificate
        );

        X509Certificate cert = new JcaX509CertificateConverter().setProvider(BC).getCertificate(certGen.build(sigGen));

        cert.checkValidity(new Date());

        if (isSelfSigned) {
            // check verifies in general
            cert.verify(pubKey);
            // check verifies with contained key
            cert.verify(cert.getPublicKey());
        }

        ByteArrayInputStream bIn = new ByteArrayInputStream(cert.getEncoded());
        CertificateFactory fact = CertificateFactory.getInstance("X.509", BC);

        return (X509Certificate) fact.generateCertificate(bIn);
    }

    private static X500NameBuilder createStdBuilder(String commonName) {
        X500NameBuilder builder = new X500NameBuilder(RFC4519Style.INSTANCE);

        builder.addRDN(RFC4519Style.c, _country);
        builder.addRDN(RFC4519Style.o, _organisation);
        builder.addRDN(RFC4519Style.l, _location);
        builder.addRDN(RFC4519Style.st, _state);
        builder.addRDN(RFC4519Style.cn, commonName);

        return builder;
    }
}
