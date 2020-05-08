const fs = require('fs');
const childProcess = require('child_process');
const assert = require('assert');
const request = require('request');
const VERSION = require('../version.json');

const GIT_USERNAME = process.env.GIT_USERNAME || bomb('GIT_USERNAME');
const GIT_PASSWORD = process.env.GIT_PASSWORD || process.env.GITHUB_TOKEN ||bomb('GIT_PASSWORD');

const GOCD_CURRENT_VERSION = VERSION.previous_go_version;
const GOCD_VERSION_TO_RELEASE = VERSION.go_version;
const NEXT_GOCD_VERSION = VERSION.next_go_version;

const CLONE_TO_PATH = './gocd';
const options = {'cwd': CLONE_TO_PATH};

console.log(`Start cloning gocd git repository into ${CLONE_TO_PATH}...`);
childProcess.execSync(`git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/gocd/gocd ${CLONE_TO_PATH}`);
console.log(`Done cloning repository...\n`);

const branchName = `bump-gocd-version-to-${GOCD_VERSION_TO_RELEASE}`;
console.log(`Checking out new branch '${branchName}'..`);
console.log(childProcess.execSync(`git checkout -b ${branchName}`, options).toString());
const checkedOutBranch = childProcess.execSync('git branch | grep \\* | cut -d \' \' -f2', options).toString().trim();
assert.strictEqual(checkedOutBranch, branchName, `Expected current branch to be ${branchName}, but was ${checkedOutBranch}`);
console.log('Done checking out new branch..');

const gradleFilePath = `${CLONE_TO_PATH}/build.gradle`;
let gradleFileContent = fs.readFileSync(gradleFilePath, 'utf8');

console.log(`Updating previous gocd version from '${GOCD_CURRENT_VERSION}' to '${GOCD_VERSION_TO_RELEASE}' in build.gradle...`);
gradleFileContent = gradleFileContent.replace(
    `def GO_VERSION_PREVIOUS = '${GOCD_CURRENT_VERSION}'`,
    `def GO_VERSION_PREVIOUS = '${GOCD_VERSION_TO_RELEASE}'`);
console.log("Done updating.");

const splitCurrentAppVersions = GOCD_VERSION_TO_RELEASE.split('.');
const splitNextAppVersions = NEXT_GOCD_VERSION.split('.');

console.log(`Updating current gocd version from '${GOCD_VERSION_TO_RELEASE}' to '${NEXT_GOCD_VERSION}' in build.gradle...`);
gradleFileContent = gradleFileContent.replace(
    `def GO_VERSION_SEGMENTS = [year: ${splitCurrentAppVersions[0]}, releaseInYear: ${splitCurrentAppVersions[1]}, patch: ${splitCurrentAppVersions[2]}]`,
    `def GO_VERSION_SEGMENTS = [year: ${splitNextAppVersions[0]}, releaseInYear: ${splitNextAppVersions[1]}, patch: ${splitNextAppVersions[2]}]`);
console.log("Done updating.");

splitNextAppVersions[1] = (+splitNextAppVersions[1]) + 1;
splitNextAppVersions[2] = 0;
const newNextAppVersion = splitNextAppVersions.join('.');

console.log(`Updating next gocd version from '${NEXT_GOCD_VERSION}' to '${newNextAppVersion}' in build.gradle...`);
gradleFileContent = gradleFileContent.replace(
    `def GO_VERSION_NEXT = '${NEXT_GOCD_VERSION}'`,
    `def GO_VERSION_NEXT = '${newNextAppVersion}'`);
console.log("Done updating.");

fs.writeFileSync(gradleFilePath, gradleFileContent);

console.log('Performing git diff..');
console.log(childProcess.execSync(`git diff`, options).toString());
console.log('-----');

const commitMessage = `Bump up GoCD Version to ${newNextAppVersion}`;
console.log('Committing build.gradle changes...');
console.log(childProcess.execSync(`git add build.gradle`, options).toString());
console.log(childProcess.execSync(`git commit --signoff -m "${commitMessage}"`, options).toString());
console.log('Done committing changes...');

console.log('Pushing branch to origin..');
console.log(childProcess.execSync(`git push origin ${branchName}`, options).toString());
console.log('Done Pushing branch to origin..');
console.log('------------');

console.log('Creating Pull Request..');
const requestConfig = {
    'url': `https://api.github.com/repos/gocd/gocd/pulls`,
    'method': 'POST',
    'auth': {
        'username': GIT_USERNAME,
        'password': GIT_PASSWORD
    },
    body: {
        title: `[post-release] ${commitMessage}`,
        head: `gocd:${branchName}`,
        base: `master`,
        maintainer_can_modify: true
    },
    json: true,
    headers: {
        'User-Agent': GIT_USERNAME,
        'Accept': 'application/vnd.github.v3+json'
    }
};

function sleep(time) {
    return new Promise((resolve) => setTimeout(resolve, time));
}

//sleep for 5 secs wait for the code to be pushed and then raise a pull request
sleep(5000).then(() => {
    request(requestConfig, (err, res) => {
        if (err) {
            return reject(err);
        }
        console.log('Done creating pull request..');
        console.log(res.body);
        console.log(`Visit: ${res.body.html_url} to see your pull request.`);
    });
});
