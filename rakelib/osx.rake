require "securerandom"

# make sure deps are installed using
namespace :osx do
  signing_dir     = "out/osx"
  osx_source_dir  = 'src/osx'
  meta_source_dir = 'src/meta'

  desc 'sign osx zip instead of binaries'
  task :sign_as_zip => ['gpg:setup'] do
    if Dir["#{osx_source_dir}/*.zip"].empty?
      raise "Unable to find any binaries in #{osx_source_dir}"
    end

    rm_rf signing_dir
    mkdir_p signing_dir
    Dir["#{osx_source_dir}/*.{zip,json}"].each do |f|
      cp f, "#{signing_dir}"
    end

    cd signing_dir do
      Dir["*.zip"].each do |f|
        sh("gpg --default-key '#{GPG_SIGNING_ID}' --armor --detach-sign --sign --output '#{f}.asc' '#{f}'")
        sh("gpg --default-key '#{GPG_SIGNING_ID}' --verify '#{f}.asc'")
      end
    end

    rm_rf 'tmp'

    generate_metadata_for_single_dir signing_dir, '*.zip', :osx, { architecture: 'x64', jre: { included: true } }
  end

  desc "upload the osx binaries, after signing the binaries"
  task :upload, [:bucket_url] => :sign_as_zip do |t, args|
    bucket_url = args[:bucket_url]

    raise "Please specify bucket url" unless bucket_url

    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    sh("aws s3 sync #{'--no-progress' unless $stdin.tty?} --acl public-read --cache-control 'max-age=31536000' #{signing_dir} s3://#{bucket_url}/binaries/#{go_full_version}/osx")
  end

  def ensure_clean_dir(directory)
    directory.tap do |d|
      rm_rf d
      mkdir_p d
    end
  end

  def run(label, cmd, &block)
    puts label
    sh(cmd) do |ok, res|
      fail "Error when: #{label}: #{res}" unless ok
      block.call if block_given?
    end
  end
end
