module.exports = {
    httpPort:             /* adapter's http port */
        3000,
    httpsPort:            /* adapter's https port */
        3001,

    adapterHost:          /* adapter's hostname or address */
        '10.0.2.2',

    publicCert:           /* self-signed public certificate */
        'cert.pem',
    privateKey:           /* self-signed private key */
        'key.pem',
        
    googleDiscoveryUrl:   /* google identity platform discovery document url */
        'https://accounts.google.com/.well-known/openid-configuration',
    googleTokenEndpoint:  /* google identity platform token endpoint url */
        'https://www.googleapis.com/oauth2/v4/token',

    approov_header:       /* Approov header name */
        'authorization',
    approov_enforcement:  /* set true to enforce token checks */
        false,
};
