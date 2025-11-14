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
    pipeline('upgrade-helm-chart-version') {
      group = 'internal'
      labelTemplate = '${COUNT}'
      lockBehavior = 'none'
      materials {
        git('codesigning') {
          branch = 'master'
          destination = 'codesigning'
          shallowClone = false
          url = 'https://github.com/gocd/codesigning'
          blacklist = ['**/*.*', '**/*']
        }
        dependency('PromoteToStable') {
          pipeline = 'PublishStableRelease'
          stage = 'publish-latest-json'
        }
      }
      stages {
        stage('send_pr_to_gocd_helm_chart_repo') {
          artifactCleanupProhibited = false
          cleanWorkingDir = false
          fetchMaterials = true
          approval {
          }
          jobs {
            job('update_at_gocd_chart_repo') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [
                GIT_USERNAME: 'gocd-ci-user',
                GIT_PASSWORD: '{{SECRET:[build-pipelines][GOCD_CI_USER_RELEASE_TOKEN]}}',
              ]
              tasks {
                fetchArtifact {
                  destination = 'codesigning'
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                  file = true
                }
                bash {
                  commandString = 'npm install && node lib/bump_gocd_helm_chart_version.js'
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

