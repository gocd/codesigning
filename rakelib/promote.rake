require 'json'
require 'pathname'
require 'tempfile'
require_relative '../lib/addon_metadata'

namespace :promote do
  task :copy_binaries_from_experimental_to_stable, [:experimental_bucket_url, :stable_bucket_url] do |t, args|
    experimental_bucket_url = args[:experimental_bucket_url]
    raise "Please specify experimental bucket url" unless experimental_bucket_url

    stable_bucket_url = args[:stable_bucket_url]
    raise "Please specify stable bucket url" unless stable_bucket_url

    meta_source_dir = 'src/meta'
    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    mkdir_p "out"

    sh("aws s3 cp s3://#{experimental_bucket_url}/binaries/#{go_full_version}/latest.json out/latest.json")

    sh("aws s3 sync s3://#{experimental_bucket_url}/binaries/#{go_full_version} s3://#{stable_bucket_url}/binaries/#{go_full_version} --acl public-read --cache-control 'max-age=31536000'")
  end

  desc "copy addons from experimental to stable"
  task :copy_addon_from_experimental_to_stable, [:experimental_addon_bucket_url, :stable_addon_bucket_url] do |t, args|
    experimental_addon_bucket_url = args[:experimental_addon_bucket_url]
    raise "Please specify experimental addon bucket url" unless experimental_addon_bucket_url

    stable_addon_bucket_url = args[:stable_addon_bucket_url]
    raise "Please specify stable addon bucket url" unless stable_addon_bucket_url

    meta_source_dir = 'src/meta'
    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    sh("aws s3 sync s3://#{experimental_addon_bucket_url}/#{go_full_version} s3://#{stable_addon_bucket_url}/#{go_full_version} --acl private --cache-control 'max-age=31536000'")
  end

  desc "task to promote artifacts to update bucket"
  task :update_check_json, [:update_bucket_url] do |t, args|
    update_bucket_url = args[:update_bucket_url]
    raise "Please specify update bucket url" unless update_bucket_url

    meta_source_dir = 'src/meta'
    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    sh("aws s3 cp s3://#{update_bucket_url}/channels/supported/latest.json s3://#{update_bucket_url}/channels/supported/latest.previous.json --cache-control 'max-age=600' --acl public-read")

    sh("aws s3 cp out/latest.json s3://#{update_bucket_url}/channels/supported/latest.json --cache-control 'max-age=600' --acl public-read")
    sh("aws s3 cp out/latest.json s3://#{update_bucket_url}/channels/supported/latest-#{go_full_version}.json --cache-control 'max-age=600' --acl public-read")
    sh("rm out/latest.json")
  end

  desc "task to promote addons and their metadata"
  task :promote_addons_metadata, [:experimental_addon_bucket_url, :stable_addon_bucket_url] do |t, args|
    experimental_addon_bucket_url = args[:experimental_addon_bucket_url]
    raise "Please specify experimental addon bucket url" unless experimental_addon_bucket_url

    stable_addon_bucket_url = args[:stable_addon_bucket_url]
    raise "Please specify stable addon bucket url" unless stable_addon_bucket_url

    meta_source_dir = 'src/meta'
    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    path_to_s3_parent_of_addon = "#{get_path_inside_addon_bucket(stable_addon_bucket_url)}/#{go_full_version}"

    pg_addon_metadata = AddonMetadata.new(
        addon_file_s3_parent:  path_to_s3_parent_of_addon,
        metadata_full_s3_path: "s3://#{stable_addon_bucket_url}/go-postgresql.json",
        version:               go_full_version,
        prefix:                'pg'
    )

    bc_addon_metadata = AddonMetadata.new(
        addon_file_s3_parent:  path_to_s3_parent_of_addon,
        metadata_full_s3_path: "s3://#{stable_addon_bucket_url}/go-business-continuity.json",
        version:               go_full_version,
        prefix:                'bc'
    )

    pg_data_to_append = metadata_for_stable_addon("s3://#{experimental_addon_bucket_url}/#{go_full_version}/pg_metadata.json", experimental_addon_bucket_url, stable_addon_bucket_url)
    bc_data_to_append = metadata_for_stable_addon("s3://#{experimental_addon_bucket_url}/#{go_full_version}/bc_metadata.json", experimental_addon_bucket_url, stable_addon_bucket_url)

    pg_addon_metadata.append_to_existing(pg_data_to_append)
    bc_addon_metadata.append_to_existing(bc_data_to_append)

    pg_addon_metadata.upload_combined_metadata_file
    bc_addon_metadata.upload_combined_metadata_file
  end

  def get_path_inside_addon_bucket(addon_bucket_url)
    raise "Please specify addon bucket url" unless addon_bucket_url
    addon_bucket_url.sub(%r{^[^/]+/}, '')
  end

  def metadata_for_stable_addon(addon_metadata_path, experimental_addon_bucket_url, stable_addon_bucket_url)
    metadata_file = Tempfile.new(['pg_metadata', '.json'], Dir.tmpdir)
    sh("aws s3 cp #{addon_metadata_path} #{metadata_file.path}")

    JSON.parse(File.read(metadata_file.path)).tap do |metadata|
      metadata['location'].sub!(/^#{get_path_inside_addon_bucket(experimental_addon_bucket_url)}/, get_path_inside_addon_bucket(stable_addon_bucket_url))
    end
  end
end