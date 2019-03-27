

task :cleanup_docker do
  dockerhub_username = env("DOCKERHUB_USERNAME")
  dockerhub_password = env("DOCKERHUB_PASSWORD")
  org                = env("DOCKERHUB_ORG")

  raise "ORG can't be `gocd`! We can't delete the official stable images." if org.eql?("gocd")

  require 'rest-client'
  require 'json'

  login = RestClient.post('https://hub.docker.com/v2/users/login/', {username: dockerhub_username, password: dockerhub_password}.to_json, {:accept => 'application/json', :content_type => 'application/json'})
  token = JSON.parse(login)['token']

  response  = RestClient.get("https://hub.docker.com/v2/repositories/#{org}/?page_size=50", {:accept => 'application/json', :Authorization => "JWT #{token}"})
  all_repos = JSON.parse(response)

  agents = all_repos['results'].map do |repo|
    repo['name'] if (repo['name'].start_with?('gocd-agent-') && repo['name'] != 'gocd-agent-deprecated') || repo['name'].start_with?('gocd-server')
  end

  agents.compact.each do |repo|
    list_all_tags = RestClient.get("https://hub.docker.com/v2/repositories/#{org}/#{repo}/tags?page_size=50", {:accept => 'application/json', :Authorization => "JWT #{token}"})
    tags          = JSON.parse(list_all_tags)['results'].map() {|result| result['name']}
    puts tags

    puts "Deleting tags"

    tags.each do |tag|
      delete_tag = RestClient.delete("https://hub.docker.com/v2/repositories/#{org}/#{repo}/tags/#{tag}/", {:accept => 'application/json', :Authorization => "JWT #{token}"})
      puts delete_tag
    end
  end

  logout = RestClient.post('https://hub.docker.com/v2/logout/', {}, {:accept => 'application/json', :Authorization => "JWT #{token}"})
end

private

def env(key)
  value = ENV[key].to_s.strip
  raise "Please specify #{key}" unless value
  value
end