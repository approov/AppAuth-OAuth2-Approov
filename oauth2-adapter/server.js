const http = require('http');
const express = require('express');
const bodyParser = require('body-parser');
const querystring = require('querystring');
const axios = require('axios');
const chalk = require('chalk');

const config = require(`${__dirname}/config/config`);
const approov = require(`${__dirname}/approov`);

const httpPort = config.http_port || 3000
const domain = config.domain

let app = express();

function log_req(req) {
  console.log(chalk.cyan(`Request: ${JSON.stringify({
    originalUrl: req.originalUrl,
    params: req.params,
    headers: req.headers,
  }, null, '  ')}`));
}

app.use('/robots.txt', function(req, res, next) {
  res.type('text/plain')
  res.send("User-agent: *\nDisallow: /");
});

// Handles request to the root entry point.
app.get('/', (req, res) => {
  res.status(200).json({name: 'Books App'});
});

// parse bodies
app.use(bodyParser.json());
// support for form-encoded bodies (for the token endpoint)
app.use(bodyParser.urlencoded({ extended: true }));

// OAuth2 adapter - google discovery doc
app.get('/.well-known/openid-configuration', (req, res) => {

  log_req(req);

  axios.get(config.google_discovery_url)
    .then(response => { 
      console.log("---> /.well-known/openid-configuration <---")

      let doc = response.data;

      console.debug("openid-configuration before", doc)
      
      // replace adapter as token endpoint
      doc.token_endpoint = `https://${domain}/oauth2/token`;
      doc.userinfo_endpoint = "https://www.googleapis.com/oauth2/v3/userinfo";
      console.debug("openid-configuration", doc)

      res.json(doc);
    })
    .catch(error => {
      console.log("AXIOS:", config.google_discovery_url, error);
    });
});

// OAuth2 adapter - endpoint for the redirect uri to exchange the code for an
//  access and id token via the Google token endpoint
app.post('/oauth2/token', (req, res) => {
  console.log('---> /oauth2/token <---');

  log_req(req);

  let clientSecret
  let clientId
	let auth = req.headers['authorization'];

	if (auth) {
  	let clientCredentials = new Buffer(auth.slice('basic '.length), 'base64').toString().split(':');
		clientId = querystring.unescape(clientCredentials[0]);
		clientSecret = querystring.unescape(clientCredentials[1]);
  
    if (req.body.client_id) {
      console.log(chalk.red('Client attempted to authenticate with multiple methods'));
      res.status(401).json({error: 'overauthorized'});
      return;
    }

    delete RegExp.body['authorization'];

  } else if (req.body.client_id) {
		clientId = req.body.client_id;
		clientSecret = req.body.client_secret;
    delete req.body.client_secret;
  }

  if (!(clientId && clientSecret)) {
    console.log(chalk.red('Client missing id or secret'));
    res.status(401).json({error: 'unauthoried'});
    return;
  }

  // Check the Approov Token
  if (!approov.isValid(clientSecret)) {
    console.log(chalk.red('Unauthorized: invalid Approov Token provided via the OAuth2 clientSecret.'));
    res.status(401).json({error: 'unauthoried'});
    return;
  }

  req.body.client_id = clientId;

  if (config.google_client_secret) {
    // Replace the Approov Token in the client secret with the expected Google client secret.
    req.body.client_secret = config.google_client_secret;
  }

  let encodedBody = Object.keys(req.body).map(k => `${encodeURIComponent(k)}=${encodeURIComponent(req.body[k])}`).join('&');

  // post to actual google token endpoint
  axios.post(config.google_token_endpoint, encodedBody)
  .then(response => { 
    res.json(response.data);
  })
  .catch(error => {
    console.debug("AXIOS:", config.google_token_endpoint, error);
    res.status(500).json({error: 'Internal server error'});
  });
});

// start server
http.createServer(app).listen(httpPort);

console.log(`Server listening on ports ${httpPort}`);
