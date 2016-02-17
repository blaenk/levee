import Bluebird from 'bluebird';

import scgi from '../../lib/scgi.js';

// examples taken from scgi protocol specification
// https://python.ca/scgi/protocol.txt

describe('SCGI', () => {
  it('should build a request', () => {
    const request = scgi.buildRequest({
      REQUEST_METHOD: "POST",
      REQUEST_URI: "/deepthought"
    }, "What is the answer to life, the Universe and everything?");

    const answer =
      "70:" +
      "CONTENT_LENGTH" + '\x00' + "56" + '\x00' +
        "SCGI" + '\x00' +  "1" + '\x00' +
        "REQUEST_METHOD" + '\x00' +  "POST" + '\x00' +
        "REQUEST_URI" + '\x00' +  "/deepthought" + '\x00' +
        "," +
      "What is the answer to life, the Universe and everything?";

    assert.equal(answer, request);
  });

  it('should parse a response', () => {
    const response =
      "Status: 200 OK\r\n" +
      "Content-Type: text/plain\r\n" +
      "\r\n" +
      "42";

    const parsed = scgi.parseResponse(response);

    assert.equal(parsed.body, '42');
    assert.equal(parsed.headers['Status'], '200 OK');
    assert.equal(parsed.headers['Content-Type'], 'text/plain');
  });
});
