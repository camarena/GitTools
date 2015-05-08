# GitSplitter
Is a CLI tool that extracts a subdirectory and its history from an existing git repo into a new git repository.  Git provides the *filter-branch* command.  The problem using that command is that if you have a huge amount of history and the subdirectory you want to split is relatively small in the project you will carry the history for the complete repository in the new repo.  GitSplitter works by creating a temporary copy of the existing repo, executing *filter-branch* on this temporary copy and then create a new repository replaying the set of commits needed to recreate the branches specified as arguments using only the files that are part of that subdirectory.  The original repo is left unchanged.

## Command Line Arguments ##

###Required###
* **--branches**.  Comma separated branches to include in new repo
* **--finalRepo**. Path to the final repo.  Must not exist
* **--mapFile**. Path to the file to store the mappings from original commit to new one
* **--source**. Url to the original git repository
* **--topFolder**. Subdirectory to extract.  It will be the top level directory of the new repo

### Optional ###
* **--rsync**. Path to the rsync command.  Default: `/usr/bin/rsync`
* **--git**. Path to the git command.  Default: `/usr/local/bin/git`
* **--tempRepo**. Path to the temporary repo.  If specified this directory must not exist
