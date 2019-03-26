let dockerHubAPI = require('docker-hub-api');
var request = require('request');

const allImages = [
    'gocd-server',
    'gocd-agent-alpine-3.6',
    'gocd-agent-alpine-3.7',
    'gocd-agent-alpine-3.8',
    'gocd-agent-alpine-3.9',
    'gocd-agent-docker-dind',
    'gocd-agent-centos-6',
    'gocd-agent-centos-7',
    'gocd-agent-debian-8',
    'gocd-agent-debian-9',
    'gocd-agent-ubuntu-14.04',
    'gocd-agent-ubuntu-16.04',
    'gocd-agent-ubuntu-18.04'
];
console.log('Updating the Dockerhub description to the contents of README in each repository.');

dockerHubAPI.login(process.env.DOCKERHUB_USERNAME, process.env.DOCKERHUB_PASSWORD)
    .then((info) => {
        dockerHubAPI.loggedInUser().then((_data) => {
            allImages.forEach((dockerhubRepo) => {
                dockerHubAPI.repository('gocd', dockerhubRepo).then((data) => {
                    let url = `https://raw.githubusercontent.com/gocd/docker-${dockerhubRepo}/master/README.md`;
                    if (dockerhubRepo === 'gocd-agent-docker-dind') {
                        url = 'https://raw.githubusercontent.com/gocd/gocd-agent-docker-dind/master/README.md';
                    }
                    request(url, (error, response, body) => {
                        json = {
                            full: body
                        };
                        dockerHubAPI.setRepositoryDescription('gocd', dockerhubRepo, json).then(data => {
                            console.log("Success: " + data.name);
                        }, (reason) => {
                            console.log("Rejected: " + dockerhubRepo + "\n" + reason)
                        });
                    });
                });
            });
        });
    });