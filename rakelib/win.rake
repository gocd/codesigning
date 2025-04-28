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

  desc "upload the win binaries, after generating metadata"
  task :upload, [:bucket_url] => :metadata do |t, args|
    bucket_url = args[:bucket_url]

    raise "Please specify bucket url" unless bucket_url
    go_full_version = JSON.parse(File.read("#{meta_source_dir}/version.json"))['go_full_version']

    sh("aws s3 sync #{'--no-progress' unless $stdin.tty?} --acl public-read --cache-control 'max-age=31536000' #{output_dir} s3://#{bucket_url}/binaries/#{go_full_version}/win")
  end
end
