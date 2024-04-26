require 'erb'

# make sure deps are installed using
# `apt-get install -y debsigs debsig-verify gnupg gnupg-agent apt-utils bzip2 gzip unzip zip rake sudo`
# `echo "go ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/go`
namespace :deb do
  signing_dir     = "out/deb"
  deb_source_dir  = 'src/deb'
  meta_source_dir = 'src/meta'

  desc "sign deb binaries"
  task :sign, [:bucket_url] => 'gpg:setup' do |t, args|
    bucket_url = args[:bucket_url]

    raise "Please specify bucket url" unless bucket_url

    if Dir["#{deb_source_dir}/*.deb"].empty?
      raise "Unable to find any binaries in #{deb_source_dir}"
    end

    rm_rf signing_dir
    mkdir_p signing_dir
    Dir["#{deb_source_dir}/*.{deb,json}"].each do |f|
      cp f, "#{signing_dir}"
    end

    cd signing_dir do
      Dir["*.deb"].each do |f|
        sh("debsigs --verbose --sign=origin --default-key='#{GPG_SIGNING_ID}' '#{f}'")
      end
    end

    # Verify the signature with (the very painful) debsig-verify
    debsig_policies_folder = "debsig/policies/#{GPG_SIGNING_ID}"
    debsig_keyring_folder = "debsig/keyrings/#{GPG_SIGNING_ID}"
    sh("mkdir -p #{debsig_policies_folder} #{debsig_keyring_folder}")
    sh("gpg --export #{GPG_SIGNING_ID} --output #{debsig_keyring_folder}/debsig.gpg")

    File.write(
      "#{debsig_policies_folder}/debsig-verify-policy.pol",
      ERB.new(File.read('rakelib/debsig-verify-policy.xml.erb')).result(binding)
    )

    Dir["#{signing_dir}/*.deb"].each do |f|
      sh("debsig-verify --verbose --debug --policies-dir '#{Dir.pwd}/debsig/policies' --keyrings-dir '#{Dir.pwd}/debsig/keyrings' '#{f}'")
    end

    generate_metadata_for_single_dir signing_dir, '*.deb', :deb, { architecture: 'all', jre: { included: false } }
  end

  desc "upload the deb binaries, after signing the binaries"
  task :upload, [:bucket_url] => :sign do |t, args|
    bucket_url = args[:bucket_url]

    raise "Please specify bucket url" unless bucket_url
    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    sh("aws s3 sync #{'--no-progress' unless $stdin.tty?} --acl public-read --cache-control 'max-age=31536000' #{signing_dir} s3://#{bucket_url}/binaries/#{go_full_version}/deb")
  end
end
