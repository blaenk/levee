import 'babel-polyfill';

global.chai = require('chai');

global.chaiAsPromised = require('chai-as-promised');
chai.use(chaiAsPromised);

global.sinon = require('sinon');
global.sinonChai = require('sinon-chai');
chai.use(sinonChai);

chai.should();
global.chaiAsPromised = chaiAsPromised;
global.expect = chai.expect;
global.AssertionError = chai.AssertionError;
global.Assertion = chai.Assertion;
global.assert = chai.assert;
