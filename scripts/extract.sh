#!/usr/bin/env bash

# $1 = get_directory
# $2 = d.get_name

downloads_dir="$1"
target="$downloads_dir/$2"

# ensure downloads_dir is under home and target under downloads_dir

if [[ $downloads_dir != "$HOME/"[!/]* ]]; then
  exit
fi

if [[ $target != "$downloads_dir/"[!/]* ]]; then
  exit
fi

# don't extract single files

if [[ -f $target ]]; then
  exit
fi

cd "$target"

touch .extracting

for archive in $(find . -name '*.rar' -o -name '*.7z' -o -name '*.zip' -o -name '*.001'); do
  dir_name=$(basename ${archive%.*})
  7z x $archive "-oextract/$dir_name" -y
done

rm .extracting

