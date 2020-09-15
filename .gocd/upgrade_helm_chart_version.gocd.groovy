
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
          blacklist = ["**/*.*", "**/*"]
        }
        dependency('PromoteToStable') {
          pipeline = 'PublishStableRelease'
          stage = 'publish-latest-json'
        }
      }
      stages {
        stage('send_pr_to_helm_chart_repo') {
          artifactCleanupProhibited = false
          cleanWorkingDir = false
          fetchMaterials = true
          approval {
          }
          jobs {
            job('update_at_helm_chart_repo') {
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
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                  file = true
                }
                exec {
                  commandLine = ['npm', 'install']
                  runIf = 'passed'
                  workingDir = 'codesigning'
                }
                exec {
                  commandLine = ['node', 'lib/bump_helm_chart_version.js']
                  runIf = 'passed'
                  workingDir = 'codesigning'
                }
              }
            }
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
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                  file = true
                }
                exec {
                  commandLine = ['npm', 'install']
                  runIf = 'passed'
                  workingDir = 'codesigning'
                }
                exec {
                  commandLine = ['node', 'lib/bump_gocd_helm_chart_version.js']
                  runIf = 'passed'
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

