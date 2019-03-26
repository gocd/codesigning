
GoCD.script {
  pipelines {
    pipeline('updated_upgrade_helm_chart_version') {
      group = 'internal'
      labelTemplate = '${COUNT}'
      lockBehavior = 'none'
      materials {
        git('codesigning') {
          branch = 'master'
          destination = 'codesigning'
          shallowClone = false
          url = 'https://github.com/gocd/codesigning'
        }
        dependency('PromoteToStable') {
          pipeline = 'promote-stable-release'
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
            job('defaultJob') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [
                GIT_USERNAME: 'gocd-ci-user',
              ]
              runInstanceCount = '1'
              secureEnvironmentVariables = [
                GIT_PASSWORD: 'AES:7NcdUscgZG/MnwoKPJYeIw==:z2S32FA5xtXtcPoMe2cZK+1id6ejS5FI35VjaH6czxrZZ/UPQW6okuuYz2oqxLjN',
              ]
              tasks {
                fetchArtifact {
                  destination = 'codesigning'
                  job = 'dist'
                  pipeline = 'installers/code-sign/promote-stable-release'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                exec {
                  commandLine = ['npm', 'install']
                  runIf = 'passed'
                  workingDir = 'codesigning'
                }
                exec {
                  commandLine = ['node', 'bump_helm_chart_version.js']
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

