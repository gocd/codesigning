GoCD.script {
  pipelines {
    pipeline('upload-to-maven') {
      environmentVariables = [
        GIT_USER: 'gocd-ci-user',
      ]
      group = 'internal'
      labelTemplate = '${COUNT}'
      lockBehavior = 'none'
      secureEnvironmentVariables = [
        GIT_PASSWORD: 'AES:VamvCdi7OX38zp33L7SJbw==:lm7xodTUI06gb39yj/qhX6zmxlkFuCjUx0+HHV5kn+ynJ2PNqfOMu1LmQio0u+Tj'
      ]
      materials {
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
          approval {
            type = 'success'
          }
          environmentVariables = [
            'AUTO_RELEASE_TO_CENTRAL': 'true',
            'EXPERIMENTAL_RELEASE'   : 'false',
            'MAVEN_NEXUS_USERNAME'   : 'arvindsv'
          ]
          secureEnvironmentVariables = [
            'MAVEN_NEXUS_PASSWORD': 'AES:U0+58CAsIkycH+6DUL+Z6w==:EoTd+MQsXP8iL64+eDUi226NbEOGM3N6RfYxZeXH6C30X70xcKKuaEuFVLATe92Ht9RDNrMhXbv2lAt/iEoEbA=='
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
                  commandString = "bundle install --jobs 4 --path .bundle --clean"
                  workingDir = 'codesigning'
                }
                bash {
                  commandString = "bundle exec rake upload_to_maven"
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

