const fs = require('fs');
const childProcess = require('child_process');
const VERSION = require('../version.json');

const GIT_USERNAME = process.env.GIT_USERNAME || bomb('GIT_USERNAME');
const GIT_PASSWORD = process.env.GIT_PASSWORD || process.env.GITHUB_TOKEN || bomb('GIT_PASSWORD');

const GOCD_VERSION_TO_RELEASE = VERSION.go_version;
const NEXT_GOCD_VERSION = VERSION.next_go_version;

const CLONE_TO_PATH = './go-plugins';
const options = {'cwd': CLONE_TO_PATH};

console.log(`Start cloning gocd git repository into ${CLONE_TO_PATH}...`);
childProcess.execSync(`git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/gocd/go-plugins ${CLONE_TO_PATH}`);
console.log(`Done cloning repository...\n`);

const gradleFilePath = `${CLONE_TO_PATH}/build.gradle`;
let gradleFileContent = fs.readFileSync(gradleFilePath, 'utf8');

console.log(`Updating current gocd version from '${GOCD_VERSION_TO_RELEASE}' to '${NEXT_GOCD_VERSION}' in build.gradle...`);
gradleFileContent = gradleFileContent.replace(
    `goVersion = '${GOCD_VERSION_TO_RELEASE}'`,
    `goVersion = '${NEXT_GOCD_VERSION}'`);
console.log("Done updating.");

fs.writeFileSync(gradleFilePath, gradleFileContent);

console.log('Performing git diff..');
console.log(childProcess.execSync(`git diff`, options).toString());
console.log('-----');

const commitMessage = `Bump up GoCD Version to ${NEXT_GOCD_VERSION}`;
console.log('Committing build.gradle changes...');
console.log(childProcess.execSync(`git add build.gradle`, options).toString());
console.log(childProcess.execSync(`git commit --signoff -m "${commitMessage}"`, options).toString());
console.log('Done committing changes...');

console.log('Pushing changes..');
console.log(childProcess.execSync(`git push`, options).toString());
console.log('Done pushing changes..');
console.log('------------');
