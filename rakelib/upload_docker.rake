require 'json'
require 'base64'

namespace :docker do

  task :dockerhub_login do
    creds = {
      :auths => {
        "https://index.docker.io/v1/" => {
          :auth => env("DOCKERHUB_TOKEN")
        }
      }
    }

    mkdir_p "#{Dir.home}/.docker"
    open("#{Dir.home}/.docker/config.json", "w") do |f|
      f.write(creds.to_json)
    end

  end

  desc "Upload experimental docker images to dockerhub"
  task :upload_experimental_docker_images => :dockerhub_login do

    %w[agent server].each do |type|
      manifest_files = Dir["docker-#{type}/manifest.json"]

      if manifest_files.length != 1
        raise "Found #{manifest_files.size} instead of 1."
      end

      manifest_files.each { |manifest|
        metadata = JSON.parse(File.read(manifest))

        metadata.each { |image|

          source_image = load_image_locally(image, type)
          destination_image = "#{get_docker_hub_name(image["imageName"], type)}:#{image["tag"]}"

          push_to_dockerhub(source_image, destination_image, true)
        }
      }
    end

  end

  desc 'Publish official/released docker images to dockerhub'
  task :publish_docker_images => :dockerhub_login do

    metadata = JSON.parse(File.read("version.json"))
    go_version = metadata['go_version']

    %w[agent server].each do |type|
      manifest_files = Dir["docker-#{type}/manifest.json"]

      if manifest_files.length != 1
        raise "Found #{manifest_files.size} instead of 1."
      end

      manifest_files.each { |manifest|
        metadata = JSON.parse(File.read(manifest))

        metadata.each { |image|

          source_image = load_image_locally(image, type)
          destination_image = "#{get_docker_hub_name(image["imageName"], type)}:v#{go_version}"

          push_to_dockerhub(source_image, destination_image, false)
        }
      }
    end
  end

  private

  def load_image_locally(image, type)
    oci_folder = "docker-#{type}/oci-#{image["imageName"].gsub('.', '-')}"
    source_image = "ocidir://#{oci_folder}:#{image["tag"]}"
    sh("regctl image import #{source_image} docker-#{type}/#{image["file"]}")
    source_image
  end

  def push_to_dockerhub(source_image, destination_image, exp = true)
    experimental_org = ENV['EXP_DOCKERHUB_ORG'] || 'gocdexperimental'
    stable_org = ENV['STABLE_DOCKERHUB_ORG'] || 'gocd'

    org = exp ? experimental_org : stable_org

    sh("regctl image copy #{source_image} #{org}/#{destination_image}")
  end

  def get_docker_hub_name(image_name, type)
    if type.to_s === "server" && image_name.include?('docker-')
      return image_name.gsub! "docker-", ""
    else
      return image_name
    end
    raise "Invalid type: #{type}"
  end

  def env(key)
    value = ENV[key].to_s.strip
    if !value || value.length == 0
      raise "Please specify #{key}"
    end
    value
  end

end
