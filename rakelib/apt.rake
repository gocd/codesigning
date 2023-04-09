namespace :apt do
  signing_dir = "out/apt"

  desc "generate apt repository"
  task :createrepo, [:bucket_url] => 'gpg:setup' do |t, args|
    bucket_url = args[:bucket_url]

    raise "Please specify bucket url" unless bucket_url

    rm_rf signing_dir
    mkdir_p signing_dir

    sh("aws s3 sync #{'--no-progress' unless $stdin.tty?} --delete --exclude='*' --include '*.deb' s3://#{bucket_url} #{signing_dir}")
    cd signing_dir do
      # create the package manifest
      sh("apt-ftparchive packages binaries > Packages")
      sh("gzip -9 --keep -- Packages")
      sh("bzip2 -9 --keep -- Packages")

      # Generate the `Release` and `InRelease` files and corresponding gpg keys
      sh("apt-ftparchive release . > Release")
      sh("gpg --default-key '#{GPG_SIGNING_ID}' --digest-algo sha512 --clearsign --output InRelease Release")
      sh("gpg --default-key '#{GPG_SIGNING_ID}' --digest-algo sha512 --armor --detach-sign --sign --output Release.gpg Release")
      sh("gpg --verify --default-key '#{GPG_SIGNING_ID}' Release.gpg Release")
      sh("gpg --armor --export '#{GPG_SIGNING_ID}' > GOCD-GPG-KEY.asc")
    end

    %w(GOCD-GPG-KEY.asc InRelease Packages Packages.bz2 Packages.gz Release Release.gpg).each do |f|
      # low cache ttl
      sh("aws s3 cp #{'--no-progress' unless $stdin.tty?} #{signing_dir}/#{f} s3://#{bucket_url}/#{f} --acl public-read --cache-control 'max-age=600'")
    end
  end
end
