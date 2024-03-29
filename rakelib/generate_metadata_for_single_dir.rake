def load_package_metadata(metadata_file)
  if File.exist?(metadata_file)
    meta = JSON.parse(File.read(metadata_file)).transform_keys(&:to_sym)
    File.delete(metadata_file)
    meta
  end
end

def generate_metadata_for_single_dir(signing_dir, glob, metadata_key, default_package_meta = {})
  cd signing_dir do
    metadata = {}
    Dir[glob].each do |each_file|
      component_type = each_file =~ /go-server/ ? 'server' : 'agent'
      package_meta = load_package_metadata("#{each_file}.json") || default_package_meta

      component_key = package_meta[:architecture] == default_package_meta[:architecture] ? component_type : "#{component_type}-#{package_meta[:architecture]}"
      component_meta = {
        md5sum:       Digest::MD5.file(each_file).hexdigest,
        sha1sum:      Digest::SHA1.file(each_file).hexdigest,
        sha256sum:    Digest::SHA256.file(each_file).hexdigest,
        sha512sum:    Digest::SHA512.file(each_file).hexdigest,
        file:         "#{metadata_key}/#{each_file}",
      }

      metadata[component_key] = package_meta.merge(component_meta)

      component_meta.each do |k, v|
        next unless k =~ /sum$/
        open("#{each_file}.#{k}", 'w') { |f| f.puts([v, File.basename(each_file)].join('  ')) }
      end
    end

    json = { metadata_key => metadata }

    $stderr.puts "Generated metadata: #{JSON.pretty_generate(json)}"
    open('metadata.json', 'w') { |f| f.puts(JSON.generate(json)) }
  end
end