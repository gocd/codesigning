desc "deleting experimental bucket after release takes places"
task :empty_experimental_bucket do
  experimental_bucket_url = ENV["EXPERIMENTAL_DOWNLOAD_BUCKET"]
  raise "Please specify experimental bucket url" unless experimental_bucket_url

  #fetch the list of folders inside binaries which are not top 10 and pass it to rm command
  #todo remove dryrun after testing
  sh("aws s3 ls s3://#{experimental_bucket_url}/binaries/ | sort --version-sort -k2 | head -n-10 | awk '{print $2}' | xargs -I DIR echo aws s3 rm s3://#{experimental_bucket_url}/binaries/DIR --dryrun --recursive")
end