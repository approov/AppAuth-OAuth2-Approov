module.exports = {
    httpPort:             /* adapter's http port */
        3000,
    httpsPort:            /* adapter's https port */
        3001,

    publicCert:           /* self-signed public certificate */
        '/secrets/cert.pem',
    privateKey:           /* self-signed private key */
        '/secrets/key.pem',

    googleDiscoveryUrl:   /* google identity platform discovery document url */
        'https://accounts.google.com/.well-known/openid-configuration',
    googleTokenEndpoint:  /* google identity platform token endpoint url */
        'https://www.googleapis.com/oauth2/v4/token'
};
