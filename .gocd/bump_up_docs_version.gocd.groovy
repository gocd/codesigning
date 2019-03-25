GoCD.script {
  pipelines {
    pipeline('updated_bump_up_docs_version') {
      environmentVariables = [
        GITHUB_USER: 'gocd-ci-user',
        ORG        : 'gocd',
      ]
      group = 'internal'
      labelTemplate = '${COUNT}'
      lockBehavior = 'none'
      secureEnvironmentVariables = [
        GITHUB_TOKEN: 'AES:BpIf28a8CFYsdHCtQ1W2ag==:669s8sbasqeh07SxM+n1rxxuWFcHqRkcUmiNchOSeHJg88br3rEeHvFAK9Jt5nAG',
      ]
      materials {
        git('ReleaseActivityScripts') {
          branch = 'master'
          shallowClone = false
          url = 'https://github.com/gocd/release-activity-scripts'
        }
        dependency('PromoteToStable') {
          pipeline = 'promote-stable-release'
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
              runInstanceCount = '1'
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle install --path .bundle --binstubs']
                  runIf = 'passed'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/promote-stable-release'
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
              runInstanceCount = '1'
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle install --path .bundle --binstubs']
                  runIf = 'passed'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/promote-stable-release'
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
              runInstanceCount = '1'
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle install --path .bundle --binstubs']
                  runIf = 'passed'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/promote-stable-release'
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
              runInstanceCount = '1'
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle install --path .bundle --binstubs']
                  runIf = 'passed'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/promote-stable-release'
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
            job('extensions-docs.gocd.org') {
              elasticProfileId = 'ecs-gocd-dev-build'
              runInstanceCount = '1'
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle install --path .bundle --binstubs']
                  runIf = 'passed'
                }
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/promote-stable-release'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                exec {
                  commandLine = ['bash', '-c', 'bundle exec rake bump_extensions_doc_version']
                  runIf = 'passed'
                }
              }
            }
          }
        }
        stage('create_docs_pipeline') {
          artifactCleanupProhibited = false
          cleanWorkingDir = false
          fetchMaterials = true
          approval {
          }
          jobs {
            job('api.go.cd') {
              elasticProfileId = 'ecs-gocd-dev-build'
              runInstanceCount = '1'
              tasks {
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/promote-stable-release'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                exec {
                  commandLine = ['bash', '-c', 'bundle install --path .bundle --binstubs']
                  runIf = 'passed'
                }
                exec {
                  commandLine = ['bash', '-c', 'PIPELINE_CONFIG_FORMAT=json REPO_NAME=api.go.cd bundle exec rake create_pipeline']
                  runIf = 'passed'
                }
              }
            }
            job('plugin-api.go.cd') {
              elasticProfileId = 'ecs-gocd-dev-build'
              runInstanceCount = '1'
              tasks {
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/promote-stable-release'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                exec {
                  commandLine = ['bash', '-c', 'bundle install --path .bundle --binstubs']
                  runIf = 'passed'
                }
                exec {
                  commandLine = ['bash', '-c', 'PIPELINE_CONFIG_FORMAT=yaml REPO_NAME=plugin-api.go.cd bundle exec rake create_pipeline']
                  runIf = 'passed'
                }
              }
            }
            job('developer.go.cd') {
              elasticProfileId = 'ecs-gocd-dev-build'
              runInstanceCount = '1'
              tasks {
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/promote-stable-release'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                }
                exec {
                  commandLine = ['bash', '-c', 'bundle install --path .bundle --binstubs']
                  runIf = 'passed'
                }
                exec {
                  commandLine = ['bash', '-c', 'PIPELINE_CONFIG_FORMAT=yaml REPO_NAME=developer.go.cd bundle exec rake create_pipeline']
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

