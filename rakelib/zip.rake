require 'json'

namespace :zip do
  signing_dir = "out/zip"
  zip_source_dir = 'src/zip'
  meta_source_dir = 'src/meta'

  desc "sign zip binaries"
  task :sign => ['gpg:setup'] do
    if Dir["#{zip_source_dir}/*.zip"].empty?
      raise "Unable to find any binaries in #{zip_source_dir}"
    end

    rm_rf signing_dir
    mkdir_p signing_dir
    Dir["#{zip_source_dir}/*.{zip,json}"].each do |f|
      cp f, "#{signing_dir}"
    end

    cd signing_dir do
      Dir["*.zip"].each do |f|
        sh("gpg --default-key '#{GPG_SIGNING_ID}' --armor --detach-sign --sign --output '#{f}.asc' '#{f}'")
        sh("gpg --default-key '#{GPG_SIGNING_ID}' --verify '#{f}.asc'")
      end
    end

    generate_metadata_for_single_dir signing_dir, "*.zip", :generic, { architecture: 'all', jre: { included: false } }
  end

  desc "upload the zip binaries, after signing the binaries"
  task :upload, [:bucket_url] => :sign do |t, args|
    bucket_url = args[:bucket_url]

    raise "Please specify bucket url" unless bucket_url

    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    sh("aws s3 sync #{'--no-progress' unless $stdin.tty?} --acl public-read --cache-control 'max-age=31536000' #{signing_dir} s3://#{bucket_url}/binaries/#{go_full_version}/generic")
  end
end
