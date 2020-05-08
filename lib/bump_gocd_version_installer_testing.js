const fs = require('fs');
const childProcess = require('child_process');
const assert = require('assert');
const request = require('request');
const VERSION = require('../version.json');

const GIT_USERNAME = process.env.GIT_USERNAME || bomb('GIT_USERNAME');
const GIT_PASSWORD = process.env.GIT_PASSWORD || process.env.GITHUB_TOKEN || bomb('GIT_PASSWORD');

const GOCD_VERSION_TO_RELEASE = VERSION.go_version;
const NEXT_GOCD_VERSION = VERSION.next_go_version;

const CLONE_TO_PATH = './installer-testing';
const options = {'cwd': CLONE_TO_PATH};

console.log(`Start cloning gocd git repository into ${CLONE_TO_PATH}...`);
childProcess.execSync(`git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/gocd/installer-testing ${CLONE_TO_PATH}`);
console.log(`Done cloning repository...\n`);

const branchName = `bump-gocd-version-to-${GOCD_VERSION_TO_RELEASE}`;
console.log(`Checking out new branch '${branchName}'..`);
console.log(childProcess.execSync(`git checkout -b ${branchName}`, options).toString());
const checkedOutBranch = childProcess.execSync('git branch | grep \\* | cut -d \' \' -f2', options).toString().trim();
assert.strictEqual(checkedOutBranch, branchName, `Expected current branch to be ${branchName}, but was ${checkedOutBranch}`);
console.log('Done checking out new branch..');

const fileNames = [
    'pipeline-as-code/Installer-test-with-addons.gocd.yaml',
    'pipeline-as-code/Upgrade-testing-with-addons.gocd.yaml',
    'pipeline-as-code/installer-tests.gocd.yaml'
];
let fileName;
for (fileName of fileNames) {
    const filePath = `${CLONE_TO_PATH}/${fileName}`;
    let fileContent = fs.readFileSync(filePath, 'utf8');

    console.log(`Updating current gocd version from '${GOCD_VERSION_TO_RELEASE}' to '${NEXT_GOCD_VERSION}' in ${fileName}...`);
    fileContent = fileContent.replace(
        `GO_VERSION: ${GOCD_VERSION_TO_RELEASE}`,
        `GO_VERSION: ${NEXT_GOCD_VERSION}`);
    console.log("Done updating.");

    fs.writeFileSync(filePath, fileContent);

    console.log('Performing git diff..');
    console.log(childProcess.execSync(`git diff`, options).toString());
    console.log('-----');

    const commitMessage = `Bump up GoCD Version to ${NEXT_GOCD_VERSION} in file ${fileName}`;
    console.log('Committing changes...');
    console.log(childProcess.execSync(`git add ${fileName}`, options).toString());
    console.log(childProcess.execSync(`git commit --signoff -m "${commitMessage}"`, options).toString());
    console.log('Done committing changes...');
}

console.log('Pushing branch to origin..');
console.log(childProcess.execSync(`git push origin ${branchName}`, options).toString());
console.log('Done Pushing branch to origin..');
console.log('------------');

console.log('Creating Pull Request..');
const requestConfig = {
    'url': `https://api.github.com/repos/gocd/installer-testing/pulls`,
    'method': 'POST',
    'auth': {
        'username': GIT_USERNAME,
        'password': GIT_PASSWORD
    },
    body: {
        title: `[post-release] Bump up GoCD Version to ${NEXT_GOCD_VERSION}`,
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
