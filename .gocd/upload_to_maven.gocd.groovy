def secretParam = { String param ->
  return "{{SECRET:[build-pipelines][$param]}}".toString()
}

GoCD.script {
  pipelines {
    pipeline('upload-to-maven') {
      environmentVariables = [
        GIT_USER: 'gocd-ci-user',
      ]
      group = 'internal'
      labelTemplate = '${COUNT}'
      lockBehavior = 'none'
      environmentVariables = [
        GOCD_GPG_PASSPHRASE: secretParam("GOCD_GPG_PASSPHRASE"),
        GIT_PASSWORD       : secretParam("GOCD_CI_USER_RELEASE_TOKEN")
      ]
      materials {
        git('signing-keys') {
          url = "https://git.gocd.io/git/gocd/signing-keys"
          destination = "signing-keys"
        }
        git('CodeSigning') {
          branch = 'master'
          shallowClone = false
          url = 'https://git.gocd.io/git/gocd/codesigning'
          destination = 'codesigning'
          autoUpdate = true
          blacklist = ["**/*.*", "**/*"]
        }
        dependency('PromoteToStable') {
          pipeline = 'PublishStableRelease'
          stage = 'publish-latest-json'
        }
      }
      stages {
        stage('upload') {
          artifactCleanupProhibited = false
          cleanWorkingDir = true
          fetchMaterials = true
          environmentVariables = [
            'AUTO_RELEASE_TO_CENTRAL': 'true',
            'EXPERIMENTAL_RELEASE'   : 'false',
            'MAVEN_NEXUS_USERNAME'   : 'arvindsv',
            'MAVEN_NEXUS_PASSWORD'   : secretParam("ARVINDSV_NEXUS_PASSWORD")
          ]
          jobs {
            job('upload-to-maven') {
              elasticProfileId = 'ecs-gocd-dev-build'
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
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'go-plugin-api'
                  stage = 'dist'
                  destination = "codesigning"
                }
                fetchArtifact {
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
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
                  commandString = "bundle exec rake maven:upload_to_maven"
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

