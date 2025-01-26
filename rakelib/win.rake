namespace :win do
  output_dir = "out/win"
  win_source_dir = 'src/win'
  meta_source_dir = 'src/meta'

  desc "generate metadata for win binaries"
  task :metadata do
    if Dir["#{win_source_dir}/*.exe"].empty?
      raise "Unable to find any binaries in #{win_source_dir}"
    end

    rm_rf output_dir
    mkdir_p output_dir
    Dir["#{win_source_dir}/*.{exe,json}"].each do |f|
      cp f, "#{output_dir}"
    end

    generate_metadata_for_single_dir output_dir, '*.exe', :win, { architecture: 'x64', jre: { included: true } }
  end

  desc "generate metadata for a single windows binary"
  task :metadata_single_binary, [:path, :dest_archive] do |task, args|
    path = args[:path]
    dest_archive = File.expand_path(args[:dest_archive] || "#{File.basename(path)}.zip")

    fail "You must specify a path to sign" if path.nil?
    fail "Path #{path} does not exists"  unless File.exist?(path)
    fail "Path must be a file, not a directory" if File.directory?(path)

    dest_dir = File.dirname(dest_archive)
    work_dir = ensure_clean_dir(File.join("tmp", SecureRandom.hex))
    output_file = File.join(work_dir, File.basename(path))

    cp path, output_file
    File.utime(0, 0, output_file)

    cd work_dir do
      sh("jar -cMf #{dest_archive} .")
    end

    generate_metadata_for_single_dir dest_dir, '*.zip', :win
  end

  desc "upload the win binaries, after generating metadata"
  task :upload, [:bucket_url] => :metadata do |t, args|
    bucket_url = args[:bucket_url]

    raise "Please specify bucket url" unless bucket_url
    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    sh("aws s3 sync #{'--no-progress' unless $stdin.tty?} --acl public-read --cache-control 'max-age=31536000' #{output_dir} s3://#{bucket_url}/binaries/#{go_full_version}/win")
  end
end
