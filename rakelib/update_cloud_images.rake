task :update_cloud_images do
  require 'time'
  require 'aws-sdk'
  require 'rest-client'
  require 'json'
  require_relative '../lib/version_file_reader'

  version = VersionFileReader.go_version
  stable_bucket_url = env("STABLE_DOWNLOAD_BUCKET")
  release_time = Time.now.utc
  cloud_images_for_version = {
    go_version: version,
    release_time_readable: release_time.xmlschema,
    release_time: release_time.to_i,
    server_docker: [{image_name: 'gocd-server'}],
    agents_docker: docker_agents(version)
  }

  s3_client = Aws::S3::Client.new(region: 'us-east-1')

  begin
    response = s3_client.get_object(bucket: stable_bucket_url, key: 'cloud.json')
  rescue Aws::S3::Errors::NoSuchKey
    File.open('cloud.json', 'w') { |f| f.write([cloud_images_for_version].to_json) }
    puts "Creating #{stable_bucket_url}/cloud.json"
    s3_client.put_object({
                           acl: "public-read",
                           body: File.read('cloud.json'),
                           bucket: stable_bucket_url,
                           cache_control: "max-age=600",
                           content_type: 'application/json',
                           content_md5: Digest::MD5.file('cloud.json').base64digest,
                           key: 'cloud.json'
                         })
  end
  unless response.nil?
    cloud_images_from_bucket = JSON.parse(response.body.string)
    cloud_images_from_bucket.delete_if { |hash| hash['go_version'] == version || hash[:go_version] == version }
    cloud_images_from_bucket << cloud_images_for_version
    to_be_uploaded = cloud_images_from_bucket.sort_by { |hash| ::Gem::Version.new(hash['go_version'] || hash[:go_version]) }
    File.open('cloud.json', 'w') { |f| f.write(to_be_uploaded.to_json) }
    puts "Uploading cloud.json to #{stable_bucket_url}/cloud.json"
    s3_client.put_object({
                           acl: "public-read",
                           body: File.read('cloud.json'),
                           bucket: stable_bucket_url,
                           cache_control: "max-age=600",
                           content_type: 'application/json',
                           content_md5: Digest::MD5.file('cloud.json').base64digest,
                           key: 'cloud.json'
                         })
  end
end

private

def docker_agents(version)
  # dockerhub_token = env("DOCKERHUB_TOKEN")
  dockerhub_username = env("DOCKERHUB_USERNAME")
  dockerhub_password = env("DOCKERHUB_PASSWORD")
  org = env("DOCKERHUB_ORG")

  login = RestClient.post('https://hub.docker.com/v2/users/login/',{username: dockerhub_username, password: dockerhub_password}.to_json, {:accept => 'application/json', :content_type => 'application/json'})
  token = JSON.parse(login)['token']

  response = RestClient.get("https://hub.docker.com/v2/repositories/#{org}/?page_size=50", {:accept => 'application/json', :Authorization => "JWT #{token}"})
  all_repos = JSON.parse(response)

  agents = all_repos['results'].map do |repo|
    tags_response = RestClient.get("https://hub.docker.com/v2/repositories/#{org}/#{repo['name']}/tags", {:accept => 'application/json', :Authorization => "JWT #{token}"})
    all_tags = JSON.parse(tags_response)['results'].map { |tag| tag['name'] }
    {image_name: repo['name']} if (repo['name'].start_with?('gocd-agent-') && repo['name'] != 'gocd-agent-deprecated' && all_tags.include?("v#{version}"))
  end
  logout = RestClient.post('https://hub.docker.com/v2/logout/', {}, {:accept => 'application/json', :Authorization => "JWT #{token}"})
  agents.compact.sort_by { |agent| agent[:image_name] }
end

def env(key)
  value = ENV[key].to_s.strip
  raise "Please specify #{key}" unless value
  value
end
