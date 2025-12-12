NUM_RETAINED_VERSIONS = 10

def prune_experimental_bucket_for(binary_type, all_types = false)
  raise "Please specify binary_type" unless all_types || binary_type
  experimental_bucket_url = ENV["EXPERIMENTAL_DOWNLOAD_BUCKET"]
  raise "Please specify experimental bucket url" unless experimental_bucket_url

  binary_type = "" if all_types

  #fetch the list of folders inside binaries which are not top 10 and pass it to rm command
  sh("aws s3 ls s3://#{experimental_bucket_url}/binaries/ | sort --version-sort -k2 | head -n-#{NUM_RETAINED_VERSIONS} | awk '{print $2}' | xargs -I DIR aws s3 rm s3://#{experimental_bucket_url}/binaries/DIR#{binary_type} --recursive")
end

desc "prune experimental bucket (all binary types) after release takes places"
task :prune_experimental_bucket do
  prune_experimental_bucket_for "", true
end

desc "prune experimental bucket for a single binary type"
task :prune_experimental_bucket_for_type, [:binary_type] do |t, args|
  prune_experimental_bucket_for args[:binary_type]
end
