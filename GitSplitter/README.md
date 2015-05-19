# GitSplitter
Is a CLI tool that operates in two modes depending on its arguments.  The purpose is to end up with a leaner git repository compared with what you get if you use only the facilities built into git.  The tool makes an effort to keep all the history relevant to the contents of the new repository.

* **Splitter Mode**.  In this mode the tool will extract a subdirectory of an existing repo and will create a new repo with this directory as the root.  It will replay the history for all commits that are relevant to the branches specified and that have changes in that subdirectory.  Git provides the *filter-branch* command.  The problem using that command is that in cases where you have a huge amount of history and the subdirectory you want to split is relatively small in the project you will carry the history for the complete repository in the new repo.  GitSplitter works by creating a temporary copy of the existing repo, executing *filter-branch* on this temporary copy and then create a new repository replaying the set of commits needed to recreate the branches specified as arguments using only the files that are part of that subdirectory.  The original repo is left unchanged.


*   **Trimming Mode**.   In this mode the tool will create a new repository removing a set of subdirectories specified as parameters from the original one.

for information on the options run the tool with "*-h*" or "*--help*"
