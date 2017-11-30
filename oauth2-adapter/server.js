const fs = require('fs');
const http = require('http');
const https = require('https');
const express = require('express');
const bodyParser = require('body-parser');
const axios = require('axios');

const config = require('./config');

const httpPort = config.httpPort || 3000
const httpsPort = config.httpsPort || 3001

var app = express();

const options = {
  cert: fs.readFileSync(__dirname + config.publicCert),
  key: fs.readFileSync(__dirname + config.privateKey)
};

var googleTokenEndpoint = config.googleTokenEndpoint;

// redirect http to https

app.use(function(req, res, next) {
  if (req.secure) {
      next();
  } else {
      let host = req.headers.host.replace(new RegExp(`:${httpPort}`, 'g'), `:${httpsPort}`);
      res.redirect(`https://${host}${req.url}`);
  }
});

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
	
	var auth = req.headers['authorization'];
	if (auth) {
		// check the auth header
		var clientCredentials = new Buffer(auth.slice('basic '.length), 'base64').toString().split(':');
		var clientId = querystring.unescape(clientCredentials[0]);
		var clientSecret = querystring.unescape(clientCredentials[1]);
	}
	
	// otherwise, check the post body
	if (req.body.client_id) {
		if (clientId) {
			// if we've already seen the client's credentials in the authorization header, this is an error
			console.log('Client attempted to authenticate with multiple methods');
			res.status(401).json({error: 'invalid_client'});
			return;
		}
		
		var clientId = req.body.client_id;
		var clientSecret = req.body.client_secret;
  }

  // adapt the headers...

  let headers = Object.assign({}, req.headers);
  delete headers["content-length"];

  // reencode body

  let encodedBody = Object.keys(req.body).map(k => `${encodeURIComponent(k)}=${encodeURIComponent(req.body[k])}`).join('&');

  console.log('proxying token post:');
  console.log(`  client_id: ${clientId}`);
  console.log(`  client_secret: ${clientSecret}`);
  console.log(`  body: ${JSON.stringify(req.body, undefined, 2)}`);
  console.log(`  body: ${encodedBody}`);
  console.log(`  headers: ${JSON.stringify(req.headers, undefined, 2)}`);
  console.log(`  headers: ${JSON.stringify(headers, undefined, 2)}`);

  // post to actual google token endpoint

  axios.post(config.googleTokenEndpoint, encodedBody)
  .then(response => { 
    res.send(response.data);
  })
  .catch(error => {
    console.log(error);
  });
});

// start server

http.createServer(app).listen(httpPort);
https.createServer(options, app).listen(httpsPort);

console.log(`Server listening on ports ${httpPort} & ${httpsPort}`);

// end of file
