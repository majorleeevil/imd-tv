#!/bin/sh
# Renders the app icons, banner, in-app logo and site images from the SVGs in art/ (needs rsvg-convert).
# Uses the app's copy of Inter (app/src/main/res/font/inter.ttf) through a private fontconfig file.
set -e
cd "$(dirname "$0")/.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
printf '<?xml version="1.0"?><fontconfig><dir>%s</dir><cachedir>%s</cachedir></fontconfig>\n' "$PWD/app/src/main/res/font" "$T" > "$T/fonts.conf"
r() { FONTCONFIG_FILE="$T/fonts.conf" rsvg-convert -w "$2" -h "$3" "art/$1.svg" -o "$4"; }
R=app/src/main/res
for d in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do r launcher ${d#*:} ${d#*:} $R/mipmap-${d%%:*}/ic_launcher.png; done
r banner 640 360 $R/drawable-xhdpi/banner.png
r mark 128 109 $R/drawable-xhdpi/logo.png  # 64dp wide at 2x: shown at 44–72dp, so it is never shrunk much (sharp small text)
mkdir -p site/img
r mark 320 272 site/img/logo.png
r launcher 64 64 site/favicon.png
r launcher 512 512 site/img/icon.png
