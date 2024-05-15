# make sure deps are installed using
# `choco install gnupg && choco install windows-sdk-11-version-22h2-all --install-arguments='/features OptionId.SigningTools /ceip off'`
namespace :win do
  signing_dir = "out/win"
  win_source_dir = 'src/win'
  meta_source_dir = 'src/meta'
  sign_tool = ENV['SIGNTOOL'] || 'C:\Program Files (x86)\Windows Kits\10\bin\10.0.22621.0\x64\signtool'

  # assumes the following:
  # - File `../signing-keys/windows-code-sign.p12.gpg` containing the encrypted p12/pfx codesigning key
  # - environment variable `GOCD_GPG_PASSPHRASE` containing the passphrase to decrypt the said key
  desc "setup code signing keys"
  task :setup do
    cd '../signing-keys' do
      open('gpg-passphrase', 'w') {|f| f.write(ENV['GOCD_GPG_PASSPHRASE'])}
      sh("gpg --quiet --batch --passphrase-file gpg-passphrase --output windows-code-sign.p12 windows-code-sign.p12.gpg")
    end
  end

  desc "sign win binaries"
  task :sign => ['setup'] do
    if Dir["#{win_source_dir}/*.exe"].empty?
      raise "Unable to find any binaries in #{win_source_dir}"
    end

    rm_rf signing_dir
    mkdir_p signing_dir
    Dir["#{win_source_dir}/*.{exe,json}"].each do |f|
      cp f, "#{signing_dir}"
    end

    # TODO re-enable signing when/if we have a valid code signing certificate. Existing cert expired 2024-05-15.
    # Dir["#{signing_dir}/*.exe"].each do |f|
    #   sh(%Q{"#{sign_tool}" sign /debug /f ../signing-keys/windows-code-sign.p12 /v /t http://timestamp.digicert.com /a /fd sha1 "#{f}"})
    #   sh(%Q{"#{sign_tool}" sign /debug /f ../signing-keys/windows-code-sign.p12 /v /tr http://timestamp.digicert.com /a /fd sha256 /td sha256 /as "#{f}"})
    #
    #   sh(%Q{"#{sign_tool}" verify /debug /v /a /pa /hash sha1 "#{f}"})
    #   sh(%Q{"#{sign_tool}" verify /debug /v /a /pa /hash sha256 "#{f}"})
    # end

    generate_metadata_for_single_dir signing_dir, '*.exe', :win, { architecture: 'x64', jre: { included: true } }
  end

  desc "sign a single windows binary"
  task :sign_single_binary, [:path, :dest_archive] => [:setup] do |task, args|
    path = args[:path]
    dest_archive = File.expand_path(args[:dest_archive] || "#{File.basename(path)}.zip")

    fail "You must specify a path to sign" if path.nil?
    fail "Path #{path} does not exists"  unless File.exist?(path)
    fail "Path must be a file, not a directory" if File.directory?(path)

    dest_dir = File.dirname(dest_archive)
    work_dir = ensure_clean_dir(File.join("tmp", SecureRandom.hex))
    signed_file = File.join(work_dir, File.basename(path))

    cp path, signed_file

    # TODO re-enable signing when/if we have a valid code signing certificate. Existing cert expired 2024-05-15.
    # sh(%Q{"#{sign_tool}" sign /debug /f ../signing-keys/windows-code-sign.p12 /v /t http://timestamp.digicert.com /a /fd sha1 "#{signed_file}"})
    # sh(%Q{"#{sign_tool}" sign /debug /f ../signing-keys/windows-code-sign.p12 /v /tr http://timestamp.digicert.com /a /fd sha256 /td sha256 /as "#{signed_file}"})
    #
    # sh(%Q{"#{sign_tool}" verify /debug /v /a /pa /hash sha1 "#{signed_file}"})
    # sh(%Q{"#{sign_tool}" verify /debug /v /a /pa /hash sha256 "#{signed_file}"})

    File.utime(0, 0, signed_file)

    cd work_dir do
      sh("jar -cMf #{dest_archive} .")
    end

    generate_metadata_for_single_dir dest_dir, '*.zip', :win
  end

  desc "upload the win binaries, after signing the binaries"
  task :upload, [:bucket_url] => :sign do |t, args|
    bucket_url = args[:bucket_url]

    raise "Please specify bucket url" unless bucket_url
    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    sh("aws s3 sync #{'--no-progress' unless $stdin.tty?} --acl public-read --cache-control 'max-age=31536000' #{signing_dir} s3://#{bucket_url}/binaries/#{go_full_version}/win")
  end
end
