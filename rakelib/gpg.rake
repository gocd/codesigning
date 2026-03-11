namespace :gpg do
  desc "import gpg keys into a GPG database"
  task :setup do
    # assumes the following:
    # - File `../signing-keys/gpg-keys.pem.gpg` containing the encrypted gpg key
    # - environment variable `GOCD_GPG_PASSPHRASE` containing the passphrase to decrypt the said GPG key
    raise "missing GOCD_GPG_PASSPHRASE" if ENV['GOCD_GPG_PASSPHRASE'].strip.empty?
    ENV['GNUPGHOME'] = File.expand_path('~/.code-signing-keys')
    rm_rf ENV['GNUPGHOME']
    mkdir_p ENV['GNUPGHOME']
    chmod 0700, ENV['GNUPGHOME']
    cd '../signing-keys' do
      File.write("gpg-passphrase", ENV['GOCD_GPG_PASSPHRASE'].strip)
      sh("set -o pipefail; gpg --quiet --batch --pinentry-mode loopback --passphrase-file gpg-passphrase --decrypt gpg-keys.pem.gpg | gpg --import --batch")
      File.delete("gpg-passphrase")
    end
  end
end
