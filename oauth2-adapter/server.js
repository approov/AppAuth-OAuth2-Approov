const path = require('path');
const fs = require('fs');
const http = require('http');
const https = require('https');
const express = require('express');
const bodyParser = require('body-parser');
const querystring = require('querystring');
const axios = require('axios');
const chalk = require('chalk');

const config = require(`${__dirname}/config/config`);
const secrets = require(`${__dirname}/config/secrets`);
const approov = require(`${__dirname}/approov`);

const httpPort = config.httpPort || 3000
const httpsPort = config.httpsPort || 3001

var app = express();

const options = {
  cert: fs.readFileSync(`${__dirname}/config/${config.publicCert}`),
  key: fs.readFileSync(`${__dirname}/config/${config.privateKey}`)
};

var googleTokenEndpoint = config.googleTokenEndpoint;

function log_req(req) {
  console.log(chalk.cyan(`Request: ${JSON.stringify({
    originalUrl: req.originalUrl,
    params: req.params,
    headers: req.headers,
  }, null, '  ')}`));
}

// redirect http to https

app.use(function(req, res, next) {
  if (req.secure) {
      next();
  } else {
      let host = req.headers.host.replace(new RegExp(`:${httpPort}`, 'g'), `:${httpsPort}`);
      res.redirect(`https://${host}${req.url}`);
  }
});

// check approov token

if (config.approov_header  == null) {
  throw new Error(`approov_header not found; please set in ${__dirname}/config.js`);
}
const approovHdr = config.approov_header;

// parse bodies

app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true })); // support form-encoded bodies (for the token endpoint)

// adapt google discovery doc

app.get('/.well-known/openid-configuration', (req, res) => {
  axios.get(config.googleDiscoveryUrl)
    .then(response => { 
      var doc = response.data;

      // update google token endpoint 
      if (doc.token_endpoint) googleTokenEndpoint = doc.googleTokenEndpoint;
      
      // replace adapter as token endpoint
      doc.token_endpoint = `https://10.0.2.2:${httpsPort}/oauth2/token`;

      res.send(JSON.stringify(doc, undefined, 2));
    })
    .catch(error => {
      console.log(error);
    });
});

// adapt token endpoint

app.post('/oauth2/token', (req, res) => {
  console.log('oauth2/token');
  //log_req(req);
	
	var auth = req.headers['authorization'];
	if (auth) {
		// check the auth header
		var clientCredentials = new Buffer(auth.slice('basic '.length), 'base64').toString().split(':');
		var clientId = querystring.unescape(clientCredentials[0]);
		var clientSecret = querystring.unescape(clientCredentials[1]);
  
    if (req.body.client_id) {
      console.log(chalk.red('Client attempted to authenticate with multiple methods'));
      res.status(401).json({error: 'overauthorized'});
      return;
    }

    delete RegExp.body['authorization'];
  } else if (req.body.client_id) {
		var clientId = req.body.client_id;
		var clientSecret = req.body.client_secret;
    delete req.body.client_secret;
  }

  if (!(clientId && clientSecret)) {
    console.log(chalk.red('Client missing id or secret'));
    res.status(401).json({error: 'unauthoried'});
    return;
  }

  console.log(`client_id: ${clientId}`);
  console.log(`client_secret: ${clientSecret}`);

  // check approov token

  if (!approov.isValid(clientSecret)) {
    console.log(chalk.red('Unauthorized: invalid Approov token'));
    res.status(401).send('Unauthorized');
    return;
  }

  // adapt body with expected client secret

  req.body.client_id = clientId;
  if (secrets.google_client_secret) {
    req.body.client_secret = secrets.google_client_secret;
  }

  let encodedBody = Object.keys(req.body).map(k => `${encodeURIComponent(k)}=${encodeURIComponent(req.body[k])}`).join('&');

  console.log(`body: ${encodedBody}`);

  // post to actual google token endpoint

  axios.post(config.googleTokenEndpoint, encodedBody)
  .then(response => { 
    res.send(response.data);
  })
  .catch(error => {
    console.log(`${error}`);
  });
});

// start server

http.createServer(app).listen(httpPort);
https.createServer(options, app).listen(httpsPort);

console.log(`Server listening on ports ${httpPort} & ${httpsPort}`);

// end of file
