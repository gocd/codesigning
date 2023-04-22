def secretParam = { String param ->
  return "{{SECRET:[build-pipelines][$param]}}".toString()
}

GoCD.script {
  pipelines {
    pipeline('publish-cloud-based-artifacts') {
      environmentVariables = [
        AWS_ACCESS_KEY_ID    : 'AKIAVL5CITUNP4CUFG4E',
        GOCD_STABLE_RELEASE  : 'true',
        GIT_USER             : 'gocd-ci-user',
        version              : '',
        revision             : '',
        GIT_PASSWORD         : secretParam("GOCD_CI_USER_RELEASE_TOKEN"),
        AWS_SECRET_ACCESS_KEY: secretParam("AWS_SECRET_KEY_FOR_PUBLISH_RELEASE")
      ]
      group = 'internal'
      labelTemplate = '${COUNT}'
      lockBehavior = 'none'
      materials {
        git('GocdChocolatey') {
          branch = 'master'
          shallowClone = true
          url = 'https://git.gocd.io/git/gocd/gocd-chocolatey'
          destination = 'gocd-chocolatey'
          autoUpdate = true
          blacklist = ["**/*.*", "**/*"]
        }
        git('CodeSigning') {
          branch = 'master'
          shallowClone = false
          url = 'https://git.gocd.io/git/gocd/codesigning'
          destination = 'codesigning'
          autoUpdate = true
          blacklist = ["**/*.*", "**/*"]
        }
        git('GoCD') {
          branch = 'master'
          shallowClone = false
          url = 'https://git.gocd.io/git/gocd/gocd'
          destination = 'gocd'
          autoUpdate = true
          blacklist = ["**/*.*", "**/*"]
        }
        dependency('PromoteToStable') {
          pipeline = 'PublishStableRelease'
          stage = 'publish-latest-json'
        }
      }
      stages {
        stage('publish') {
          artifactCleanupProhibited = false
          cleanWorkingDir = true
          fetchMaterials = true
          approval {
            type = 'success'
          }
          jobs {
            job('publish-all-docker-images') {
              elasticProfileId = 'ecs-gocd-dev-build-dind'
              environmentVariables = [
                DOCKERHUB_USERNAME: secretParam("DOCKERHUB_USER"),
                DOCKERHUB_PASSWORD: secretParam("DOCKERHUB_PASS"),
                DOCKERHUB_TOKEN   : secretParam("DOCKERHUB_TOKEN")
              ]
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
                  job = 'docker-server'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'docker-server'
                  stage = 'docker'
                  destination = "codesigning"
                }
                fetchArtifact {
                  job = 'docker-agent'
                  pipeline = 'installers/code-sign/PublishStableRelease'
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
                  commandString = "bundle exec rake docker:publish_docker_images"
                  workingDir = 'codesigning'
                }
                fetchArtifact {
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'dist/zip'
                  stage = 'dist'
                  destination = "gocd"
                }
                bash {
                  commandString = 'git config --global user.email "godev+gocd-ci-user@thoughtworks.com"'
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
                bash {
                  commandString = './gradlew --parallel --max-workers 4 docker:assemble -PskipDockerBuild -PdockerbuildServerZipLocation=\$(readlink -f zip/go-server-*.zip) -PdockerbuildAgentZipLocation=\$(readlink -f zip/go-agent-*.zip) -PdockerGitPush="I_REALLY_WANT_TO_DO_THIS"'
                  workingDir = 'gocd'
                }
              }
            }
            job('choco-server') {
              elasticProfileId = 'window-dev-build'
              environmentVariables = [
                version : '',
                revision: '',
                apiKey  : secretParam("CHOCO_API_KEY")
              ]
              tasks {
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                  destination = ""
                }
                exec {
                  commandLine = ['powershell', '-ExecutionPolicy',
                                 'ByPass',
                                 '-File',
                                 '.\\createPackage.ps1',
                                 'server']
                  runIf = 'passed'
                  workingDir = "gocd-chocolatey"
                }
                exec {
                  commandLine = ['powershell', '$env:version=(Get-Content \'..\\version.json\' | ConvertFrom-Json).go_version; choco push gocd-server\\gocdserver.$env:version.nupkg -k $env:apiKey']
                  runIf = 'passed'
                  workingDir = "gocd-chocolatey"
                }
              }
            }
            job('choco-agent') {
              elasticProfileId = 'window-dev-build'
              environmentVariables = [
                version : '',
                revision: '',
                apiKey  : secretParam("CHOCO_API_KEY")
              ]
              tasks {
                fetchArtifact {
                  file = true
                  job = 'dist'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'dist/meta/version.json'
                  stage = 'dist'
                  destination = ""
                }
                exec {
                  commandLine = ['powershell', '-ExecutionPolicy',
                                 'ByPass',
                                 '-File',
                                 '.\\createPackage.ps1',
                                 'agent']
                  runIf = 'passed'
                  workingDir = "gocd-chocolatey"
                }
                exec {
                  commandLine = ['powershell', '$env:version=(Get-Content \'..\\version.json\' | ConvertFrom-Json).go_version; choco push gocd-agent\\gocdagent.$env:version.nupkg -k $env:apiKey']
                  runIf = 'passed'
                  workingDir = "gocd-chocolatey"
                }
              }
            }
          }
        }
        stage('post-publish') {
          artifactCleanupProhibited = false
          cleanWorkingDir = false
          fetchMaterials = true
          approval {
            type = 'success'
          }
          environmentVariables = [
            'EXPERIMENTAL_DOWNLOAD_BUCKET': 'downloadgocdio-experimentaldownloadss3-dakr8wkhi2bo/experimental',
            'STABLE_DOWNLOAD_BUCKET'      : 'downloadgocdio-downloadgocdios3-192sau789jtkh',
            'DOCKERHUB_TOKEN'             : secretParam("DOCKERHUB_TOKEN")
          ]
          jobs {
            job('empty_exp_bucket') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [
                AWS_ACCESS_KEY_ID    : secretParam("AWS_ACCESS_KEY_ID_FOR_EXP_RELEASE"),
                AWS_SECRET_ACCESS_KEY: secretParam("AWS_SECRET_KEY_FOR_EXP_RELEASE")
              ]
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle && bundle exec rake empty_experimental_bucket']
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
                exec {
                  commandLine = ['bash', '-c', 'bundle exec rake metadata:cleanup_cloud_json[${EXPERIMENTAL_DOWNLOAD_BUCKET}]']
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
              }
            }
            job('update_cloud_images') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [
                REGION            : 'us-east-2',
                DOCKERHUB_ORG     : 'gocd',
                DOCKERHUB_USERNAME: secretParam("DOCKERHUB_USER"),
                DOCKERHUB_PASSWORD: secretParam("DOCKERHUB_PASS"),
                DOCKERHUB_TOKEN   : secretParam("DOCKERHUB_TOKEN")
              ]
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
                exec {
                  commandLine = ['bash', '-c', 'bundle && bundle exec rake update_cloud_images']
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
              }
            }
            job('docker_cleanup') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [
                DOCKERHUB_ORG     : 'gocdexperimental',
                DOCKERHUB_USERNAME: secretParam("DOCKERHUB_USER"),
                DOCKERHUB_PASSWORD: secretParam("DOCKERHUB_PASS"),
                DOCKERHUB_TOKEN   : secretParam("DOCKERHUB_TOKEN")
              ]
              tasks {
                exec {
                  commandLine = ['bash', '-c', 'bundle && bundle exec rake cleanup_docker']
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
              }
            }
            job('update_dockerhub_full_description') {
              elasticProfileId = 'ecs-gocd-dev-build'
              environmentVariables = [
                DOCKERHUB_ORG     : 'gocdexperimental',
                DOCKERHUB_USERNAME: secretParam("DOCKERHUB_USER"),
                DOCKERHUB_PASSWORD: secretParam("DOCKERHUB_PASS"),
                DOCKERHUB_TOKEN   : secretParam("DOCKERHUB_TOKEN")
              ]
              tasks {
                fetchArtifact {
                  job = 'docker-server'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'docker-server'
                  stage = 'docker'
                  destination = "codesigning"
                }
                fetchArtifact {
                  job = 'docker-agent'
                  pipeline = 'installers/code-sign/PublishStableRelease'
                  runIf = 'passed'
                  source = 'docker-agent'
                  stage = 'docker'
                  destination = "codesigning"
                }
                exec {
                  commandLine = ['npm', 'install']
                  workingDir = "codesigning"
                }
                exec {
                  commandLine = ['bash', '-c', 'node lib/update_dockerhub_full_description.js gocd']
                  runIf = 'passed'
                  workingDir = "codesigning"
                }
              }
            }
          }
        }
      }
    }
  }
}

