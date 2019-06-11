desc 'renaming the relevant files with relevant suffix'
task :upload_to_maven, [:experimental] do |t, args|
  require 'json'
  require_relative '../lib/version_file_reader'

  go_full_version = VersionFileReader.go_full_version
  go_version      = VersionFileReader.go_version


  isExperimental        = args[:experimental]
  maven_release_version = isExperimental ? go_full_version : go_version
  artifact_suffix       = isExperimental ? "-exp" : ""

  {
      "go-plugin-api/go-plugin-api-#{go_full_version}.jar"                         => "go-plugin-api/go-plugin-api#{artifact_suffix}-#{maven_release_version}.jar",
      "go-plugin-api/go-plugin-api-#{go_full_version}-javadoc.jar"                 => "go-plugin-api/go-plugin-api#{artifact_suffix}-#{maven_release_version}-javadoc.jar",
      "go-plugin-api/go-plugin-api-#{go_full_version}-sources.jar"                 => "go-plugin-api/go-plugin-api#{artifact_suffix}-#{maven_release_version}-sources.jar",
      "go-plugin-config-repo/go-plugin-config-repo-#{go_full_version}.jar"         => "go-plugin-config-repo/go-plugin-config-repo#{artifact_suffix}-#{maven_release_version}.jar",
      "go-plugin-config-repo/go-plugin-config-repo-#{go_full_version}-javadoc.jar" => "go-plugin-config-repo/go-plugin-config-repo#{artifact_suffix}-#{maven_release_version}-javadoc.jar",
      "go-plugin-config-repo/go-plugin-config-repo-#{go_full_version}-sources.jar" => "go-plugin-config-repo/go-plugin-config-repo#{artifact_suffix}-#{maven_release_version}-sources.jar"
  }.each do |original_name, new_name|
    if original_name != new_name
      cp original_name, new_name
    end
  end


  %w(go-plugin-api go-plugin-config-repo).each do |artifact_name|
    pom_content = File.read('deploy-pom.xml')
                      .gsub('${goReleaseVersion}', maven_release_version)
                      .gsub('${artifact}', "#{artifact_name}#{artifact_suffix}")
                      .gsub('${desc}', description_for(artifact_name))
                      .gsub('${name}', name_for(artifact_name))

    File.open("#{artifact_name}/pom.xml", 'w') {|f| f.puts pom_content}

    cd "#{artifact_name}" do
      sh("mvn -DautoReleaseToCentral=true --batch-mode deploy")
    end

  end
end

private

def description_for(artifact)
  if artifact == "go-plugin-api"
    "The APIs described here are needed for developing plugins for GoCD - A continuous delivery server"
  elsif artifact == "go-plugin-config-repo"
    "The APIs described here are needed for developing pipeline as code plugins for GoCD - A continuous delivery server"
  end
end

def name_for(artifact)
  if artifact == "go-plugin-api"
    "API for plugins of GoCD"
  elsif artifact == "go-plugin-config-repo"
    "Contract for pipeline as code plugins of GoCD"
  end
end
