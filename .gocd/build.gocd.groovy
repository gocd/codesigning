/*
 * Copyright 2019 ThoughtWorks, Inc.
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

def fetchArtifactTask = { String osType ->
  return new FetchArtifactTask(false, {
    pipeline = 'installers'
    stage = 'dist'
    job = 'dist'
    source = "dist/${osType}"
    destination = "codesigning/src"
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
def createRakeTask = { String osType ->
  return new ExecTask({
    commandLine = ["rake", "--trace", "${osType}:sign"]
    workingDir = 'codesigning'
  })
}

def publishArtifactTask = { String osType ->
  return new BuildArtifact('build', {
    source = "codesigning/out"
    destination = "out"
  })
}

def getArtifact = { String source1 ->
  return new FetchArtifactTask(false, {
    pipeline = 'code-sign'
    file = true
    stage = 'aggregate-jsons'
    job = 'aggregate-jsons'
    source = "out/latest.json"
    destination = "codesigning"
  })
}

def secretParam = { String param ->
  return "{{SECRET:[build-pipelines][$param]}}".toString()
}

def environmentVariableForGoCD = [
  GOCD_GPG_PASSPHRASE  : secretParam("GOCD_GPG_PASSPHRASE"),
  AWS_ACCESS_KEY_ID    : secretParam("AWS_ACCESS_KEY_ID_FOR_GOCD"),
  AWS_SECRET_ACCESS_KEY: secretParam("AWS_SECRET_ACCESS_KEY_FOR_GOCD")
]

def environmentVariableForUpdateChannel = [
  GOCD_GPG_PASSPHRASE  : secretParam("GOCD_GPG_PASSPHRASE"),
  AWS_ACCESS_KEY_ID    : secretParam("AWS_ACCESS_KEY_ID_FOR_UPDATE_CHANNEL"),
  AWS_SECRET_ACCESS_KEY: secretParam("AWS_SECRET_ACCESS_KEY_FOR_UPDATE_CHANNEL")
]

def environmentVariableForAddons = [
  AWS_ACCESS_KEY_ID    : secretParam("AWS_ACCESS_KEY_ID_FOR_ADDONS"),
  AWS_SECRET_ACCESS_KEY: secretParam("AWS_SECRET_ACCESS_KEY_FOR_ADDONS")
]

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
        'STABLE_DOWNLOAD_BUCKET'      : 'downloadgocdio-downloadgocdios3-192sau789jtkh',
        'EXPERIMENTAL_DOWNLOAD_BUCKET': 'downloadgocdio-experimentaldownloadss3-dakr8wkhi2bo/experimental',
        'UPDATE_CHECK_BUCKET'         : 'updategocdio-updategocdios3-1ujj23u8hpqdl'
      ]

      materials() {
        git('codesigning') {
          url = 'https://github.com/gocd/codesigning'
          destination = "codesigning"
          blacklist = ["**/*.*", "**/*"]
        }
        git('signing-keys') {
          url = "https://git.gocd.io/git/gocd/signing-keys"
          destination = "signing-keys"
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
          //credentials for gocd experimental builds
          environmentVariables = environmentVariableForGoCD

          jobs {
            job('rpm') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                addAll(cleanTasks())
                add(fetchArtifactTask('rpm'))
                add(fetchArtifactTask('meta'))
                bash {
                  commandString = "bundle"
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace rpm:sign rpm:upload[${EXPERIMENTAL_DOWNLOAD_BUCKET}] yum:createrepo[${EXPERIMENTAL_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
            job('deb') {
              elasticProfileId = 'ubuntu'
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
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                add(fetchArtifactTask('zip'))
                add(fetchArtifactTask('meta'))
                bash {
                  commandString = "bundle"
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace zip:sign zip:upload[${EXPERIMENTAL_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
            job('win') {
              elasticProfileId = 'window-dev-build'
              tasks {
                addAll(cleanTasks())
                add(fetchArtifactTask('win'))
                add(fetchArtifactTask('meta'))
                exec {
                  commandLine = ['rake', '--trace', 'win:sign', 'win:upload[%EXPERIMENTAL_DOWNLOAD_BUCKET%]']
                  workingDir = 'codesigning'
                }
              }
            }
            job('osx') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                add(fetchArtifactTask('osx'))
                add(fetchArtifactTask('meta'))
                bash {
                  commandString = "bundle"
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = 'bundle exec rake --trace osx:sign_as_zip osx:upload[${EXPERIMENTAL_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }
            job('upload-docker-image') {
              elasticProfileId = 'ecs-gocd-dev-build-dind'
              environmentVariables = [
                DOCKERHUB_USERNAME: secretParam("DOCKERHUB_USER"),
                DOCKERHUB_PASSWORD: secretParam("DOCKERHUB_PASS"),
                DOCKERHUB_TOKEN   : secretParam("DOCKERHUB_TOKEN")
              ]

              tasks {
                fetchArtifact {
                  job = 'docker-server'
                  pipeline = 'installers'
                  runIf = 'passed'
                  source = 'docker-server'
                  stage = 'docker'
                  destination = "codesigning"
                }
                fetchArtifact {
                  job = 'docker-agent'
                  pipeline = 'installers'
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
                  commandString = "bundle exec rake docker:upload_experimental_docker_images"
                  workingDir = 'codesigning'
                }
                exec {
                  commandLine = ['npm', 'install']
                  workingDir = "codesigning"
                }
                bash {
                  commandString = 'node lib/update_dockerhub_full_description.js gocdexperimental'
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
              }
            }
          }
        }

        stage('aggregate-jsons') {
          //credentials for gocd experimental builds
          environmentVariables = environmentVariableForGoCD + [
            DOCKERHUB_ORG     : 'gocdexperimental',
            DOCKERHUB_USERNAME: secretParam("DOCKERHUB_USER"),
            DOCKERHUB_PASSWORD: secretParam("DOCKERHUB_PASS"),
            DOCKERHUB_TOKEN   : secretParam("DOCKERHUB_TOKEN")
          ]
          jobs {
            job('aggregate-jsons') {
              elasticProfileId = 'ecs-gocd-dev-build'
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
                  commandString = "bundle"
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
          //credentials for gocd update channel
          environmentVariables = environmentVariableForUpdateChannel

          jobs {
            job('generate') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                addAll(cleanTasks())
                add(fetchArtifactTask('meta'))
                add(getArtifact())
                bash {
                  commandString = "bundle"
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
          environmentVariables = environmentVariableForGoCD

          jobs {
            job('invalidate-distributions') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                bash {
                  commandString = "bundle"
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
            'AUTO_RELEASE_TO_CENTRAL': 'true',
            'EXPERIMENTAL_RELEASE'   : 'true',
            'MAVEN_NEXUS_USERNAME'   : 'arvindsv',
            'GOCD_GPG_PASSPHRASE'    : secretParam("GOCD_GPG_PASSPHRASE"),
            'MAVEN_NEXUS_PASSWORD'   : secretParam("ARVINDSV_NEXUS_PASSWORD")
          ]
          jobs {
            job('upload-maven') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                  destination = "codesigning"
                }
                fetchArtifact {
                  job = 'dist'
                  pipeline = 'installers'
                  runIf = 'passed'
                  source = 'go-plugin-api'
                  stage = 'dist'
                  destination = "codesigning"
                }
                fetchArtifact {
                  job = 'dist'
                  pipeline = 'installers'
                  runIf = 'passed'
                  source = 'go-plugin-config-repo'
                  stage = 'dist'
                  destination = "codesigning"
                }
                bash {
                  commandString = "bundle"
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = "git pull && bundle exec rake maven:upload_to_maven"
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
        'STABLE_DOWNLOAD_BUCKET'      : 'downloadgocdio-downloadgocdios3-192sau789jtkh',
        'EXPERIMENTAL_DOWNLOAD_BUCKET': 'downloadgocdio-experimentaldownloadss3-dakr8wkhi2bo/experimental',
        'UPDATE_CHECK_BUCKET'         : 'updategocdio-updategocdios3-1ujj23u8hpqdl',
        'ADDONS_EXPERIMENTAL_BUCKET'  : 'mini-apps-extensionsexperimentaldownloadss3-hare386lt2d9/addons/experimental',
        'ADDONS_STABLE_BUCKET'        : 'mini-apps-extensionsdownloadss3-11t0jfofrxhyd/addons',
        'REALLY_REALLY_UPLOAD'        : ''
      ]

      materials() {
        git('codesigning') {
          url = 'https://github.com/gocd/codesigning'
          destination = "codesigning"
          blacklist = ["**/*.*", "**/*"]
        }
        git('signing-keys') {
          url = "https://git.gocd.io/git/gocd/signing-keys"
          destination = "signing-keys"
        }
        dependency('code-sign') {
          pipeline = 'code-sign'
          stage = 'metadata'
        }
      }

      stages {
        stage('promote-binaries') {
          approval {
            type = 'manual'
          }
          //credentials for gocd experimental/stable builds
          environmentVariables = environmentVariableForGoCD
          jobs {
            job('promote-binaries') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                bash {
                  commandString = 'if [ "${REALLY_REALLY_UPLOAD}" != \'YES_I_REALLY_REALLY_WANT_TO_UPLOAD\' ]; then echo "REALLY_REALLY_UPLOAD environment variable should be overridden while triggering."; exit 1; fi'
                }
                fetchDirectory {
                  pipeline = 'installers/code-sign'
                  stage = 'dist'
                  job = 'dist'
                  source = "dist/meta"
                  destination = "codesigning/src"
                }
                bash {
                  commandString = "bundle"
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
          //credentials for gocd stable builds
          environmentVariables = environmentVariableForGoCD
          jobs {
            job('apt') {
              elasticProfileId = 'ubuntu'
              tasks {
                fetchDirectory {
                  pipeline = 'installers/code-sign'
                  stage = 'dist'
                  job = 'dist'
                  source = "dist/meta"
                  destination = "codesigning/src"
                }
                bash {
                  commandString = 'rake --trace apt:createrepo[${STABLE_DOWNLOAD_BUCKET}]'
                  workingDir = 'codesigning'
                }
              }
            }

            job('yum') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                fetchDirectory {
                  pipeline = 'installers/code-sign'
                  stage = 'dist'
                  job = 'dist'
                  source = "dist/meta"
                  destination = "codesigning/src"
                }
                bash {
                  commandString = "bundle"
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
          //credentials for gocd stable builds
          environmentVariables = environmentVariableForGoCD
          jobs {
            job('publish') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                fetchDirectory {
                  pipeline = 'installers/code-sign'
                  stage = 'dist'
                  job = 'dist'
                  source = "dist/meta"
                  destination = "codesigning/src"
                }
                bash {
                  commandString = "bundle"
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
          //credentials for gocd update channel
          environmentVariables = environmentVariableForUpdateChannel
          jobs {
            job('publish') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                fetchDirectory {
                  pipeline = 'installers/code-sign'
                  stage = 'dist'
                  job = 'dist'
                  source = "dist/meta"
                  destination = "codesigning/src"
                }
                bash {
                  commandString = "bundle"
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
