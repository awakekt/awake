#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Packages the prebuilt Awake Player for macOS (Apple silicon) as AwakePlayer.app inside
# macos-arm64-player.zip directly from Awake Core's `:awake:player:player-shell`.
#
# Needs JDK 17+ (jpackage) and `brew install vulkan-loader molten-vk glfw`.
set -euo pipefail

out_zip="${1:?usage: $0 <output-zip-path>}"
name="AwakePlayer"
main_class="com.awakekt.awake.player.shell.MainKt"
root="$(git rev-parse --show-toplevel)"
out="$root/awake/player/player-shell/build/desktop-package"
input="$out/lib"
natives="$input/natives"
dist="$out/dist"

[[ "$(uname -s)-$(uname -m)" == "Darwin-arm64" ]] || { echo "Run this on an Apple silicon Mac." >&2; exit 1; }

cd "$root"
./gradlew --console=plain --no-configuration-cache \
  :awake:engine:window:buildDesktopNative \
  :awake:backend:vulkan:bindings:buildDesktopNative \
  :awake:player:player-shell:desktopPackageInput

rm -rf "$natives" "$dist"
mkdir -p "$natives" "$dist"

copy_native() {
  local src="$1"
  local lib="$2"
  if [[ ! -f "$src" ]]; then
    echo "Missing built native library: $src" >&2
    exit 1
  fi
  cp -L "$src" "$natives/$lib"
}

copy_native "$root/awake/backend/vulkan/bindings/build/desktop-native-libs/libawake-vulkan.dylib" "libawake-vulkan.dylib"
copy_native "$root/awake/engine/window/build/desktop-native-libs/libawake-window.dylib" "libawake-window.dylib"

# Vulkan loader, GLFW and MoltenVK from Homebrew, relinked to sit beside each other.
cp -L "$(brew --prefix vulkan-loader)/lib/libvulkan.1.dylib" "$natives/"
cp -L "$(brew --prefix glfw)/lib/libglfw.3.dylib" "$natives/"
cp -L "$(brew --prefix molten-vk)/lib/libMoltenVK.dylib" "$natives/"
for lib in "$natives"/*.dylib; do
  chmod u+w "$lib"
  strip -S "$lib"
  install_name_tool -id "@loader_path/$(basename "$lib")" "$lib" 2>/dev/null
  otool -L "$lib" | awk 'NR > 1 { print $1 }' | { grep -E '^/(opt/homebrew|usr/local)/' || true; } | while read -r dep; do
    target="@loader_path/$(basename "$dep")"
    [[ "$(basename "$dep")" == libvulkan.1.dylib ]] && target="@loader_path/../../Frameworks/libvulkan.1.dylib"
    install_name_tool -change "$dep" "$target" "$lib" 2>/dev/null
  done
done
if otool -L "$natives"/*.dylib | grep -E '/(opt/homebrew|usr/local)/'; then
  echo "A bundled library still points at Homebrew." >&2
  exit 1
fi

iconset="$out/AwakePlayer.iconset"
rm -rf "$iconset" && mkdir -p "$iconset"
mark="$root/templates/project/app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"
for size in 16 32 128 256 512; do
  sips -z "$size" "$size" "$mark" --out "$iconset/icon_${size}x${size}.png" >/dev/null
  sips -z $((size * 2)) $((size * 2)) "$mark" --out "$iconset/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$iconset" -o "$out/AwakePlayer.icns"

main_jar="$(basename "$(ls "$input"/awake-player-player-shell-*.jar | head -1)")"
jpackage \
  --type app-image \
  --dest "$dist" \
  --name "$name" \
  --app-version 1.0.0 \
  --vendor "AwakeKt" \
  --icon "$out/AwakePlayer.icns" \
  --input "$input" \
  --main-jar "$main_jar" \
  --main-class "$main_class" \
  --mac-package-identifier com.awakekt.awake.player \
  --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.zipfs,jdk.httpserver \
  --java-options "-XstartOnFirstThread" \
  --java-options "-Djava.library.path=\$APPDIR/natives" \
  --java-options "-Dawake.project.dir=\$APPDIR/../Resources/project"

app="$dist/$name.app"
plist="$app/Contents/Info.plist"
plutil -replace LSMinimumSystemVersion -string "15.0" "$plist"

mkdir -p "$app/Contents/Frameworks"
mv "$app/Contents/app/natives/libvulkan.1.dylib" "$app/Contents/Frameworks/"

icd_dir="$app/Contents/Resources/vulkan/icd.d"
mkdir -p "$icd_dir"
cat > "$icd_dir/MoltenVK_icd.json" <<'EOF'
{
  "file_format_version": "1.0.0",
  "ICD": {
    "library_path": "../../../app/natives/libMoltenVK.dylib",
    "api_version": "1.4.0",
    "is_portability_driver": true
  }
}
EOF

mkdir -p "$app/Contents/Resources/project"

codesign --force --deep --sign - "$app"
codesign --verify --deep --strict "$app"

mkdir -p "$(dirname "$out_zip")"
rm -f "$out_zip"
(cd "$dist" && zip -q -y -r "$out_zip" "$name.app")
ls -lh "$out_zip"
