require 'json'
require 'time'
require 'base64'
require 'openssl'
require 'pathname'

namespace :metadata do
  meta_source_dir = 'src/meta'
  out_dir         = 'out/meta'

  desc "aggregate metadata for this release for all binaries"
  task :metadata_json, [:download_bucket_url] do |t, args|
    download_bucket_url = args[:download_bucket_url]

    raise "Please specify download bucket url" unless download_bucket_url

    release_time = Time.now.utc
    metadata     = JSON.parse(File.read("#{meta_source_dir}/version.json"))
    metadata.merge!(release_time_readable: release_time.xmlschema, release_time: release_time.to_i)
    go_full_version = metadata['go_full_version']

    rm_rf out_dir
    mkdir_p out_dir

    # fetch all metadata from s3
    sh("aws s3 sync #{'--no-progress' unless $stdin.tty?} --delete --exclude='*' --include '*.json' s3://#{download_bucket_url}/binaries/#{go_full_version} out")

    %w(deb rpm osx win generic).each do |dir|
      json_files = Dir["out/#{dir}/*.json"]
      raise "Did not find any JSON files under `out/#{dir}`" if json_files.empty?

      json_files.each do |json_file|
        metadata.merge!(JSON.parse(File.read(json_file)))
      end
    end
    open('out/metadata.json', 'w') {|f| f.write(JSON.generate(metadata))}
    sh("aws s3 cp #{'--no-progress' unless $stdin.tty?} out/metadata.json s3://#{download_bucket_url}/binaries/#{go_full_version}/ --acl public-read --cache-control 'max-age=31536000'")
  end

  desc "create releases.json"
  task :releases_json, [:download_bucket_url] do |t, args|
    bucket_url = args[:download_bucket_url]
    raise "Please specify bucket url" unless bucket_url

    target_dir = Pathname.new('target')

    sh("aws s3 sync --exclude='*' --include '*/metadata.json' s3://#{bucket_url} #{target_dir.join('repo')}")

    json = Dir["#{target_dir.join('repo', 'binaries', '*', 'metadata.json')}"].sort.collect {|file|
      JSON.parse(File.read(file))
    }

    open(target_dir.join('repo', 'releases.json'), 'w') do |file|
      file.write(JSON.generate(json))
    end

    sh("aws s3 cp #{target_dir.join('repo', 'releases.json')} s3://#{bucket_url}/releases.json --acl public-read --cache-control 'max-age=600'")
  end

  desc "aggregate metadata for this release for all binaries"
  task :update_check_json, [:download_bucket_url] => :unlock_update_check_credentials do |t, args|
    download_bucket_url = args[:download_bucket_url]

    raise "Please specify bucket url" unless download_bucket_url

    release_time = Time.now.utc
    metadata     = JSON.parse(File.read("#{meta_source_dir}/version.json"))
    metadata.merge!(release_time_readable: release_time.xmlschema, release_time: release_time.to_i)
    go_full_version = metadata['go_full_version']

    # generate the update check json
    message = JSON.generate({
                                'latest-version' => go_full_version,
                                'release-time'   => Time.now.utc.xmlschema
                            })

    digest            = OpenSSL::Digest::SHA512.new
    private_key       = OpenSSL::PKey::RSA.new(File.read('../signing-keys/update-check-signing-keys/subordinate-private-key.pem'), File.read('../signing-keys/update-check-signing-keys/subordinate-private-key-passphrase').strip)
    message_signature = Base64.encode64(private_key.sign(digest, message))

    open('out/latest.json', 'w') do |f|
      f.puts(JSON.generate({
                               message:                      message,
                               message_signature:            message_signature,
                               signing_public_key:           File.read('../signing-keys/update-check-signing-keys/subordinate-public-key.pem'),
                               signing_public_key_signature: File.read('../signing-keys/update-check-signing-keys/subordinate-public-key-digest')
                           }))
    end

    sh("aws s3 cp #{'--no-progress' unless $stdin.tty?} out/latest.json s3://#{download_bucket_url}/binaries/#{go_full_version}/ --acl public-read --cache-control 'max-age=31536000'")
  end

  desc 'create cloud json with experimental'
  task :cloud_json, [:download_bucket_url] do |t, args|
    require 'aws-sdk'
    require 'rest-client'

    metadata                 = JSON.parse(File.read("#{meta_source_dir}/version.json"))
    full_version             = metadata['go_full_version']
    release_time             = Time.now.utc
    download_bucket_url      = args[:download_bucket_url].split('/')[0]
    cloud_images_for_version = {
        go_version:            full_version,
        release_time_readable: release_time.xmlschema,
        release_time:          release_time.to_i,
        server_docker:         [{image_name: 'gocd-server'}, {image_name: 'gocd-server-centos-8'}],
        agents_docker:         docker_agents(full_version)
    }

    s3_client = Aws::S3::Client.new(region: 'us-east-1')
    File.open('cloud.json', 'w') {|f| f.write([cloud_images_for_version].to_json)}
    cloud_key = 'experimental/cloud.json'

    puts "Creating #{download_bucket_url}/cloud.json"
    s3_client.put_object({
                             acl:           "public-read",
                             body:          File.read('cloud.json'),
                             bucket:        download_bucket_url,
                             cache_control: "max-age=600",
                             content_type:  'application/json',
                             content_md5:   Digest::MD5.file('cloud.json').base64digest,
                             key:           cloud_key
                         })
  end

  desc 'aggregate all json for variation of installers created'
  task :aggregate_jsons, [:download_bucket_url] => [:metadata_json, :update_check_json, :releases_json, :cloud_json]

  desc "Generate all metadata for this release"
  task :generate, [:update_check_bucket_url] do |t, args|
    update_check_bucket_url = args[:update_check_bucket_url]

    raise "Please specify bucket url" unless update_check_bucket_url

    metadata        = JSON.parse(File.read("#{meta_source_dir}/version.json"))
    go_full_version = metadata['go_full_version']

    sh("ls")
    sh("aws s3 cp #{'--no-progress' unless $stdin.tty?} latest.json s3://#{update_check_bucket_url}/channels/experimental/latest-#{go_full_version}.json --acl public-read --cache-control 'max-age=600'")
    sh("aws s3 cp #{'--no-progress' unless $stdin.tty?} latest.json s3://#{update_check_bucket_url}/channels/experimental/latest.json --acl public-read --cache-control 'max-age=600'")
  end

  task :unlock_update_check_credentials do
    cd '../signing-keys/update-check-signing-keys' do
      open('gpg-passphrase', 'w') {|f| f.write(ENV['GOCD_GPG_PASSPHRASE'])}
      Dir['*.gpg'].each do |f|
        sh("gpg --yes --quiet --batch --passphrase-file gpg-passphrase --output '#{f.gsub(/.gpg$/, '')}' '#{f}'")
      end
    end
  end
end
