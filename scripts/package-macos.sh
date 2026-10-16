#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -Pdesktop -DskipTests package
mkdir -p target/desktop-input
cp target/atlas-desktop.jar target/desktop-input/
package_dir=$(mktemp -d /private/tmp/atlas-package.XXXXXX)
jpackage --type app-image --name Atlas --app-version 1.0.0 \
  --input target/desktop-input --main-jar atlas-desktop.jar \
  --dest "$package_dir" --java-options '-Djava.awt.headless=false' \
  --mac-package-identifier com.atlas.desktop --mac-package-name Atlas \
  --vendor Atlas --description 'Atlas personal expense tracker'
mkdir -p dist
ditto "$package_dir/Atlas.app" dist/Atlas.app
ditto -c -k --sequesterRsrc --keepParent "$package_dir/Atlas.app" dist/Atlas-macos.zip
