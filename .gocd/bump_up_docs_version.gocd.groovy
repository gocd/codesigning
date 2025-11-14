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

GoCD.script {
  pipelines {
    pipeline('bump_up_docs_version') {
      environmentVariables = [
        GITHUB_USER : 'gocd-ci-user',
        ORG         : 'gocd',
        GITHUB_TOKEN: '{{SECRET:[build-pipelines][GOCD_CI_USER_RELEASE_TOKEN]}}'
      ]
      group = 'internal'
      labelTemplate = '${COUNT}'
      lockBehavior = 'none'
      materials {
        git('ReleaseActivityScripts') {
          branch = 'master'
          shallowClone = false
          url = 'https://github.com/gocd/release-activity-scripts'
          blacklist = ['**/*.*', '**/*']
        }
        dependency('PromoteToStable') {
          pipeline = 'PublishStableRelease'
          stage = 'publish-latest-json'
        }
      }
      stages {
        stage('bump_up_version') {
          artifactCleanupProhibited = false
          cleanWorkingDir = false
          fetchMaterials = true
          approval {
          }
          jobs {
            job('api.go.cd') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                bash {
                  commandString = 'bundle install'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                bash {
                  commandString = 'REPO_NAME=api.go.cd bundle exec rake bump_docs_version'
                }
              }
            }
            job('plugin-api.go.cd') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                bash {
                  commandString = 'bundle install'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                bash {
                  commandString = 'REPO_NAME=plugin-api.go.cd bundle exec rake bump_docs_version'
                }
              }
            }
            job('developer.go.cd') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                bash {
                  commandString = 'bundle install'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                bash {
                  commandString = 'REPO_NAME=developer.go.cd bundle exec rake bump_docs_version'
                }
              }
            }
            job('docs.go.cd') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                bash {
                  commandString = 'bundle install'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                bash {
                  commandString = 'REPO_NAME=docs.go.cd bundle exec rake bump_docs_version'
                }
              }
            }
          }
        }
      }
    }
  }
}

