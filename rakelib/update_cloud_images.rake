require 'time'
require 'aws-sdk'
require 'rest-client'
require 'json'
require_relative '../lib/version_file_reader'

task :update_cloud_images do
  version           = VersionFileReader.go_version
  stable_bucket_url = env("STABLE_DOWNLOAD_BUCKET")

  release_time             = Time.now.utc
  cloud_images_for_version = {
      go_version:            version,
      release_time_readable: release_time.xmlschema,
      release_time:          release_time.to_i,
      server_amis:           server_amis(version),
      demo_amis:             demo_amis(version),
      server_docker:         [{image_name: 'gocd-server'}],
      agents_docker:         docker_agents(version)
  }

  s3_client = Aws::S3::Client.new(region: 'us-east-1', credentials: Aws::Credentials.new(env("AWS_ACCESS_KEY_ID"), env("AWS_SECRET_ACCESS_KEY")))

  begin
    response = s3_client.get_object(bucket: s3_bucket, key: 'cloud.json')
  rescue Aws::S3::Errors::NoSuchKey
    File.open('cloud.json', 'w') {|f| f.write([cloud_images_for_version].to_json)}
    puts "Creating #{s3_bucket}/cloud.json"
    s3_client.put_object({
                             acl:           "public-read",
                             body:          File.read('cloud.json'),
                             bucket:        s3_bucket,
                             cache_control: "max-age=600",
                             content_type:  'application/json',
                             content_md5:   Digest::MD5.file('cloud.json').base64digest,
                             key:           'cloud.json'
                         })
  end
  unless response.nil?
    cloud_images_from_bucket = JSON.parse(response.body.string)
    cloud_images_from_bucket.delete_if {|hash| hash['go_version'] == version}
    cloud_images_from_bucket << cloud_images_for_version
    to_be_uploaded = cloud_images_from_bucket.sort_by {|hash| hash['go_version']}
    File.open('cloud.json', 'w') {|f| f.write(to_be_uploaded.to_json)}
    puts "Uploading cloud.json to #{stable_bucket_url}/cloud.json"
    s3_client.put_object({
                             acl:           "public-read",
                             body:          File.read('cloud.json'),
                             bucket:        stable_bucket_url,
                             cache_control: "max-age=600",
                             content_type:  'application/json',
                             content_md5:   Digest::MD5.file('cloud.json').base64digest,
                             key:           'cloud.json'
                         })
  end
end

private

def server_amis(version)
  get_amis('Server', version)
end

def demo_amis(version)
  get_amis('Demo', version)
end

def get_amis(type, version)
  amis    = []
  regions = Aws.partition('aws').regions.map {|region| region.name}
  regions.each do |region|
    owned_images = image_list(region, type, version)
    populate_amis(amis, region, owned_images)
  end
  amis
end

def docker_agents(version)
  dockerhub_username = env("DOCKERHUB_USERNAME")
  dockerhub_password = env("DOCKERHUB_PASSWORD")
  org                = env("DOCKERHUB_ORG")

  login = RestClient.post('https://hub.docker.com/v2/users/login/', {username: dockerhub_username, password: dockerhub_password}.to_json, {:accept => 'application/json', :content_type => 'application/json'})
  token = JSON.parse(login)['token']

  response  = RestClient.get("https://hub.docker.com/v2/repositories/#{org}/?page_size=50", {:accept => 'application/json', :Authorization => "JWT #{token}"})
  all_repos = JSON.parse(response)

  agents = all_repos['results'].map do |repo|
    tags_response = RestClient.get("https://hub.docker.com/v2/repositories/#{org}/#{repo['name']}/tags", {:accept => 'application/json', :Authorization => "JWT #{token}"})
    all_tags      = JSON.parse(tags_response)['results'].map {|tag| tag['name']}
    {image_name: repo['name']} if (repo['name'].start_with?('gocd-agent-') && repo['name'] != 'gocd-agent-deprecated' && all_tags.include?("v#{version}"))
  end
  agents.compact.sort_by {|agent| agent[:image_name]}
  logout = RestClient.post('https://hub.docker.com/v2/logout/', {}, {:accept => 'application/json', :Authorization => "JWT #{token}"})
end


def populate_amis(all_amis, region, self_owned_images)
  self_owned_images.images.each do |image|
    all_amis << image_details(image, region)
  end
end

def image_list(region, type, version)
  ec2_client = Aws::EC2::Client.new(region: region)
  ec2_client.describe_images({
                                 owners:  ["self"],
                                 filters: [
                                              {
                                                  name:   "tag:GoCD-Version",
                                                  values: ["#{version}"]
                                              },
                                              {
                                                  name:   "tag:Name",
                                                  values: ["GoCD #{type} #{version}"]
                                              },
                                              {
                                                  name:   "state",
                                                  values: ["available"]
                                              }
                                          ]
                             })
end

def image_details(image, region)
  {
      region:     region,
      ami_id:     image.image_id,
      image_name: image.name,
      href:       "https://console.aws.amazon.com/ec2/home?region=#{region}#launchAmi=#{image.image_id}"
  }
end

def env(key)
  value = ENV[key].to_s.strip
  raise "Please specify #{key}" unless value
  value
end
