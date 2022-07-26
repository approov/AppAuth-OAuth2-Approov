/*
 * Copyright (C) 2017 CriticalBlue, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const jwt = require('jsonwebtoken');
const chalk = require('chalk');

const config = require(`${__dirname}/config/config`);

const approovSecret = Buffer.from(config.approov_base64_secret, 'base64');
const extraChecks = { algorithms: ['HS256'] };

let  enforceApproov = config.approov_enforcement;

if (!enforceApproov) {
  console.log(chalk.red('\nCAUTION: Approov token checking is disabled!\n'));
}

function isEnforced() {
  return enforceApproov;
}

function isValid(token) {

  if (!enforceApproov) {
    console.log(chalk.red("Approov token check it's disabled."));
    return false
  }

  try {

    jwt.verify(token, approovSecret, extraChecks);
    console.log(chalk.blue('Approov token verified'));
    return true

  } catch (err) {
    console.log(chalk.red('Approov token failed verification: '));
    console.log(chalk.red(err))
    return false
  }

}


module.exports = {
  isEnforced: isEnforced,
  isValid: isValid
};
