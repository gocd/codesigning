/*
 * Copyright 2022 Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import cd.go.contrib.plugins.configrepo.groovy.dsl.GoCD

def secretParam = { String param ->
  return "{{SECRET:[build-pipelines][$param]}}".toString()
}

GoCD.script {
  pipelines {
    pipeline('publish-cloud-based-artifacts') {
      environmentVariables = [
        GOCD_STABLE_RELEASE  : 'true',
        version              : '',
        revision             : '',
        GIT_USER             : 'gocd-ci-user',
        GIT_PASSWORD         : secretParam("GOCD_CI_USER_RELEASE_TOKEN"),
      ]
      group = 'internal'
      labelTemplate = '${COUNT}'
      lockBehavior = 'none'
      materials {
        git('CodeSigning') {
          branch = 'master'
          shallowClone = false
          url = 'https://git.gocd.io/git/gocd/codesigning'
          destination = 'codesigning'
          autoUpdate = true
          blacklist = ["**/*.*", "**/*"]
        }
        git('GoCD') {
          branch = 'master'
          shallowClone = false
          url = 'https://git.gocd.io/git/gocd/gocd'
          destination = 'gocd'
          autoUpdate = true
          blacklist = ["**/*.*", "**/*"]
        }
        dependency('PromoteToStable') {
          pipeline = 'PublishStableRelease'
          stage = 'publish-latest-json'
        }
      }
      stages {
        stage('publish') {
          artifactCleanupProhibited = false
          cleanWorkingDir = true
          fetchMaterials = true
          approval {
            type = 'success'
          }
          jobs {
            job('publish-all-docker-images') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [
                DOCKERHUB_USERNAME: secretParam("DOCKERHUB_USERNAME"),
                DOCKERHUB_TOKEN   : secretParam("DOCKERHUB_TOKEN")
              ]
              tasks {
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                  destination = "codesigning"
                }
                fetchArtifact {
                  job = 'docker-server'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'docker-server'
                  stage = 'docker'
                  destination = "codesigning"
                }
                fetchArtifact {
                  job = 'docker-agent'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'docker-agent'
                  stage = 'docker'
                  destination = "codesigning"
                }
                bash {
                  commandString = "bundle"
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = "bundle exec rake docker:publish_docker_images"
                  workingDir = 'codesigning'
                }
                fetchArtifact {
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'dist/zip'
                  stage = 'dist'
                  destination = "gocd"
                }
                bash {
                  commandString = 'git config --global user.email "12554687+gocd-ci-user@users.noreply.github.com"'
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
                bash {
                  commandString = './gradlew --parallel --max-workers 4 docker:assemble -PskipDockerBuild -PdockerbuildServerZipLocation=\$(readlink -f zip/go-server-*.zip) -PdockerbuildAgentZipLocation=\$(readlink -f zip/go-agent-*.zip) -PdockerGitPush="I_REALLY_WANT_TO_DO_THIS"'
                  workingDir = 'gocd'
                }
              }
            }
          }
        }
        stage('post-publish') {
          artifactCleanupProhibited = false
          cleanWorkingDir = false
          fetchMaterials = true
          approval {
            type = 'success'
          }
          environmentVariables = [
            EXPERIMENTAL_DOWNLOAD_BUCKET: 'downloadgocdio-experimentaldownloadss3-dakr8wkhi2bo/experimental',
            STABLE_DOWNLOAD_BUCKET      : 'downloadgocdio-downloadgocdios3-192sau789jtkh',
          ]
          jobs {
            job('empty_exp_bucket') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle && bundle exec rake empty_experimental_bucket']
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
                exec {
                  commandLine = ['bash', '-c', 'bundle exec rake metadata:cleanup_cloud_json[${EXPERIMENTAL_DOWNLOAD_BUCKET}]']
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
              }
            }
            job('update_cloud_images') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                  destination = "codesigning"
                }
                exec {
                  commandLine = ['bash', '-c', 'bundle && bundle exec rake update_cloud_images']
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
              }
            }
            job('docker_cleanup') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [
                DOCKERHUB_ORG     : 'gocdexperimental',
                DOCKERHUB_USERNAME: secretParam("DOCKERHUB_USERNAME"),
                DOCKERHUB_TOKEN   : secretParam("DOCKERHUB_TOKEN")
              ]
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle && bundle exec rake cleanup_docker']
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
              }
            }
            job('update_dockerhub_full_description') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [
                DOCKERHUB_ORG     : 'gocdexperimental',
                DOCKERHUB_USERNAME: secretParam("DOCKERHUB_USERNAME"),
                DOCKERHUB_TOKEN   : secretParam("DOCKERHUB_TOKEN")
              ]
              tasks {
                fetchArtifact {
                  job = 'docker-server'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'docker-server'
                  stage = 'docker'
                  destination = "codesigning"
                }
                fetchArtifact {
                  job = 'docker-agent'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'docker-agent'
                  stage = 'docker'
                  destination = "codesigning"
                }
                exec {
                  commandLine = ['npm', 'install']
                  workingDir = "codesigning"
                }
                exec {
                  commandLine = ['bash', '-c', 'node lib/update_dockerhub_full_description.js gocd']
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
              }
            }
          }
        }
      }
    }
  }
}

