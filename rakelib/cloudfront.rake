namespace :cloudfront do

  require "shellwords"
  require "open3"

  def shiv(*cmds, &block)
    full_cmd = Shellwords.join(cmds)
    stdout, stderr, status = Open3.capture3(full_cmd)

    stdout.tap do |output|
      raise "Failed to run: #{full_cmd}; STDERR:\n#{stderr}" unless status.success?

      block.call(output) if block_given?
    end
  end

  def find_by_alias(data, names=[])
    data.reduce([]) do |memo, distro|
      aliases = distro["Names"]
      memo << distro["Id"] if !aliases.nil? && names.any? { |name| aliases.include?(name) }
      memo
    end
  end

  task :invalidate do
    require "json"

    shiv *%w/aws cloudfront list-distributions --query DistributionList.Items[].{Id:Id,Names:Aliases.Items}/ do |json|
      puts "Finding GoCD CloudFront distributions for downloads\n"

      distros = find_by_alias(JSON.parse(json), %w(download.gocd.org download.gocd.io)).tap { |results|
        if results.size > 0
          puts "  => Located distributions: [ #{results.join(", ")} ]\n"
        else
          raise "Failed to locate CloudFront distributions!"
        end
      }

      distros.each do |distro|
        path = "/*"
        puts "Creating CloudFront invalidations for distro #{distro} with path #{path}"
        shiv *%W[aws cloudfront create-invalidation --distribution-id #{distro} --paths #{path}]
      end

      distros.each do |distro|
        puts "Waiting for invalidations to finish for distro #{distro}"

        until JSON.parse(shiv *%W/aws cloudfront list-invalidations --distribution-id #{distro} --query InvalidationList.Items[].{Id:Id,Status:Status}/).all? { |iv| iv["Status"] == "Completed" } do
          print "."
          sleep 5
        end

        puts "\nDone"
      end

    end
  end
end
