const $RefParser = require("@apidevtools/json-schema-ref-parser");
const fs = require('fs');

let args = process.argv.slice(2);
let rawdata = fs.readFileSync(args[0]);
let input = JSON.parse(rawdata);
$RefParser.dereference(input, (err, schema) => {
  if (err) {
    console.error(err);
  }
  else {
    let data = JSON.stringify(schema, null, 2);
    fs.writeFileSync(args[1], data);
  }
})