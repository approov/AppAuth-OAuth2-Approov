const dotenv = require('dotenv').config()

if (dotenv.error) {
  throw dotenv.error
}

const config = {
    http_port: dotenv.parsed.HTTP_PORT,
    domain: dotenv.parsed.DOMAIN,

    google_discovery_url: dotenv.parsed.GOOGLE_DISCOVERY_URL,
    google_token_endpoint: dotenv.parsed.GOOGLE_TOKEN_ENDPOINT,
    google_client_secret: dotenv.parsed.GOOGLE_CLIENT_SECRET,

    approov_base64_secret: dotenv.parsed.APPROOV_TOKEN_BASE64_SECRET,
    approov_enforcement: dotenv.parsed.APPROOV_ENFORCEMENT || true,
}

let missing_env_vars = ""


console.debug(config)

Object.entries(config).forEach(([key, value]) => {
  if (value === null || value === "" || value == undefined) {
    missing_env_vars += key.toUpperCase() + ", "
  }
})

if (missing_env_vars !== "") {
  throw new Error("Missing Env Vars values for: " + missing_env_vars.slice(0, -2)) // removes last comma in the string
}

module.exports = config
