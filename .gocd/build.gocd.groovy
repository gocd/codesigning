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

import cd.go.contrib.plugins.configrepo.groovy.dsl.ExecTask
import cd.go.contrib.plugins.configrepo.groovy.dsl.FetchArtifactTask
import cd.go.contrib.plugins.configrepo.groovy.dsl.GoCD

def fetchArtifactTask = { String osType ->
  return new FetchArtifactTask(false, {
    pipeline = 'installers'
    stage = 'dist'
    job = 'dist'
    source = "dist/${osType}"
    destination = 'codesigning/src'
  })
}

def cleanTasks = {
  return [
    new ExecTask({
      commandLine = ['git', 'clean', '-dffx']
      workingDir = 'codesigning'
    })
  ]
}

def getArtifact = { String source1 ->
  return new FetchArtifactTask(false, {
    pipeline = 'code-sign'
    file = true
    stage = 'aggregate-jsons'
    job = 'aggregate-jsons'
    source = 'out/latest.json'
    destination = 'codesigning'
  })
}

def secretParam = { String param ->
  return "{{SECRET:[build-pipelines][$param]}}".toString()
}

GoCD.script {
  environments {
    environment('internal') {
      pipelines = ['code-sign', 'PublishStableRelease']
    }
  }

  pipelines {
    pipeline('code-sign') { thisPipeline ->
      group = 'go-cd'
      environmentVariables = [
        STABLE_DOWNLOAD_BUCKET      : 'downloadgocdio-downloadgocdios3-192sau789jtkh',
        EXPERIMENTAL_DOWNLOAD_BUCKET: 'downloadgocdio-experimentaldownloadss3-dakr8wkhi2bo/experimental',
        UPDATE_CHECK_BUCKET         : 'updategocdio-updategocdios3-1ujj23u8hpqdl'
      ]

      materials() {
        git('codesigning') {
          url = 'https://github.com/gocd/codesigning'
          destination = 'codesigning'
          blacklist = ['**/*.*', '**/*']
        }
        git('signing-keys') {
          url = 'https://git.gocd.io/git/gocd/signing-keys'
          destination = 'signing-keys'
          blacklist = ['**/*.*', '**/*']
        }
        dependency('installers') {
          pipeline = 'installers'
          stage = 'docker'
        }
        dependency('regression-SPAs') {
          pipeline = 'regression-SPAs'
          stage = 'Firefox'
        }
      }

      stages {
        stage('sign-and-upload') {
          cleanWorkingDir = true
          environmentVariables = [
            GOCD_GPG_PASSPHRASE  : secretParam('GOCD_GPG_PASSPHRASE'),
          ]
          jobs {
            job('rpm') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                addAll(cleanTasks())
                add(fetchArtifactTask('rpm'))
                add(fetchArtifactTask('meta'))
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace rpm:sign rpm:upload[${EXPERIMENTAL_DOWNLOAD_BUCKET}] yum:createrepo[${EXPERIMENTAL_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
            job('deb') {
              elasticProfileId = 'ubuntu-release-aws-privileged'
              tasks {
                addAll(cleanTasks())
                add(fetchArtifactTask('deb'))
                add(fetchArtifactTask('meta'))
                bash {
                  commandString = 'rake --trace deb:sign[${EXPERIMENTAL_DOWNLOAD_BUCKET}] deb:upload[${EXPERIMENTAL_DOWNLOAD_BUCKET}] apt:createrepo[${EXPERIMENTAL_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
            job('zip') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                add(fetchArtifactTask('zip'))
                add(fetchArtifactTask('meta'))
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace zip:sign zip:upload[${EXPERIMENTAL_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
            job('win') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                addAll(cleanTasks())
                add(fetchArtifactTask('win'))
                add(fetchArtifactTask('meta'))
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace win:metadata win:upload[${EXPERIMENTAL_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
            job('osx') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                add(fetchArtifactTask('osx'))
                add(fetchArtifactTask('meta'))
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace osx:sign_as_zip osx:upload[${EXPERIMENTAL_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
            job('upload-docker-image') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [
                DOCKERHUB_USERNAME: secretParam('DOCKERHUB_USERNAME'),
                DOCKERHUB_TOKEN   : secretParam('DOCKERHUB_TOKEN')
              ]

              tasks {
                fetchArtifact {
                  job = 'docker-server'
                  pipeline = 'installers'
                  source = 'docker-server'
                  stage = 'docker'
                  destination = 'codesigning'
                }
                fetchArtifact {
                  job = 'docker-agent'
                  pipeline = 'installers'
                  source = 'docker-agent'
                  stage = 'docker'
                  destination = 'codesigning'
                }
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake docker:upload_experimental_docker_images'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'npm install && node lib/update_dockerhub_full_description.js gocdexperimental'
                  workingDir = 'codesigning'
                }
              }
            }
          }
        }

        stage('aggregate-jsons') {
          environmentVariables = [
            GOCD_GPG_PASSPHRASE: secretParam('GOCD_GPG_PASSPHRASE'),
            DOCKERHUB_ORG      : 'gocdexperimental',
            DOCKERHUB_USERNAME : secretParam('DOCKERHUB_USERNAME'),
            DOCKERHUB_TOKEN    : secretParam('DOCKERHUB_TOKEN')
          ]
          jobs {
            job('aggregate-jsons') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              artifacts {
                build {
                  destination = 'out'
                  source = 'codesigning/out/latest.json'
                }
              }
              tasks {
                addAll(cleanTasks())
                add(fetchArtifactTask('meta'))
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace metadata:aggregate_jsons[${EXPERIMENTAL_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
          }
        }

        stage('metadata') {
          environmentVariables = [
            GOCD_GPG_PASSPHRASE: secretParam('GOCD_GPG_PASSPHRASE'),
          ]
          jobs {
            job('generate') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                addAll(cleanTasks())
                add(fetchArtifactTask('meta'))
                add(getArtifact())
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace metadata:generate[${UPDATE_CHECK_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
          }
        }

        stage('cloudfront-invalidation') {
          jobs {
            job('invalidate-distributions') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace cloudfront:invalidate'
                  workingDir = 'codesigning'
                }
              }
            }
          }
        }

        stage('upload-to-maven-exp') {
          cleanWorkingDir = true
          approval {
            type = 'manual'
          }
          environmentVariables = [
            EXPERIMENTAL_RELEASE        : 'true', // Auto-releases to central when false
            GOCD_GPG_PASSPHRASE         : secretParam('GOCD_GPG_PASSPHRASE'),
            MAVEN_CENTRAL_TOKEN_USERNAME: secretParam('MAVEN_CENTRAL_TOKEN_USERNAME'),
            MAVEN_CENTRAL_TOKEN_PASSWORD: secretParam('MAVEN_CENTRAL_TOKEN_PASSWORD'),
          ]
          jobs {
            job('upload-maven') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                  destination = 'codesigning'
                }
                fetchArtifact {
                  job = 'dist'
                  pipeline = 'installers'
                  source = 'go-plugin-api'
                  stage = 'dist'
                  destination = 'codesigning'
                }
                fetchArtifact {
                  job = 'dist'
                  pipeline = 'installers'
                  source = 'go-plugin-config-repo'
                  stage = 'dist'
                  destination = 'codesigning'
                }
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'git pull && bundle exec rake maven:upload_to_maven'
                  workingDir = 'codesigning'
                }
              }
            }
          }
        }
      }
    }

    pipeline('PublishStableRelease') {
      group = 'go-cd'

      environmentVariables = [
        STABLE_DOWNLOAD_BUCKET      : 'downloadgocdio-downloadgocdios3-192sau789jtkh',
        EXPERIMENTAL_DOWNLOAD_BUCKET: 'downloadgocdio-experimentaldownloadss3-dakr8wkhi2bo/experimental',
        UPDATE_CHECK_BUCKET         : 'updategocdio-updategocdios3-1ujj23u8hpqdl',
        REALLY_REALLY_UPLOAD        : ''
      ]

      materials() {
        git('codesigning') {
          url = 'https://github.com/gocd/codesigning'
          destination = 'codesigning'
          blacklist = ['**/*.*', '**/*']
        }
        git('signing-keys') {
          url = 'https://git.gocd.io/git/gocd/signing-keys'
          destination = 'signing-keys'
          blacklist = ['**/*.*', '**/*']
        }
        dependency('code-sign') {
          pipeline = 'code-sign'
          stage = 'metadata'
        }
        dependency('installer-tests') {
          pipeline = 'installer-tests'
          stage = 'install-tests'
        }
      }

      stages {
        stage('promote-binaries') {
          approval {
            type = 'manual'
          }
          jobs {
            job('promote-binaries') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                bash {
                  commandString = 'if [ "${REALLY_REALLY_UPLOAD}" != \'YES_I_REALLY_REALLY_WANT_TO_UPLOAD\' ]; then echo "REALLY_REALLY_UPLOAD environment variable should be overridden while triggering."; exit 1; fi'
                }
                fetchDirectory {
                  pipeline = 'installers/code-sign'
                  stage = 'dist'
                  job = 'dist'
                  source = 'dist/meta'
                  destination = 'codesigning/src'
                }
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace promote:copy_binaries_from_experimental_to_stable[${EXPERIMENTAL_DOWNLOAD_BUCKET},${STABLE_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
          }
        }

        stage('create-repositories') {
          environmentVariables = [
            GOCD_GPG_PASSPHRASE  : secretParam('GOCD_GPG_PASSPHRASE'),
          ]
          jobs {
            job('apt') {
              elasticProfileId = 'ubuntu-release-aws-privileged'
              tasks {
                fetchDirectory {
                  pipeline = 'installers/code-sign'
                  stage = 'dist'
                  job = 'dist'
                  source = 'dist/meta'
                  destination = 'codesigning/src'
                }
                bash {
                  commandString = 'rake --trace apt:createrepo[${STABLE_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }

            job('yum') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                fetchDirectory {
                  pipeline = 'installers/code-sign'
                  stage = 'dist'
                  job = 'dist'
                  source = 'dist/meta'
                  destination = 'codesigning/src'
                }
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace yum:createrepo[${STABLE_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
          }
        }

        stage('publish-stable-releases-json') {
          jobs {
            job('publish') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                fetchDirectory {
                  pipeline = 'installers/code-sign'
                  stage = 'dist'
                  job = 'dist'
                  source = 'dist/meta'
                  destination = 'codesigning/src'
                }
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace metadata:releases_json[${STABLE_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
          }
        }

        stage('publish-latest-json') {
          jobs {
            job('publish') {
              elasticProfileId = 'ecs-gocd-dev-build-release-aws-privileged'
              tasks {
                fetchDirectory {
                  pipeline = 'installers/code-sign'
                  stage = 'dist'
                  job = 'dist'
                  source = 'dist/meta'
                  destination = 'codesigning/src'
                }
                bash {
                  commandString = 'bundle install'
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace promote:update_check_json[${UPDATE_CHECK_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
          }
        }
      }
    }
  }
}
