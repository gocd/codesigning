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
          blacklist = ["**/*.*", "**/*"]
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
                exec {
                  commandLine = ['bash', '-c', 'bundle']
                  runIf = 'passed'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                exec {
                  commandLine = ['bash', '-c', 'REPO_NAME=api.go.cd bundle exec rake bump_docs_version']
                  runIf = 'passed'
                }
              }
            }
            job('plugin-api.go.cd') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle']
                  runIf = 'passed'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                exec {
                  commandLine = ['bash', '-c', 'REPO_NAME=plugin-api.go.cd bundle exec rake bump_docs_version']
                  runIf = 'passed'
                }
              }
            }
            job('developer.go.cd') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle']
                  runIf = 'passed'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                exec {
                  commandLine = ['bash', '-c', 'REPO_NAME=developer.go.cd bundle exec rake bump_docs_version']
                  runIf = 'passed'
                }
              }
            }
            job('docs.go.cd') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle']
                  runIf = 'passed'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                exec {
                  commandLine = ['bash', '-c', 'REPO_NAME=docs.go.cd bundle exec rake bump_docs_version']
                  runIf = 'passed'
                }
              }
            }
          }
        }
      }
    }
  }
}

