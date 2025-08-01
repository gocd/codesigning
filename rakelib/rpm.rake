namespace :rpm do
  signing_dir = "out/rpm"
  rpm_source_dir = 'src/rpm'
  meta_source_dir = 'src/meta'

  desc "sign rpm binaries"
  task :sign => ['gpg:setup'] do
    if Dir["#{rpm_source_dir}/*.rpm"].empty?
      raise "Unable to find any binaries in #{rpm_source_dir}"
    end

    rm_rf signing_dir
    mkdir_p signing_dir
    Dir["#{rpm_source_dir}/*.{rpm,json}"].each do |f|
      cp f, "#{signing_dir}"
    end

    cd signing_dir do
      Dir["*.rpm"].each do |f|
        # wrap with `setsid ... </dev/null` to avoid attaching to TTY. This otherwise causes a password prompt
        sh(%Q{setsid sh -c "rpm --addsign --define '_gpg_name #{GPG_SIGNING_ID}' '#{f}' < /dev/null"})
      end
    end

    sh("gpg --armor --output GPG-KEY-GOCD-#{Process.pid} --export #{GPG_SIGNING_ID}")

    # FIXME Temporarily allow SHA1 hashes within signatures on RHEL 9+. Needs to be moved to SHA256 when we can change our signing key
    # or may be an issue with signatures on older RPMs from before a certain point.
    # See https://github.com/gocd/gocd/issues/10722
    sh("sudo bash -c '(echo \"hash = +SHA1\" >/etc/crypto-policies/policies/modules/SHA1-HASH.pmod) && update-crypto-policies --set DEFAULT:SHA1-HASH'")

    sh("sudo rpm --import GPG-KEY-GOCD-#{Process.pid}")
    rm "GPG-KEY-GOCD-#{Process.pid}"

    Dir["#{signing_dir}/*.rpm"].each do |f|
      sh("rpm --checksig '#{f}'")
    end

    generate_metadata_for_single_dir signing_dir, '*.rpm', :rpm, { architecture: 'all', jre: { included: false } }
  end

  desc "upload the rpm binaries, after signing the binaries"
  task :upload, [:bucket_url] => :sign do |t, args|
    bucket_url = args[:bucket_url]

    raise "Please specify bucket url" unless bucket_url

    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    sh("aws s3 sync #{'--no-progress' unless $stdin.tty?} --acl public-read --cache-control 'max-age=31536000' #{signing_dir} s3://#{bucket_url}/binaries/#{go_full_version}/rpm")
  end
end
