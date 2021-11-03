const fs = require('fs');
const childProcess = require('child_process');
const assert = require('assert');
const request = require('request');
const VERSION = require('../version.json');

const CLONE_TO_PATH = './tmp-charts';

const GIT_USERNAME = process.env.GIT_USERNAME || bomb('GIT_USERNAME');
const GIT_PASSWORD = process.env.GIT_PASSWORD || bomb('GIT_PASSWORD');

const GOCD_CURRENT_VERSION = VERSION.previous_go_version;
const GOCD_VERSION_TO_RELEASE = VERSION.go_version;

const options = {'cwd': CLONE_TO_PATH};

console.log(`Start cloning git repository into '${CLONE_TO_PATH}'...`);
childProcess.execSync(`git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/gocd/helm-chart ${CLONE_TO_PATH}`);
console.log(`Done cloning repository...\n`);

console.log('Verifying current branch being master..');
const currentBranch = childProcess.execSync('git branch | grep \\* | cut -d \' \' -f2', options).toString().trim();
assert.strictEqual(currentBranch, 'master', `Expected current branch to be master, but was ${currentBranch}`);
console.log('Done verifying master branch..');

const branchName = `bump-gocd-version-to-${GOCD_VERSION_TO_RELEASE}`;
console.log(`Checking out new branch '${branchName}'..`);
console.log(childProcess.execSync(`git checkout -b ${branchName}`, options).toString());
const checkedOutBranch = childProcess.execSync('git branch | grep \\* | cut -d \' \' -f2', options).toString().trim();
assert.strictEqual(checkedOutBranch, branchName, `Expected current branch to be ${branchName}, but was ${checkedOutBranch}`);
console.log('Done checking out new branch..');

const chartYamlFilePath = `${CLONE_TO_PATH}/gocd/Chart.yaml`;
let chartYamlContent = fs.readFileSync(chartYamlFilePath, 'utf8');

console.log(`Updating appVersion from '${GOCD_CURRENT_VERSION}' to '${GOCD_VERSION_TO_RELEASE}' in Chart.yaml...`);
chartYamlContent = chartYamlContent.replace(`appVersion: ${GOCD_CURRENT_VERSION}`, `appVersion: ${GOCD_VERSION_TO_RELEASE}`);
fs.writeFileSync(chartYamlFilePath, chartYamlContent);
console.log(`Done updating appVersion.`);

const versionString = chartYamlContent.match('version: [0-9].[0-9]+.[0-9]')[0];
const currentAppVersion = versionString.split(':')[1].trim();
const splitAppVersions = currentAppVersion.split('.');
splitAppVersions[1] = (+splitAppVersions[1]) + 1;
splitAppVersions[2] = 0;
const newAppVersion = splitAppVersions.join('.');

console.log(`Updating Chart version from '${currentAppVersion}' to '${newAppVersion}' in Chart.yaml...`);
chartYamlContent = chartYamlContent.replace(`version: ${currentAppVersion}`, `version: ${newAppVersion}`);
fs.writeFileSync(chartYamlFilePath, chartYamlContent);
console.log(`Done updating appVersion..`);

console.log('Performing git diff..');
console.log(childProcess.execSync(`git diff`, options).toString());
console.log('-----');

const commitMessage = `Bump up GoCD Version to ${GOCD_VERSION_TO_RELEASE}`;
console.log('Committing Chart.yaml changes...');
console.log(childProcess.execSync(`git add gocd/Chart.yaml`, options).toString());
console.log(childProcess.execSync(`git commit --signoff -m "${commitMessage}"`, options).toString());
console.log('Done committing changes...');

console.log('Updating Changelog...');
const latestCommitShortSHA = childProcess.execSync(`git rev-parse --short HEAD`, options).toString().trim();

const changelog = `### ${newAppVersion}
* [${latestCommitShortSHA}](https://github.com/gocd/helm-chart/commit/${latestCommitShortSHA}): ${commitMessage}
`;

const changelogFilePath = `${CLONE_TO_PATH}/CHANGELOG.md`;
const existingChangelog = fs.readFileSync(changelogFilePath, 'utf8');
const newChangelog = changelog + existingChangelog;
fs.writeFileSync(changelogFilePath, newChangelog);
console.log('Done updating Changelog...');

console.log('Performing git diff..');
console.log(childProcess.execSync(`git diff`, options).toString());
console.log('-----');

const changelogCommitMessage = 'Updated Changelog.';
console.log('Committing CHANGELOG.md changes...');
console.log(childProcess.execSync(`git add CHANGELOG.md`, options).toString());
console.log(childProcess.execSync(`git commit --signoff -m "${changelogCommitMessage}"`, options).toString());
console.log('Done committing changes...');

console.log('Pushing branch to origin..');
console.log(childProcess.execSync(`git push origin ${branchName}`, options).toString());
console.log('Done Pushing branch to origin..');
console.log('------------');

console.log('Creating Pull Request..');

const requestConfig = {
    'url': `https://api.github.com/repos/gocd/helm-chart/pulls`,
    'method': 'POST',
    'auth': {
        'username': GIT_USERNAME,
        'password': GIT_PASSWORD
    },
    body: {
        title: `[GoCD Helm Chart] ${commitMessage}`,
        head: `${branchName}`,
        base: `master`,
        maintainer_can_modify: true,
        body: `PR to update the helm chart to the latest version of GoCD`
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
