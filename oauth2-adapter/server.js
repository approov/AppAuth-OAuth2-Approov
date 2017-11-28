const express = require('express');
const axios = require('axios');
const bodyParser = require('body-parser');

const config = require('./config');
const secrets = require('./secrets');

const port = config.serverPort || 3000

var app = express();

app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true })); // support form-encoded bodies (for the token endpoint)

app.get('/.well-known/openid-configuration', (req, res) => {
  axios.get(config.googleDiscoveryUrl)
    .then(response => {
      var doc = response.data;
      doc.token_endpoint = `http://localhost:${port}/oauth2/token`;
      res.send(JSON.stringify(doc, undefined, 2));
    })
    .catch(error => {
      console.log(error);
    });
});

app.post("/oauth2/token", (req, res) => {
	
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

  // handle code grant and token refresh from this endpoint...
  
  console.log('proxying token post:');
  console.log(`  client_id: ${clientId}`);
  console.log(`  client_secret: ${clientSecret}`);
  console.log(`  body: ${JSON.stringify(req.body, undefined, 2)}`);

  res.send('proxying post...');
});

app.listen(3000, () => {
  console.log(`Server is up on port ${port}`);
});

// end of file
