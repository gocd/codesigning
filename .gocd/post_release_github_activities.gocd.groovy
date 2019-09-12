
GoCD.script {
  pipelines {
    pipeline('post-release-github-activities') {
      group = 'internal'
      labelTemplate = '${COUNT}'
      lockBehavior = 'none'
      materials {
        git('codesigning') {
          branch = 'master'
          destination = 'codesigning'
          shallowClone = false
          url = 'https://git.gocd.io/git/gocd/codesigning'
          blacklist = ["**/*.*", "**/*"]
        }
        dependency('PromoteToStable') {
          pipeline = 'PublishStableRelease'
          stage = 'publish-latest-json'
        }
      }
      stages {
        stage('draft-release') {
          artifactCleanupProhibited = false
          cleanWorkingDir = false
          fetchMaterials = true
          approval {
          }
          jobs {
            job('draft-release') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [GITHUB_TOKEN: "{{SECRET:[build-pipelines][GOCD_CI_USER_RELEASE_TOKEN]}}"]
              timeout = 0
              tasks {
                fetchArtifact {
                  destination = 'codesigning'
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
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
                  commandLine = ['node', 'lib/draft_new_release.js']
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

