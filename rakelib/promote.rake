require 'json'
require 'pathname'
require 'tempfile'

namespace :promote do
  task :copy_binaries_from_experimental_to_stable, [:experimental_bucket_url, :stable_bucket_url] do |t, args|
    experimental_bucket_url = args[:experimental_bucket_url]
    raise "Please specify experimental bucket url" unless experimental_bucket_url

    stable_bucket_url = args[:stable_bucket_url]
    raise "Please specify stable bucket url" unless stable_bucket_url

    meta_source_dir = 'src/meta'
    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    mkdir_p "out"

    sh("aws s3 cp #{'--no-progress' unless $stdin.tty?} s3://#{experimental_bucket_url}/binaries/#{go_full_version}/latest.json out/latest.json")

    sh("aws s3 sync #{'--no-progress' unless $stdin.tty?} s3://#{experimental_bucket_url}/binaries/#{go_full_version} s3://#{stable_bucket_url}/binaries/#{go_full_version} --copy-props none --acl public-read --cache-control 'max-age=31536000'")
  end

  desc "task to promote artifacts to update bucket"
  task :update_check_json, [:update_bucket_url] do |t, args|
    update_bucket_url = args[:update_bucket_url]
    raise "Please specify update bucket url" unless update_bucket_url

    meta_source_dir = 'src/meta'
    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    sh("aws s3 cp #{'--no-progress' unless $stdin.tty?} s3://#{update_bucket_url}/channels/supported/latest.json s3://#{update_bucket_url}/channels/supported/latest.previous.json --copy-props none --acl public-read --cache-control 'max-age=600' --content-type 'application/json'")

    sh("aws s3 cp #{'--no-progress' unless $stdin.tty?} s3://#{update_bucket_url}/channels/experimental/latest-#{go_full_version}.json s3://#{update_bucket_url}/channels/supported/latest.json --copy-props none --acl public-read --cache-control 'max-age=600' --content-type 'application/json'")
    sh("aws s3 cp #{'--no-progress' unless $stdin.tty?} s3://#{update_bucket_url}/channels/experimental/latest-#{go_full_version}.json s3://#{update_bucket_url}/channels/supported/latest-#{go_full_version}.json --copy-props none --acl public-read --cache-control 'max-age=600' --content-type 'application/json'")
  end
end
