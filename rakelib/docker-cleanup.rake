task :cleanup_docker do
  # dockerhub_token = env("DOCKERHUB_TOKEN")
  dockerhub_username = env("DOCKERHUB_USERNAME")
  dockerhub_password = env("DOCKERHUB_PASSWORD")
  org = env("DOCKERHUB_ORG")
  batch_size = 100
  keep_most_recent_tags = 1

  raise "ORG can't be `gocd`! We can't delete the official stable images." if org.eql?("gocd")

  require 'rest-client'
  require 'json'

  login = RestClient.post('https://hub.docker.com/v2/users/login/',{username: dockerhub_username, password: dockerhub_password}.to_json, {:accept => 'application/json', :content_type => 'application/json'})
  token = JSON.parse(login)['token']

  response = RestClient.get("https://hub.docker.com/v2/repositories/#{org}/?page_size=50", {:accept => 'application/json', :Authorization => "JWT #{token}"})
  all_repos = JSON.parse(response)

  repos = all_repos['results'].map do |repo|
    repo['name'] if (repo['name'].start_with?('gocd-agent-') && repo['name'] != 'gocd-agent-deprecated') || repo['name'].start_with?('gocd-server')
  end

  repos.compact.each do |repo|
    tags = nil

    # Keep going until we are not dealing with a full batch
    until tags != nil && tags.length + keep_most_recent_tags < batch_size do
      list_all_tags = RestClient.get("https://hub.docker.com/v2/repositories/#{org}/#{repo}/tags?page_size=#{batch_size}", {:accept => 'application/json', :Authorization => "JWT #{token}"})
      tags = JSON.parse(list_all_tags)['results'].map() { |result| result['name'] }

      # sort the tags in a reverse order of version and not deleting the most recent experimental tag
      tags = tags.sort.reverse.drop(keep_most_recent_tags)

      puts "Deleting #{tags.length} tags for #{org}/#{repo}: #{tags}"

      tags.each do |tag|
        delete_tag = RestClient.delete("https://hub.docker.com/v2/repositories/#{org}/#{repo}/tags/#{tag}/", {:accept => 'application/json', :Authorization => "JWT #{token}"})
        puts "Response for #{tag}: #{delete_tag}"
      end
    end
  end
end

private

def env(key)
  value = ENV[key].to_s.strip
  raise "Please specify #{key}" unless value
  value
end
